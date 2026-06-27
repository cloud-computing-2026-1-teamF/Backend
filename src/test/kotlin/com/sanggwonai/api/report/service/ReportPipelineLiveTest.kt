package com.sanggwonai.api.report.service

import com.sanggwonai.api.report.config.ReportProperties
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실제 OpenAI 로 v6.5 병렬 파이프라인을 끝까지 돌려보는 라이브 e2e — DB/인증 없이 LLM 부분만.
 *
 * 실행 (OpenAI 키 필요):
 *   export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
 *   export OPENAI_API_KEY="sk-..."
 *   # (선택) export OPENAI_REPORT_MODEL=gpt-5.4   # nano가 약하면 더 큰 모델로
 *   ./gradlew test --tests "*ReportPipelineLiveTest*"
 *   open build/report_live.html
 *
 * OPENAI_API_KEY 가 없으면 조용히 통과(skip)한다 — 일반 빌드/CI 를 깨지 않기 위함.
 */
class ReportPipelineLiveTest {

    private val mapper = JsonMapper.builder().build()
    private val tr = object : TypeReference<Map<String, Any?>>() {}

    @Test
    fun `실제 OpenAI로 v6_5 병렬 파이프라인 e2e`() {
        val key = System.getenv("OPENAI_API_KEY").orEmpty()
        if (key.isBlank()) {
            println("[SKIP] OPENAI_API_KEY 없음 — 라이브 e2e 테스트를 건너뜁니다.")
            return
        }

        val props = ReportProperties(
            openai = ReportProperties.OpenAi(
                enabled = true,
                apiKey = key,
                model = System.getenv("OPENAI_REPORT_MODEL") ?: "gpt-5.4-nano",
                timeoutSeconds = System.getenv("OPENAI_REPORT_TIMEOUT_SECONDS")?.toLongOrNull() ?: 90,
                maxOutputTokens = System.getenv("OPENAI_REPORT_MAX_OUTPUT_TOKENS")?.toIntOrNull() ?: 12000
            )
        )
        val composer = ReportLlmComposer(ReportLlmClient(props, mapper), mapper)

        val input = mapper.readValue(
            javaClass.getResourceAsStream("/report/sample_input_v65.json")!!.readBytes().decodeToString(),
            tr
        )

        val t0 = System.currentTimeMillis()
        val result = composer.compose(input)
        val elapsed = (System.currentTimeMillis() - t0) / 1000.0
        println("[LIVE] source=${result.source}  elapsed=${"%.1f".format(elapsed)}s  model=${props.openai.model}")

        assertEquals("openai", result.source, "LLM 생성 실패 — 위 로그(parse/검증 경고) 확인")
        val report = result.report!!
        val propReports = (report["property_reports"] as? List<*>)?.size ?: 0
        println("[LIVE] property_reports=$propReports  keys=${report.keys}")

        val bytes = ReportHtmlRenderer().render(report, input)
        File("build/report_live.html").apply { parentFile.mkdirs() }.writeBytes(bytes)
        println("[LIVE] wrote build/report_live.html (${bytes.size} bytes)")

        val html = bytes.toString(Charsets.UTF_8)
        assertTrue(html.contains("매물별 상세 보고서"), "매물 탭 섹션 누락")
        assertTrue(html.contains("당신에게 맞는 선택"), "하단 선택 가이드 누락")
        assertTrue(propReports >= 3, "property_reports 가 3개 미만: $propReports")
    }
}
