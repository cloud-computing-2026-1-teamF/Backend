package com.sanggwonai.api.analysis.controller

import com.sanggwonai.api.analysis.controller.request.CreateAnalysisRequest
import com.sanggwonai.api.analysis.controller.response.AnalysisErrorResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisLinksResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisPollingResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisSectionTodoResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisStepResponse
import com.sanggwonai.api.analysis.controller.response.CreateAnalysisResponse
import com.sanggwonai.api.analysis.dto.AnalysisErrorDto
import com.sanggwonai.api.analysis.dto.AnalysisLinksDto
import com.sanggwonai.api.analysis.dto.AnalysisPollingData
import com.sanggwonai.api.analysis.dto.AnalysisSectionTodoDto
import com.sanggwonai.api.analysis.dto.AnalysisStepDto
import com.sanggwonai.api.analysis.dto.CreateAnalysisData
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
        val data = analysisFacade.create(authorization, request)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPolling(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.polling(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): SseEmitter = analysisFacade.events(authorization, analysisId)

    @GetMapping("/{id}/recommended-properties", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun recommendedProperties(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.recommendedProperties(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/key-metrics", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun keyMetrics(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.keyMetrics(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/foot-traffic", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun footTraffic(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.footTraffic(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/competition", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun competition(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.competition(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/estimated-revenue", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun estimatedRevenue(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.estimatedRevenue(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/industry-growth", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun industryGrowth(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.industryGrowth(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/accessibility", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun accessibility(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<*>> {
        val data = analysisFacade.accessibility(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    private fun toResponse(data: CreateAnalysisData): CreateAnalysisResponse {
        return CreateAnalysisResponse(
            id = data.id,
            status = data.status,
            progress = data.progress,
            createdAt = data.createdAt,
            estimatedSeconds = data.estimatedSeconds,
            links = toResponse(data.links)
        )
    }

    private fun toResponse(data: AnalysisLinksDto): AnalysisLinksResponse {
        return AnalysisLinksResponse(
            self = data.self,
            events = data.events
        )
    }

    private fun toResponse(data: AnalysisPollingData): AnalysisPollingResponse {
        return AnalysisPollingResponse(
            id = data.id,
            status = data.status,
            progress = data.progress,
            step = toResponse(data.step),
            createdAt = data.createdAt,
            completedAt = data.completedAt,
            error = toResponse(data.error)
        )
    }

    private fun toResponse(data: AnalysisStepDto?): AnalysisStepResponse? {
        if (data == null) {
            return null
        }
        return AnalysisStepResponse(
            index = data.index,
            total = data.total,
            label = data.label
        )
    }

    private fun toResponse(data: AnalysisErrorDto?): AnalysisErrorResponse? {
        if (data == null) {
            return null
        }
        return AnalysisErrorResponse(
            code = data.code,
            message = data.message
        )
    }

    private fun toResponse(data: AnalysisSectionTodoDto): AnalysisSectionTodoResponse {
        return AnalysisSectionTodoResponse(
            analysisId = data.analysisId,
            sectionKey = data.sectionKey,
            sectionLabel = data.sectionLabel,
            todo = data.todo,
            updatedAt = data.updatedAt
        )
    }
}
