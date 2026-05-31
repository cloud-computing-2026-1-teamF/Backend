package com.sanggwonai.api.report.controller

import com.sanggwonai.api.report.facade.ReportFacade
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/analyses")
class ReportController(
    private val reportFacade: ReportFacade
) {
    /**
     * 분석이력 1건 → AI 입지 분석 보고서 PDF (Top3 묶음).
     * 성공: application/pdf 바이트. 실패(LLM 비활성/검증실패): 503 → 프론트가 샘플 PDF 폴백.
     */
    @PostMapping("/{id}/report", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun generate(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ByteArray> {
        val gen = reportFacade.generate(authorization, analysisId)
            ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-$analysisId.pdf\"")
            .header("X-Report-Source", gen.source)
            .body(gen.pdf)
    }
}
