package com.sanggwonai.api.report.service

import com.sanggwonai.api.auth.service.AuthContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

data class ReportGenerationResult(
    val source: String,                 // "openai" | "failed"
    val report: Map<String, Any?>?,     // 검증 통과한 보고서 JSON (output_schema 형태)
    val rawText: String?,
    val input: Map<String, Any?>        // 조립된 input_data (감사/디버그용)
)

/**
 * 보고서 생성 오케스트레이션 (Step 4).
 *  1) ReportContextAssembler 로 input_data 조립
 *  2) reports/prompt_template.json·output_schema.json(클래스패스) 로드 + 플레이스홀더 치환
 *  3) ReportLlmClient(OpenAI) 호출
 *  4) 출력 JSON 파싱 + 필수 챕터 검증 (실패 시 1회 재시도 → 그래도 실패면 source=failed)
 *
 * source=failed 면 호출측(엔드포인트)이 사전 생성 샘플 PDF로 폴백한다.
 */
@Service
class ReportPromptService(
    private val assembler: ReportContextAssembler,
    private val llmClient: ReportLlmClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ReportPromptService::class.java)
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    // 클래스패스(reports/*.json — build.gradle 의 srcDir("reports"))에서 1회 로드 후 캐시
    private val template: Map<String, Any?> by lazy { loadJson("/prompt_template.json") }
    private val outputSchemaJson: String by lazy { loadText("/output_schema.json") }

    private val systemPrompt: String get() = template["system_prompt"] as? String ?: ""
    private val userTemplate: String get() = template["user_prompt_template"] as? String ?: ""
    private val temperature: Double
        get() = ((template["request_config"] as? Map<*, *>)?.get("temperature") as? Number)?.toDouble() ?: 0.4

    fun llmAvailable(): Boolean = llmClient.isAvailable()

    fun generate(authContext: AuthContext, analysisId: String): ReportGenerationResult {
        val input = assembler.assemble(authContext, analysisId)
        val saved = input["saved_analysis"] as? Map<*, *>
        val region = saved?.get("region") as? String ?: ""
        val category = saved?.get("category") as? String ?: ""
        val userPrompt = userTemplate
            .replace("{input_data_json}", objectMapper.writeValueAsString(input))
            .replace("{output_schema_json}", outputSchemaJson)
            .replace("{region}", region)
            .replace("{category_name}", category)
            .replace("{category}", category)

        repeat(2) { attempt ->
            val text = llmClient.generate(systemPrompt, userPrompt, temperature)
            if (text != null) {
                val report = parseAndValidate(text, input)
                if (report != null) return ReportGenerationResult("openai", report, text, input)
                logger.warn("Report output validation failed (attempt {})", attempt + 1)
            }
        }
        return ReportGenerationResult("failed", null, null, input)
    }

    private fun parseAndValidate(text: String, input: Map<String, Any?>): Map<String, Any?>? {
        val json = text.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val report = runCatching { objectMapper.readValue(json, mapType) }.getOrNull() ?: return null

        val required = REQUIRED_CHAPTERS.toMutableList()
        if (input.containsKey("section_05_review_insight")) required.add("chapter_8_review_insight")
        if (input.containsKey("section_06_investment_payback")) required.add("chapter_9_investment_payback")
        val missing = required.filterNot { report.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn("Report missing required chapters: {}", missing)
            return null
        }
        return report
    }

    private fun loadJson(path: String): Map<String, Any?> = objectMapper.readValue(loadText(path), mapType)

    private fun loadText(path: String): String =
        javaClass.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("리소스 없음: $path  (build.gradle.kts 의 sourceSets resources srcDir(\"reports\") 확인)")

    companion object {
        private val REQUIRED_CHAPTERS = listOf(
            "report_metadata",
            "chapter_1_executive_summary",
            "chapter_2_analysis_overview",
            "chapter_3_top3_property_analysis",
            "chapter_4_location_characteristics",
            "chapter_5_business_fit_analysis",
            "chapter_6_diagnosis_and_action",
            "chapter_7_appendix"
        )
    }
}
