package com.sanggwonai.api.analysis.controller

import com.sanggwonai.api.analysis.controller.request.CreateAnalysisRequest
import com.sanggwonai.api.analysis.controller.request.PatchAnalysisRequest
import com.sanggwonai.api.analysis.controller.response.AnalysisErrorResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisLinksResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisPollingResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisRecommendationResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisRecommendationsResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisSectionTodoResponse
import com.sanggwonai.api.analysis.controller.response.AnalysisStepResponse
import com.sanggwonai.api.analysis.controller.response.CreateAnalysisResponse
import com.sanggwonai.api.analysis.dto.AnalysisErrorDto
import com.sanggwonai.api.analysis.dto.AnalysisLinksDto
import com.sanggwonai.api.analysis.dto.AnalysisPollingData
import com.sanggwonai.api.analysis.dto.AnalysisRecommendationDto
import com.sanggwonai.api.analysis.dto.AnalysisRecommendationsDto
import com.sanggwonai.api.analysis.dto.AnalysisSectionTodoDto
import com.sanggwonai.api.analysis.dto.AnalysisStepDto
import com.sanggwonai.api.analysis.dto.CreateAnalysisData
import com.sanggwonai.api.analysis.facade.AnalysisFacade
import com.sanggwonai.api.common.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/v1/analyses")
@Tag(name = "분석", description = "입지 분석 생성/조회/상세 섹션 API 모음임")
@SecurityRequirement(name = "bearerAuth")
class AnalysisController(
    private val analysisFacade: AnalysisFacade
) {
    @PostMapping
    @Operation(
        summary = "입지 분석 생성함",
        description = "업종/지역/예산 조건으로 분석 작업을 생성하고 비동기 처리 시작함."
    )
    fun create(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @Valid @RequestBody request: CreateAnalysisRequest
    ): ResponseEntity<ApiResponse<CreateAnalysisResponse>> {
        val data = analysisFacade.create(authorization, request)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "분석 상태 조회함",
        description = "분석 진행률/상태를 폴링 방식으로 조회함."
    )
    fun getPolling(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisPollingResponse>> {
        val data = analysisFacade.polling(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestParam(name = "limit", required = false) limit: Int?,
        @RequestParam(name = "saved", required = false) saved: Boolean?
    ): ResponseEntity<ApiResponse<ListAnalysesResponse>> {
        val items = analysisFacade.list(authorization, limit, saved).map(::toResponse)
        return ResponseEntity.ok(ApiResponse(ListAnalysesResponse(items = items, nextCursor = null)))
    }

    @PatchMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun patch(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String,
        @RequestBody(required = false) request: PatchAnalysisRequest?
    ): ResponseEntity<ApiResponse<AnalysisPollingResponse>> {
        val data = analysisFacade.patch(authorization, analysisId, request)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @DeleteMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun delete(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<DeleteAnalysisResponse>> {
        analysisFacade.delete(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(DeleteAnalysisResponse(ok = true)))
    }

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "분석 SSE 이벤트 구독함",
        description = "분석 진행 이벤트를 Server-Sent Events 스트림으로 수신함."
    )
    fun events(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): SseEmitter = analysisFacade.events(authorization, analysisId)

    @GetMapping("/{id}/recommended-properties", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "추천 매물 섹션 조회함",
        description = "분석 조건에 맞는 공실을 점수순으로 정렬한 Top 3 추천 매물을 조회함."
    )
    fun recommendedProperties(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisRecommendationsResponse>> {
        val data = analysisFacade.recommendedProperties(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/key-metrics", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "주요 지표 섹션 조회함",
        description = "상세 페이지의 주요 지표 섹션 데이터를 조회함. 현재 응답 필드는 TODO 상태임."
    )
    fun keyMetrics(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisSectionTodoResponse>> {
        val data = analysisFacade.keyMetrics(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/foot-traffic", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "유동인구 섹션 조회함",
        description = "상세 페이지의 유동인구 섹션 데이터를 조회함. 현재 응답 필드는 TODO 상태임."
    )
    fun footTraffic(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisSectionTodoResponse>> {
        val data = analysisFacade.footTraffic(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/competition", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "경쟁 점포 섹션 조회함",
        description = "상세 페이지의 경쟁 점포 섹션 데이터를 조회함. 현재 응답 필드는 TODO 상태임."
    )
    fun competition(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisSectionTodoResponse>> {
        val data = analysisFacade.competition(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/estimated-revenue", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "추정 매출 섹션 조회함",
        description = "상세 페이지의 추정 매출 섹션 데이터를 조회함. 현재 응답 필드는 TODO 상태임."
    )
    fun estimatedRevenue(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisSectionTodoResponse>> {
        val data = analysisFacade.estimatedRevenue(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/industry-growth", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "업종 성장률 섹션 조회함",
        description = "상세 페이지의 업종 성장률 섹션 데이터를 조회함. 현재 응답 필드는 TODO 상태임."
    )
    fun industryGrowth(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisSectionTodoResponse>> {
        val data = analysisFacade.industryGrowth(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @GetMapping("/{id}/accessibility", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "입지 접근성 섹션 조회함",
        description = "상세 페이지의 입지 접근성 섹션 데이터를 조회함. 현재 응답 필드는 TODO 상태임."
    )
    fun accessibility(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("id") analysisId: String
    ): ResponseEntity<ApiResponse<AnalysisSectionTodoResponse>> {
        val data = analysisFacade.accessibility(authorization, analysisId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    private fun toResponse(data: CreateAnalysisData): CreateAnalysisResponse {
        return CreateAnalysisResponse(
            id = data.id,
            vacancyId = data.vacancyId,
            status = data.status,
            progress = data.progress,
            createdAt = data.createdAt,
            estimatedSeconds = data.estimatedSeconds,
            analyzedVacancyCount = data.analyzedVacancyCount,
            saved = data.saved,
            links = toResponse(data.links),
            recommendations = data.recommendations.map(::toResponse)
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
            vacancyId = data.vacancyId,
            status = data.status,
            progress = data.progress,
            step = toResponse(data.step),
            createdAt = data.createdAt,
            completedAt = data.completedAt,
            error = toResponse(data.error),
            saved = data.saved,
            businessTypeKey = data.businessTypeKey,
            transactionType = data.transactionType,
            region = data.region,
            centerLat = data.centerLat,
            centerLng = data.centerLng,
            radiusM = data.radiusM,
            budgetDepositMax = data.budgetDepositMax,
            budgetRentMax = data.budgetRentMax,
            budgetMaintenanceFeeMax = data.budgetMaintenanceFeeMax,
            budgetPremiumMax = data.budgetPremiumMax,
            budgetSalePriceMax = data.budgetSalePriceMax,
            topScore = data.topScore,
            analyzedVacancyCount = data.analyzedVacancyCount,
            recommendationCount = data.recommendationCount
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

    private fun toResponse(data: AnalysisRecommendationsDto): AnalysisRecommendationsResponse {
        return AnalysisRecommendationsResponse(
            analysisId = data.analysisId,
            sectionKey = data.sectionKey,
            sectionLabel = data.sectionLabel,
            todo = data.todo,
            recommendations = data.recommendations.map(::toResponse),
            updatedAt = data.updatedAt
        )
    }

    private fun toResponse(data: AnalysisRecommendationDto): AnalysisRecommendationResponse {
        return AnalysisRecommendationResponse(
            rank = data.rank,
            vacancyId = data.vacancyId,
            recommended = data.recommended,
            score = data.score,
            distanceM = data.distanceM,
            areaId = data.areaId,
            latitude = data.latitude,
            longitude = data.longitude,
            monthlyRent = data.monthlyRent,
            deposit = data.deposit,
            maintenanceFee = data.maintenanceFee,
            premium = data.premium,
            salePrice = data.salePrice,
            transactionType = data.transactionType,
            facilityTotalSize = data.facilityTotalSize,
            locationArea = data.locationArea,
            category = data.category,
            roadAddress = data.roadAddress,
            lotAddress = data.lotAddress,
            businessMiddleCategoryName = data.businessMiddleCategoryName,
            businessSubCategoryName = data.businessSubCategoryName,
            floatingPopulationAnnualTotal = data.floatingPopulationAnnualTotal,
            restaurantCount500m = data.restaurantCount500m,
            cafeCount500m = data.cafeCount500m,
            industryGrowthRate500m = data.industryGrowthRate500m,
            averageSalesPerStore = data.averageSalesPerStore,
            busStopInfo = data.busStopInfo,
            subwayStationInfo = data.subwayStationInfo,
            parkingInfo = data.parkingInfo,
            hourlyFloatingPopulation = data.hourlyFloatingPopulation
        )
    }
}

data class ListAnalysesResponse(
    val items: List<AnalysisPollingResponse>,
    val nextCursor: String?
)

data class DeleteAnalysisResponse(
    val ok: Boolean
)
