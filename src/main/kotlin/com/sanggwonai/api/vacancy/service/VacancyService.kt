package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.repository.VacancyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VacancyService(
    private val vacancyRepository: VacancyRepository
) {
    @Transactional(readOnly = true)
    fun list(areaId: String?): List<VacancyDto> {
        val vacancies = if (areaId.isNullOrBlank()) {
            vacancyRepository.findAllByOrderByIdAsc()
        } else {
            vacancyRepository.findAllByAreaIdOrderByIdAsc(areaId)
        }
        return vacancies.map(::toDto)
    }

    @Transactional(readOnly = true)
    fun get(id: String): VacancyDto {
        val vacancy = vacancyRepository.findById(id)
            .orElseThrow { ApiException.of(ErrorType.VACANCY_NOT_FOUND) }
        return toDto(vacancy)
    }

    private fun toDto(entity: VacancyEntity): VacancyDto {
        return VacancyDto(
            id = entity.id,
            areaId = entity.areaId,
            monthlyRent = entity.monthlyRent,
            deposit = entity.deposit,
            maintenanceFee = entity.maintenanceFee,
            floatingPopulationAnnualTotal = entity.floatingPopulationAnnualTotal,
            residentPopulationAnnualTotal = entity.residentPopulationAnnualTotal,
            workerPopulationAnnualTotal = entity.workerPopulationAnnualTotal,
            floatingPopulationQuarterlyAverage = entity.floatingPopulationQuarterlyAverage,
            residentPopulationQuarterlyAverage = entity.residentPopulationQuarterlyAverage,
            workerPopulationQuarterlyAverage = entity.workerPopulationQuarterlyAverage,
            restaurantCount250m = entity.restaurantCount250m,
            cafeCount250m = entity.cafeCount250m,
            industryGrowthRate250m = entity.industryGrowthRate250m,
            restaurantCount500m = entity.restaurantCount500m,
            cafeCount500m = entity.cafeCount500m,
            industryGrowthRate500m = entity.industryGrowthRate500m,
            restaurantCount1000m = entity.restaurantCount1000m,
            cafeCount1000m = entity.cafeCount1000m,
            industryGrowthRate1000m = entity.industryGrowthRate1000m,
            category = entity.category,
            businessMiddleCategoryName = entity.businessMiddleCategoryName,
            businessSubCategoryName = entity.businessSubCategoryName,
            multiUseFacility = entity.multiUseFacility,
            facilityTotalSize = entity.facilityTotalSize,
            locationArea = entity.locationArea,
            eveningPopulationRatio = entity.eveningPopulationRatio,
            lateNightPopulationRatio = entity.lateNightPopulationRatio,
            morningPopulationRatio = entity.morningPopulationRatio,
            weekendPopulationRatio = entity.weekendPopulationRatio,
            age2030PopulationRatio = entity.age2030PopulationRatio,
            age40PlusPopulationRatio = entity.age40PlusPopulationRatio,
            femalePopulationRatio = entity.femalePopulationRatio,
            residentToFloatingRatio = entity.residentToFloatingRatio,
            workerToFloatingRatio = entity.workerToFloatingRatio,
            officialLandPrice = entity.officialLandPrice,
            closureRate = entity.closureRate,
            openingRate = entity.openingRate,
            averageSalesPerStore = entity.averageSalesPerStore,
            timeBasedSalesRatio = entity.timeBasedSalesRatio,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
