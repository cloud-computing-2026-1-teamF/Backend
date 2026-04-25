package com.sanggwonai.api.analysis.controller

import com.sanggwonai.api.analysis.dto.CreateAnalysisRequest
import com.sanggwonai.api.analysis.facade.AnalysisFacade
import com.sanggwonai.api.common.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/v1/analyses")
class AnalysisController(
    private val analysisFacade: AnalysisFacade
) {
    @PostMapping
    fun create(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @Valid @RequestBody request: CreateAnalysisRequest
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse(analysisFacade.create(authorization, request)))
    }

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPolling(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse(analysisFacade.polling(authorization, analysisId)))
    }

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): SseEmitter = analysisFacade.events(authorization, analysisId)
}
