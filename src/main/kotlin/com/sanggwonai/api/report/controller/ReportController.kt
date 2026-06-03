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
     * 분석이력 1건 → 발표 시연용 AI 입지 분석 보고서 HTML.
     * 성공: 30초 지연 후 검수된 standalone text/html 바이트 반환.
     */
    @PostMapping("/{id}/report", produces = [MediaType.TEXT_HTML_VALUE])
    fun generate(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ByteArray> {
        val gen = reportFacade.generate(authorization, analysisId)
            ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-$analysisId.html\"")
            .header("X-Report-Source", gen.source)
            .body(gen.html)
    }
}
