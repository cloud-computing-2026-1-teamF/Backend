package com.sanggwonai.api.report.service

import com.sanggwonai.api.auth.service.AuthContext
import org.springframework.stereotype.Service

/** 생성된 보고서 PDF + 출처(openai/...). */
class GeneratedReport(val pdf: ByteArray, val source: String)

/**
 * 보고서 생성 오케스트레이션 (Step 5).
 * ReportPromptService(조립+OpenAI+검증) -> ReportPdfRenderer(PDF).
 * LLM 실패/검증 실패면 null -> 컨트롤러가 503 -> 프론트가 샘플 PDF로 폴백.
 */
@Service
class ReportService(
    private val promptService: ReportPromptService,
    private val pdfRenderer: ReportPdfRenderer
) {
    fun generate(authContext: AuthContext, analysisId: String): GeneratedReport? {
        val result = promptService.generate(authContext, analysisId)
        val report = result.report ?: return null
        return GeneratedReport(pdfRenderer.render(report), result.source)
    }
}
