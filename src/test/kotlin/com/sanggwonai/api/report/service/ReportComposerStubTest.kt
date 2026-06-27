package com.sanggwonai.api.report.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 가짜 LLM 으로 v6.5 병렬 조립 배관을 검증 — OpenAI 키·비용 0.
 *
 * 가짜 LlmTextClient 가 user 프롬프트를 보고 "공통 콜"이면 overview 조각을, "매물 콜"이면
 * 해당 rank 의 property_report 조각을 돌려준다(기대 출력 fixture 를 쪼개서). 이로써
 * 병렬 4콜 → rank 별 머지 → 검증 → 렌더가 깨지지 않는지 결정론적으로 확인한다.
 * (실제 모델 품질·소요시간은 ReportPipelineLiveTest 로 별도 확인 — 그건 키 필요)
 *
 * 실행: ./gradlew test --tests "*ReportComposerStubTest*"
 */
class ReportComposerStubTest {

    private val mapper = JsonMapper.builder().build()
    private val tr = object : TypeReference<Map<String, Any?>>() {}

    @Test
    fun `가짜 LLM으로 병렬 조립·머지·렌더 검증`() {
        val full = mapper.readValue(resource("/report/sample_report_v65.json"), tr)
        val properties = (full["property_reports"] as List<*>).map { it as Map<*, *> }
        val overviewPiece = full.filterKeys { it != "property_reports" }
        val propByRank = properties.associateBy { (it["rank"] as Number).toInt() }

        // user 프롬프트 내용으로 어떤 콜인지 판별해 해당 조각을 돌려주는 가짜 LLM.
        val fake = object : LlmTextClient {
            override fun isAvailable() = true
            override fun generate(systemPrompt: String, userPrompt: String, temperature: Double): String? {
                if (userPrompt.contains("공통 영역만")) return mapper.writeValueAsString(overviewPiece)
                val rank = Regex("rank (\\d+) 매물").find(userPrompt)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return null
                return mapper.writeValueAsString(propByRank[rank])
            }
        }

        val composer = ReportLlmComposer(fake, mapper)
        val input = mapper.readValue(resource("/report/sample_input_v65.json"), tr)

        val result = composer.compose(input)
        assertEquals("openai", result.source, "조립/검증 실패")
        val report = result.report!!
        assertEquals(3, (report["property_reports"] as List<*>).size, "property_reports 3개 아님")

        val bytes = ReportHtmlRenderer().render(report, input)
        File("build/report_fake.html").apply { parentFile.mkdirs() }.writeBytes(bytes)
        println("[STUB] wrote build/report_fake.html (${bytes.size} bytes)")

        val html = bytes.toString(Charsets.UTF_8)
        assertTrue(html.contains("매물별 상세 보고서"), "탭 섹션 누락")
        assertTrue(
            html.contains("동교로 194") && html.contains("동교로 195") && html.contains("동교로25길 34"),
            "세 매물 주소 탭 누락"
        )
        // rank별 머지가 올바른지 — 2순위 고유 문구가 실제로 들어갔는지
        assertTrue(html.contains("고정비가 가장 낮아"), "rank2 매물 고유 텍스트 누락(머지 오류 가능)")
        assertTrue(html.contains("당신에게 맞는 선택"), "공통 하단 섹션 누락")
    }

    // 회귀: 실제 assembler 의 top3 항목엔 rank 가 없다. rank 없이도 매물 탭이 비지 않아야 한다(인덱스 폴백).
    @Test
    fun `top3에 rank 없어도 매물 탭이 렌더된다`() {
        val full = mapper.readValue(resource("/report/sample_report_v65.json"), tr)
        val overviewPiece = full.filterKeys { it != "property_reports" }
        val propByRank = (full["property_reports"] as List<*>).map { it as Map<*, *> }
            .associateBy { (it["rank"] as Number).toInt() }

        val fake = object : LlmTextClient {
            override fun isAvailable() = true
            override fun generate(systemPrompt: String, userPrompt: String, temperature: Double): String? {
                if (userPrompt.contains("공통 영역만")) return mapper.writeValueAsString(overviewPiece)
                val rank = Regex("rank (\\d+) 매물").find(userPrompt)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                return mapper.writeValueAsString(propByRank[rank])
            }
        }

        // 프로덕션처럼 top3 에서 rank 키 제거
        val input = mapper.readValue(resource("/report/sample_input_v65.json"), tr).toMutableMap()
        val sa = (input["saved_analysis"] as Map<*, *>).toMutableMap()
        sa["top3"] = (sa["top3"] as List<*>).map { (it as Map<*, *>).filterKeys { k -> k != "rank" } }
        input["saved_analysis"] = sa

        val result = ReportLlmComposer(fake, mapper).compose(input)
        assertEquals("openai", result.source)
        val html = ReportHtmlRenderer().render(result.report!!, input).toString(Charsets.UTF_8)

        assertTrue(html.contains("매물 3개 비교"), "매물 0개로 비어버림(rank 매칭 실패)")
        assertTrue(
            html.contains("동교로 194") && html.contains("동교로 195") && html.contains("동교로25길 34"),
            "rank 없을 때 매물 탭이 비었음"
        )
    }

    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.readBytes().decodeToString()
}
