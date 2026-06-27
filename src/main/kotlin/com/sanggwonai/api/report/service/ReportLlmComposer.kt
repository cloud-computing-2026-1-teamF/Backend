package com.sanggwonai.api.report.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * 보고서 LLM 조립 — v6.5 병렬.
 *
 * 단일 거대 콜(매물 3벌, ~60~75초)은 AWS 60초 인바운드 한도에 걸린다. 매물별 구조를 이용해
 * "매물별 콜 N개 + 공통 콜 1개"를 **병렬**로 던지고 합친다. 각 콜은 단일 매물(~v5.0 1매물) 분량이라
 * 벽시계 ~25~30초로 v5.0과 같고, 콜당 JSON이 작아 잘림·검증실패도 준다.
 *
 * input_data 조립(DB/보안 컨텍스트)은 ReportPromptService 가 메인 스레드에서 하고, 여기는
 * 조립된 input 만 받아 LLM 호출·병합·검증만 한다(→ DB 없이 단위 테스트 가능).
 */
@Component
class ReportLlmComposer(
    private val llmClient: LlmTextClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ReportLlmComposer::class.java)
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    private val template: Map<String, Any?> by lazy { loadJson("/prompt_template.json") }
    private val propertySchemaJson: String by lazy { loadText("/output_schema_property.json") }
    private val overviewSchemaJson: String by lazy { loadText("/output_schema_overview.json") }

    private val systemPrompt: String get() = template["system_prompt"] as? String ?: ""
    private val propertyTemplate: String get() = template["user_prompt_property_template"] as? String ?: ""
    private val overviewTemplate: String get() = template["user_prompt_overview_template"] as? String ?: ""
    private val temperature: Double
        get() = ((template["request_config"] as? Map<*, *>)?.get("temperature") as? Number)?.toDouble() ?: 0.4

    fun llmAvailable(): Boolean = llmClient.isAvailable()

    /** 조립된 input_data → overview 콜 1개 + property 콜 N개 병렬 호출 → 병합 → 검증. */
    fun compose(input: Map<String, Any?>): ReportGenerationResult {
        val saved = input["saved_analysis"] as? Map<*, *>
        val region = saved?.get("region") as? String ?: ""
        val category = saved?.get("category") as? String ?: ""
        val top3 = (saved?.get("top3") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        if (top3.isEmpty()) {
            logger.warn("Report compose aborted: empty top3")
            return ReportGenerationResult("failed", null, null, input)
        }
        val ranks = top3.mapIndexed { i, t -> (t["rank"] as? Number)?.toInt() ?: (i + 1) }
        val inputJson = objectMapper.writeValueAsString(input)

        val pool = Executors.newFixedThreadPool(ranks.size + 1)
        try {
            val overviewFuture = CompletableFuture.supplyAsync({
                callJson(overviewUserPrompt(inputJson, region, category))
            }, pool)
            val propertyFutures = ranks.map { rank ->
                rank to CompletableFuture.supplyAsync({
                    callJson(propertyUserPrompt(inputJson, region, category, rank))
                }, pool)
            }

            val overview = overviewFuture.get()
            val properties = propertyFutures.map { (rank, f) -> rank to f.get() }
            if (overview == null || properties.any { it.second == null }) {
                logger.warn(
                    "Report compose failed: overview={} property_ok={}",
                    overview != null, properties.map { "${it.first}=${it.second != null}" }
                )
                return ReportGenerationResult("failed", null, null, input)
            }

            val report = LinkedHashMap<String, Any?>(overview)
            report["property_reports"] = properties.sortedBy { it.first }.map { (rank, obj) ->
                LinkedHashMap(obj!!).apply { put("rank", rank) }
            }

            val validated = validate(report, top3.size) ?: return ReportGenerationResult("failed", null, null, input)
            return ReportGenerationResult("openai", validated, null, input)
        } finally {
            pool.shutdown()
        }
    }

    private fun overviewUserPrompt(inputJson: String, region: String, category: String): String =
        overviewTemplate
            .replace("{input_data_json}", inputJson)
            .replace("{output_schema_json}", overviewSchemaJson)
            .replace("{region}", region)
            .replace("{category_name}", category)
            .replace("{category}", category)

    private fun propertyUserPrompt(inputJson: String, region: String, category: String, rank: Int): String =
        propertyTemplate
            .replace("{input_data_json}", inputJson)
            .replace("{output_schema_json}", propertySchemaJson)
            .replace("{rank_index}", (rank - 1).toString())
            .replace("{rank}", rank.toString())
            .replace("{region}", region)
            .replace("{category_name}", category)
            .replace("{category}", category)

    /** LLM 콜 1건 → JSON 파싱. 형식 실패 시 1회 재시도. */
    private fun callJson(userPrompt: String): Map<String, Any?>? {
        repeat(2) { attempt ->
            val text = llmClient.generate(systemPrompt, userPrompt, temperature)
            if (text != null) {
                parseJson(text)?.let { return it }
                logger.warn("Report sub-call JSON parse failed (attempt {})", attempt + 1)
            }
        }
        return null
    }

    private fun parseJson(text: String): Map<String, Any?>? {
        val json = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { objectMapper.readValue(json, mapType) }.getOrNull()
    }

    private fun validate(report: Map<String, Any?>, expectedProps: Int): Map<String, Any?>? {
        val missing = REQUIRED_KEYS.filterNot { report.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn("Report missing required keys: {}", missing)
            return null
        }
        val reports = report["property_reports"] as? List<*>
        if (reports.isNullOrEmpty() || reports.size < expectedProps) {
            logger.warn("property_reports count {} < expected {}", reports?.size ?: 0, expectedProps)
            return null
        }
        return report
    }

    private fun loadJson(path: String): Map<String, Any?> = objectMapper.readValue(loadText(path), mapType)

    private fun loadText(path: String): String =
        javaClass.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?.removePrefix("﻿")
            ?: error("리소스 없음: $path  (build.gradle.kts 의 sourceSets resources srcDir(\"reports\") 확인)")

    companion object {
        // v6.5 매물별 구조: 공통 콜(overview) 키 + 병합된 property_reports 존재만 검증.
        private val REQUIRED_KEYS = listOf(
            "report_metadata",
            "comparison_overview",
            "property_reports",
            "your_choice",
            "chapter_7_appendix"
        )
    }
}
