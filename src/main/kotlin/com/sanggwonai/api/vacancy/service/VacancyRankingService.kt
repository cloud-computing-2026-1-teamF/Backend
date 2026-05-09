package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.RankedVacancy
import com.sanggwonai.api.vacancy.dto.VacancySearchCriteria
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.repository.VacancyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class VacancyRankingService(
    private val vacancyRepository: VacancyRepository
) {
    @Transactional(readOnly = true)
    fun findTop(criteria: VacancySearchCriteria, limit: Int = 3): List<RankedVacancy> {
        return vacancyRepository.findBudgetAndLocationCandidates(
            areaId = criteria.areaId,
            rentMax = criteria.rentMax,
            depositMax = criteria.depositMax,
            maintenanceFeeMax = criteria.maintenanceFeeMax
        )
            .mapNotNull { vacancy -> vacancy.toDistanceCandidate(criteria) }
            .filter { candidate -> candidate.distanceM <= criteria.radiusM }
            .sortedWith(
                compareByDescending<VacancyDistanceCandidate> { it.score }
                    .thenBy { it.distanceM }
                    .thenBy { it.vacancy.id }
            )
            .take(limit)
            .mapIndexed { index, candidate ->
                RankedVacancy(
                    vacancy = candidate.vacancy,
                    rank = index + 1,
                    score = candidate.score,
                    distanceM = candidate.distanceM
                )
            }
    }

    private fun VacancyEntity.toDistanceCandidate(criteria: VacancySearchCriteria): VacancyDistanceCandidate? {
        val vacancyLat = latitude?.toDouble() ?: return null
        val vacancyLng = longitude?.toDouble() ?: return null
        return VacancyDistanceCandidate(
            vacancy = this,
            score = resolvedScore(),
            distanceM = distanceMeters(criteria.latitude, criteria.longitude, vacancyLat, vacancyLng)
        )
    }

    private fun VacancyEntity.resolvedScore(): BigDecimal {
        survivalScore?.let { return it.setScale(2, RoundingMode.HALF_UP) }

        val dailyFootTraffic = (floatingPopulationAnnualTotal ?: 0L).toDouble() / 365.0
        val trafficScore = min(24.0, ln(dailyFootTraffic + 1.0) * 2.5)
        val growthScore = min(14.0, maxOf(0.0, industryGrowthRate500m?.toDouble() ?: 0.0) * 0.9)
        val salesScore = min(18.0, (averageSalesPerStore?.toDouble() ?: 0.0) / 120.0)
        val competitionCount = (restaurantCount500m ?: 0) + (cafeCount500m ?: 0)
        val competitionScore = when {
            competitionCount in 2..8 -> 16.0
            competitionCount < 2 -> 10.0
            else -> maxOf(4.0, 16.0 - (competitionCount - 8) * 1.2)
        }
        val closurePenalty = min(12.0, maxOf(0.0, closureRate?.toDouble() ?: 0.0) * 2.0)
        val raw = 32.0 + trafficScore + growthScore + salesScore + competitionScore - closurePenalty
        return BigDecimal.valueOf(raw.coerceIn(1.0, 99.0)).setScale(2, RoundingMode.HALF_UP)
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val earthRadiusM = 6_371_000.0
        val latRad1 = Math.toRadians(lat1)
        val latRad2 = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLng = Math.toRadians(lng2 - lng1)
        val a = sin(deltaLat / 2).pow(2) +
            cos(latRad1) * cos(latRad2) * sin(deltaLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusM * c).toInt()
    }
}

private data class VacancyDistanceCandidate(
    val vacancy: VacancyEntity,
    val score: BigDecimal,
    val distanceM: Int
)

