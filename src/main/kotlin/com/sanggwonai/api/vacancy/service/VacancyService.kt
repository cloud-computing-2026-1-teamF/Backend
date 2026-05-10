package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.dto.VacancyExplorerCriteria
import com.sanggwonai.api.vacancy.dto.VacancyExplorerResult
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSummary
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.repository.VacancyRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

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
    fun search(criteria: VacancyExplorerCriteria): VacancyExplorerResult {
        val specification = buildSpecification(criteria)
        val pageRequest = PageRequest.of(
            criteria.page.coerceAtLeast(0),
            criteria.size.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE),
            criteria.sort.toSort()
        )
        val page = vacancyRepository.findAll(specification, pageRequest)
        val matchedVacancies = vacancyRepository.findAll(specification)

        return VacancyExplorerResult(
            items = page.content.map(::toDto),
            total = page.totalElements,
            page = page.number,
            size = page.size,
            totalPages = page.totalPages,
            summary = summarize(matchedVacancies)
        )
    }

    @Transactional(readOnly = true)
    fun get(id: String): VacancyDto {
        val vacancy = vacancyRepository.findById(id)
            .orElseThrow { ApiException.of(ErrorType.VACANCY_NOT_FOUND) }
        return toDto(vacancy)
    }

    private fun buildSpecification(criteria: VacancyExplorerCriteria): Specification<VacancyEntity> {
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            criteria.areaId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                predicates += criteriaBuilder.equal(root.get<String>("areaId"), it)
            }
            criteria.q?.trim()?.lowercase(Locale.KOREA)?.takeIf { it.isNotEmpty() }?.let {
                val keyword = "%$it%"
                predicates += criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get<String>("id")), keyword),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get<String>("category")), keyword),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get<String>("businessMiddleCategoryName")), keyword),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get<String>("businessSubCategoryName")), keyword)
                )
            }
            criteria.rentMax?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get<Long>("monthlyRent"), it)
            }
            criteria.depositMax?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get<Long>("deposit"), it)
            }
            criteria.maintenanceFeeMax?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get<Long>("maintenanceFee"), it)
            }
            criteria.scoreMin?.let {
                predicates += criteriaBuilder.greaterThanOrEqualTo(root.get<BigDecimal>("survivalScore"), it)
            }
            criteria.areaMin?.let {
                predicates += criteriaBuilder.greaterThanOrEqualTo(root.get<BigDecimal>("locationArea"), it)
            }
            criteria.areaMax?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get<BigDecimal>("locationArea"), it)
            }

            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    private fun summarize(vacancies: List<VacancyEntity>): VacancyExplorerSummary {
        return VacancyExplorerSummary(
            total = vacancies.size.toLong(),
            averageScore = averageDecimal(vacancies.mapNotNull { it.survivalScore }),
            averageRent = averageLong(vacancies.mapNotNull { it.monthlyRent }),
            averageDeposit = averageLong(vacancies.mapNotNull { it.deposit }),
            averageMaintenanceFee = averageLong(vacancies.mapNotNull { it.maintenanceFee }),
            minRent = vacancies.mapNotNull { it.monthlyRent }.minOrNull(),
            maxRent = vacancies.mapNotNull { it.monthlyRent }.maxOrNull(),
            areaCount = vacancies.map { it.areaId }.distinct().size
        )
    }

    private fun averageLong(values: List<Long>): BigDecimal? {
        if (values.isEmpty()) return null
        val sum = values.fold(BigDecimal.ZERO) { acc, value -> acc + BigDecimal.valueOf(value) }
        return sum.divide(BigDecimal.valueOf(values.size.toLong()), SUMMARY_SCALE, RoundingMode.HALF_UP)
    }

    private fun averageDecimal(values: List<BigDecimal>): BigDecimal? {
        if (values.isEmpty()) return null
        val sum = values.fold(BigDecimal.ZERO) { acc, value -> acc + value }
        return sum.divide(BigDecimal.valueOf(values.size.toLong()), SUMMARY_SCALE, RoundingMode.HALF_UP)
    }

    private fun toDto(entity: VacancyEntity): VacancyDto {
        return VacancyDto(
            id = entity.id,
            areaId = entity.areaId,
            monthlyRent = entity.monthlyRent,
            deposit = entity.deposit,
            maintenanceFee = entity.maintenanceFee,
            latitude = entity.latitude,
            longitude = entity.longitude,
            survivalScore = entity.survivalScore,
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

    companion object {
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 100
        private const val SUMMARY_SCALE = 2
    }
}
