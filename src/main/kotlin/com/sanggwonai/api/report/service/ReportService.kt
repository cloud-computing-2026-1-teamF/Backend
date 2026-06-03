package com.sanggwonai.api.report.service

import com.sanggwonai.api.auth.service.AuthContext
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/** 생성된 보고서 HTML + 출처(openai/static-demo/...). */
class GeneratedReport(val html: ByteArray, val source: String)

/**
 * 발표 시연용 보고서 서비스.
 *
 * 실제 OpenAI 호출 없이, 검수된 static HTML을 30초 지연 후 반환한다.
 * 프론트는 기존 생성 플로우와 동일하게 기다렸다가 파일을 다운로드한다.
 */
@Service
class ReportService {
    private val demoReport = ClassPathResource("report/static/demo-analysis-report.html")

    fun generate(authContext: AuthContext, analysisId: String): GeneratedReport? {
        val _unused = authContext to analysisId
        waitForDemoGeneration()
        if (!demoReport.exists()) return null
        val html = demoReport.inputStream.use { it.readBytes() }
        return GeneratedReport(html, "static-demo")
    }

    private fun waitForDemoGeneration() {
        try {
            Thread.sleep(DEMO_DELAY_MILLIS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val DEMO_DELAY_MILLIS = 30_000L
    }
}
