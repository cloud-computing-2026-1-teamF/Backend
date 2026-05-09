package com.sanggwonai.api.vacancy.controller

import com.sanggwonai.api.common.api.ApiResponse
import com.sanggwonai.api.vacancy.controller.response.VacancyResponse
import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.facade.VacancyFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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

    private fun toResponse(data: VacancyDto): VacancyResponse {
        return VacancyResponse(
            id = data.id,
            areaId = data.areaId,
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
            category = data.category,
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
            createdAt = data.createdAt,
            updatedAt = data.updatedAt
        )
    }
}
