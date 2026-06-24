package com.sanggwonai.api.vacancy.controller

import com.sanggwonai.api.common.api.ApiResponse
import com.sanggwonai.api.vacancy.controller.request.VacancyPromptParseRequest
import com.sanggwonai.api.vacancy.controller.request.VacancyPromptSearchRequest
import com.sanggwonai.api.vacancy.controller.request.VacancyStructuredSearchRequest
import com.sanggwonai.api.vacancy.controller.response.VacancyMetricDistributionResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyPromptParseResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyPromptSchemaResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyPromptSearchResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyMetricReferenceResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyHorizonScoreResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyResponse
import com.sanggwonai.api.vacancy.controller.response.VacancySearchResponse
import com.sanggwonai.api.vacancy.controller.response.VacancySearchSummaryResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyScoreExplanationResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyScoreFeatureContributionResponse
import com.sanggwonai.api.vacancy.dto.VacancyMetricDistribution
import com.sanggwonai.api.vacancy.dto.VacancyMetricReference
import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.dto.VacancyExplorerCriteria
import com.sanggwonai.api.vacancy.dto.VacancyExplorerResult
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSort
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSummary
import com.sanggwonai.api.vacancy.dto.VacancyHorizonScoreDto
import com.sanggwonai.api.vacancy.dto.VacancyScoreMode
import com.sanggwonai.api.vacancy.dto.VacancyScoreExplanationDto
import com.sanggwonai.api.vacancy.dto.VacancyScoreFeatureContributionDto
import com.sanggwonai.api.vacancy.facade.VacancyFacade
import com.sanggwonai.api.vacancy.service.VacancyPromptSchema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/v1/vacancies")
@Tag(name = "공실", description = "공실 매물/상권 피처 조회 API 모음임")
class VacancyController(
    private val vacancyFacade: VacancyFacade
) {
    @GetMapping
    @Operation(
        summary = "공실 목록 조회함",
        description = "areaId가 있으면 해당 행정동의 공실 피처를 조회하고, 없으면 전체 공실 피처를 조회함."
    )
    fun list(
        @RequestParam(name = "areaId", required = false) areaId: String?
    ): ResponseEntity<ApiResponse<List<VacancyResponse>>> {
        val data = vacancyFacade.list(areaId).map(::toResponse)
        return ResponseEntity.ok(ApiResponse(data))
    }

    @GetMapping("/search")
    @Operation(
        summary = "공실 탐색 조건 검색함",
        description = "공실 탐색 화면에서 쓰는 검색/필터/정렬/요약 데이터를 반환함."
    )
    fun search(
        @RequestParam(name = "areaId", required = false) areaId: String?,
        @RequestParam(name = "categoryId", required = false) categoryId: String?,
        @RequestParam(name = "scoreMode", required = false) scoreMode: String?,
        @RequestParam(name = "transactionType", required = false) transactionType: String?,
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(name = "latitude", required = false) latitude: Double?,
        @RequestParam(name = "longitude", required = false) longitude: Double?,
        @RequestParam(name = "radiusM", required = false) radiusM: Int?,
        @RequestParam(name = "rentMax", required = false) rentMax: Long?,
        @RequestParam(name = "depositMax", required = false) depositMax: Long?,
        @RequestParam(name = "maintenanceFeeMax", required = false) maintenanceFeeMax: Long?,
        @RequestParam(name = "premiumMax", required = false) premiumMax: Long?,
        @RequestParam(name = "salePriceMax", required = false) salePriceMax: Long?,
        @RequestParam(name = "scoreMin", required = false) scoreMin: BigDecimal?,
        @RequestParam(name = "areaMin", required = false) areaMin: BigDecimal?,
        @RequestParam(name = "areaMax", required = false) areaMax: BigDecimal?,
        @RequestParam(name = "page", required = false) page: Int?,
        @RequestParam(name = "size", required = false) size: Int?,
        @RequestParam(name = "sort", required = false) sort: String?
    ): ResponseEntity<ApiResponse<VacancySearchResponse>> {
        val criteria = VacancyExplorerCriteria(
            areaId = areaId,
            categoryId = categoryId,
            scoreMode = VacancyScoreMode.from(scoreMode),
            transactionType = transactionType,
            q = q,
            latitude = latitude,
            longitude = longitude,
            radiusM = radiusM,
            rentMax = rentMax,
            depositMax = depositMax,
            maintenanceFeeMax = maintenanceFeeMax,
            premiumMax = premiumMax,
            salePriceMax = salePriceMax,
            scoreMin = scoreMin,
            areaMin = areaMin,
            areaMax = areaMax,
            page = page ?: DEFAULT_PAGE,
            size = size ?: DEFAULT_PAGE_SIZE,
            sort = VacancyExplorerSort.from(sort)
        )
        return ResponseEntity.ok(ApiResponse(toSearchResponse(vacancyFacade.search(criteria))))
    }

    @GetMapping("/prompt/schema")
    @Operation(
        summary = "공실 프롬프트 필터 JSON 스키마 조회함",
        description = "OpenAI Structured Outputs에 넣는 정적 JSON schema와 enum 계약을 반환함."
    )
    fun promptSchema(): ResponseEntity<ApiResponse<VacancyPromptSchemaResponse>> {
        return ResponseEntity.ok(
            ApiResponse(
                VacancyPromptSchemaResponse(
                    schemaVersion = VacancyPromptSchema.VERSION,
                    jsonSchema = VacancyPromptSchema.jsonSchema,
                    openAiTextFormat = VacancyPromptSchema.openAiTextFormat,
                    enums = VacancyPromptSchema.enums,
                    example = VacancyPromptSchema.example
                )
            )
        )
    }

    @PostMapping("/structured-search")
    @Operation(
        summary = "공실 구조화 필터 검색함",
        description = "프롬프트 파싱 결과 JSON을 받아 DB 조건으로 후보를 좁힌 뒤 기존 공실 탐색 응답을 반환함."
    )
    fun structuredSearch(
        @RequestBody request: VacancyStructuredSearchRequest
    ): ResponseEntity<ApiResponse<VacancySearchResponse>> {
        return ResponseEntity.ok(ApiResponse(toSearchResponse(vacancyFacade.structuredSearch(request.filters))))
    }

    @PostMapping("/prompt/parse")
    @Operation(
        summary = "공실 자연어 프롬프트를 구조화 필터로 변환함",
        description = "OpenAI 설정이 켜져 있으면 Structured Outputs로 파싱하고, 아니면 저비용 로컬 파서를 사용함."
    )
    fun parsePrompt(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: VacancyPromptParseRequest
    ): ResponseEntity<ApiResponse<VacancyPromptParseResponse>> {
        val parsed = vacancyFacade.parsePrompt(authorization, request.prompt)
        return ResponseEntity.ok(
            ApiResponse(
                VacancyPromptParseResponse(
                    filters = parsed.filters,
                    source = parsed.source,
                    schemaVersion = parsed.schemaVersion
                )
            )
        )
    }

    @PostMapping("/prompt/search")
    @Operation(
        summary = "공실 자연어 프롬프트 검색함",
        description = "프롬프트를 구조화 필터로 변환하고 바로 DB 후보 검색 및 공실 탐색 응답을 반환함."
    )
    fun promptSearch(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: VacancyPromptSearchRequest
    ): ResponseEntity<ApiResponse<VacancyPromptSearchResponse>> {
        val (parsed, result) = vacancyFacade.promptSearch(authorization, request.prompt, request.page, request.size)
        return ResponseEntity.ok(
            ApiResponse(
                VacancyPromptSearchResponse(
                    filters = parsed.filters,
                    source = parsed.source,
                    schemaVersion = parsed.schemaVersion,
                    result = toSearchResponse(result)
                )
            )
        )
    }

    @GetMapping("/metric-reference")
    @Operation(
        summary = "공실 주요 지표 기준값 조회함",
        description = "선택 공실이 동일 업종 공실 분포에서 어느 위치인지 표시하기 위한 평균/백분위 기준값을 반환함."
    )
    fun metricReference(
        @RequestParam(name = "categoryId", required = false) categoryId: String?,
        @RequestParam(name = "vacancyId", required = false) vacancyId: String?
    ): ResponseEntity<ApiResponse<VacancyMetricReferenceResponse>> {
        return ResponseEntity.ok(ApiResponse(toMetricReferenceResponse(vacancyFacade.metricReference(categoryId, vacancyId))))
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "공실 상세 조회함",
        description = "공실 id 기준으로 매물/상권 피처를 조회함."
    )
    fun get(
        @PathVariable id: String
    ): ResponseEntity<ApiResponse<VacancyResponse>> {
        return ResponseEntity.ok(ApiResponse(toResponse(vacancyFacade.get(id))))
    }

    private fun toSearchResponse(data: VacancyExplorerResult): VacancySearchResponse {
        return VacancySearchResponse(
            items = data.items.map(::toResponse),
            total = data.total,
            page = data.page,
            size = data.size,
            totalPages = data.totalPages,
            summary = toSummaryResponse(data.summary)
        )
    }

    private fun toSummaryResponse(data: VacancyExplorerSummary): VacancySearchSummaryResponse {
        return VacancySearchSummaryResponse(
            total = data.total,
            averageScore = data.averageScore,
            averageRent = data.averageRent,
            averageDeposit = data.averageDeposit,
            averageSalePrice = data.averageSalePrice,
            averageMaintenanceFee = data.averageMaintenanceFee,
            minRent = data.minRent,
            maxRent = data.maxRent,
            areaCount = data.areaCount
        )
    }

    private fun toMetricReferenceResponse(data: VacancyMetricReference): VacancyMetricReferenceResponse {
        return VacancyMetricReferenceResponse(
            categoryId = data.categoryId,
            vacancyId = data.vacancyId,
            peerCount = data.peerCount,
            footTrafficDaily = toMetricDistributionResponse(data.footTrafficDaily),
            competition500m = toMetricDistributionResponse(data.competition500m),
            averageSalesMonthly = toMetricDistributionResponse(data.averageSalesMonthly)
        )
    }

    private fun toMetricDistributionResponse(data: VacancyMetricDistribution): VacancyMetricDistributionResponse {
        return VacancyMetricDistributionResponse(
            selected = data.selected,
            average = data.average,
            median = data.median,
            min = data.min,
            max = data.max,
            p10 = data.p10,
            p25 = data.p25,
            p75 = data.p75,
            p90 = data.p90,
            percentile = data.percentile
        )
    }

    private fun toResponse(data: VacancyDto): VacancyResponse {
        return VacancyResponse(
            id = data.id,
            areaId = data.areaId,
            areaName = data.areaName,
            categoryId = data.categoryId,
            category = data.category,
            recommended = data.recommended,
            monthlyRent = data.monthlyRent,
            deposit = data.deposit,
            maintenanceFee = data.maintenanceFee,
            premium = data.premium,
            salePrice = data.salePrice,
            latitude = data.latitude,
            longitude = data.longitude,
            survivalScore = data.survivalScore,
            horizonScores = data.horizonScores.map(::toResponse),
            scoreExplanation = toResponse(data.scoreExplanation),
            listingId = data.listingId,
            listingNumber = data.listingNumber,
            roadAddress = data.roadAddress,
            lotAddress = data.lotAddress,
            postalCode = data.postalCode,
            buildingName = data.buildingName,
            province = data.province,
            district = data.district,
            dong = data.dong,
            detailAddress = data.detailAddress,
            transactionType = data.transactionType,
            dedicatedArea = data.dedicatedArea,
            supplyArea = data.supplyArea,
            floor = data.floor,
            totalFloors = data.totalFloors,
            basementFloors = data.basementFloors,
            buildingType = data.buildingType,
            buildingUse = data.buildingUse,
            buildingGrade = data.buildingGrade,
            approvalDate = data.approvalDate,
            direction = data.direction,
            elevatorAvailable = data.elevatorAvailable,
            elevatorCount = data.elevatorCount,
            heatingType = data.heatingType,
            restroomType = data.restroomType,
            restroomCount = data.restroomCount,
            parkingAvailable = data.parkingAvailable,
            parkingCount = data.parkingCount,
            terrace = data.terrace,
            rooftop = data.rooftop,
            interior = data.interior,
            storage = data.storage,
            airConditioner = data.airConditioner,
            heater = data.heater,
            lateNightOperationAvailable = data.lateNightOperationAvailable,
            priceNegotiable = data.priceNegotiable,
            rentAdjustable = data.rentAdjustable,
            rentFreePeriodAvailable = data.rentFreePeriodAvailable,
            subway = data.subway,
            busStopInfo = data.busStopInfo,
            subwayStationInfo = data.subwayStationInfo,
            parkingInfo = data.parkingInfo,
            hourlyFloatingPopulation = data.hourlyFloatingPopulation,
            brokerageFee = data.brokerageFee,
            brokerageRate = data.brokerageRate,
            viewCount = data.viewCount,
            favoriteCount = data.favoriteCount,
            majorBusinessCategory = data.majorBusinessCategory,
            middleBusinessCategory = data.middleBusinessCategory,
            floatingPopulationAnnualTotal = data.floatingPopulationAnnualTotal,
            residentPopulationAnnualTotal = data.residentPopulationAnnualTotal,
            workerPopulationAnnualTotal = data.workerPopulationAnnualTotal,
            floatingPopulationQuarterlyAverage = data.floatingPopulationQuarterlyAverage,
            residentPopulationQuarterlyAverage = data.residentPopulationQuarterlyAverage,
            workerPopulationQuarterlyAverage = data.workerPopulationQuarterlyAverage,
            restaurantCount250m = data.restaurantCount250m,
            cafeCount250m = data.cafeCount250m,
            industryGrowthRate250m = data.industryGrowthRate250m,
            restaurantCount500m = data.restaurantCount500m,
            cafeCount500m = data.cafeCount500m,
            industryGrowthRate500m = data.industryGrowthRate500m,
            restaurantCount1000m = data.restaurantCount1000m,
            cafeCount1000m = data.cafeCount1000m,
            industryGrowthRate1000m = data.industryGrowthRate1000m,
            sameCategoryRestaurantCount250m = data.sameCategoryRestaurantCount250m,
            sameCategoryRestaurantCount500m = data.sameCategoryRestaurantCount500m,
            sameCategoryRestaurantCount1000m = data.sameCategoryRestaurantCount1000m,
            businessMiddleCategoryName = data.businessMiddleCategoryName,
            businessSubCategoryName = data.businessSubCategoryName,
            multiUseFacility = data.multiUseFacility,
            facilityTotalSize = data.facilityTotalSize,
            locationArea = data.locationArea,
            eveningPopulationRatio = data.eveningPopulationRatio,
            lateNightPopulationRatio = data.lateNightPopulationRatio,
            morningPopulationRatio = data.morningPopulationRatio,
            weekendPopulationRatio = data.weekendPopulationRatio,
            age2030PopulationRatio = data.age2030PopulationRatio,
            age40PlusPopulationRatio = data.age40PlusPopulationRatio,
            femalePopulationRatio = data.femalePopulationRatio,
            residentToFloatingRatio = data.residentToFloatingRatio,
            workerToFloatingRatio = data.workerToFloatingRatio,
            officialLandPrice = data.officialLandPrice,
            closureRate = data.closureRate,
            openingRate = data.openingRate,
            averageSalesPerStore = data.averageSalesPerStore,
            timeBasedSalesRatio = data.timeBasedSalesRatio,
            lateNightSalesRatio = data.lateNightSalesRatio,
            weekendSalesRatio = data.weekendSalesRatio,
            age2030SalesRatio = data.age2030SalesRatio,
            femaleSalesRatio = data.femaleSalesRatio,
            totalSpending = data.totalSpending,
            foodSpending = data.foodSpending,
            spendingPerStore = data.spendingPerStore,
            commercialTurnoverType = data.commercialTurnoverType,
            commercialGrowthType = data.commercialGrowthType,
            createdAt = data.createdAt,
            updatedAt = data.updatedAt
        )
    }

    private fun toResponse(data: VacancyHorizonScoreDto): VacancyHorizonScoreResponse {
        return VacancyHorizonScoreResponse(
            horizonYears = data.horizonYears,
            survivalScore = data.survivalScore,
            recommended = data.recommended
        )
    }

    private fun toResponse(data: VacancyScoreExplanationDto?): VacancyScoreExplanationResponse? {
        if (data == null) return null
        return VacancyScoreExplanationResponse(
            positive = data.positive.map(::toResponse),
            negative = data.negative.map(::toResponse),
            source = data.source
        )
    }

    private fun toResponse(data: VacancyScoreFeatureContributionDto): VacancyScoreFeatureContributionResponse {
        return VacancyScoreFeatureContributionResponse(
            direction = data.direction,
            rank = data.rank,
            featureKey = data.featureKey,
            featureLabel = data.featureLabel,
            featureDisplayValue = data.featureDisplayValue,
            impactValue = data.impactValue,
            impactPercent = data.impactPercent
        )
    }

    companion object {
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_PAGE_SIZE = 20
    }
}
