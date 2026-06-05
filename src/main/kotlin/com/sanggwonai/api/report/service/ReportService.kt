package com.sanggwonai.api.report.service

import com.sanggwonai.api.auth.service.AuthContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 생성된 보고서 HTML + 출처(openai/...). */
class GeneratedReport(val html: ByteArray, val source: String)

/**
 * 분석 이력 기반 AI 보고서 서비스.
 * OpenAI가 생성한 구조화 JSON을 검증한 뒤, 서버 HTML 렌더러로 standalone 보고서를 만든다.
 */
@Service
class ReportService(
    private val promptService: ReportPromptService,
    private val htmlRenderer: ReportHtmlRenderer
) {
    private val logger = LoggerFactory.getLogger(ReportService::class.java)

    fun generate(authContext: AuthContext, analysisId: String): GeneratedReport? {
        val result = promptService.generate(authContext, analysisId)
        val report = result.report
        if (report == null) {
            logger.warn("AI report generation failed for analysisId={} source={}", analysisId, result.source)
            return null
        }
        return GeneratedReport(htmlRenderer.render(report, result.input), result.source)
    }
}
