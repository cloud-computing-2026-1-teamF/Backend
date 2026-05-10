package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.RankedVacancy
import com.sanggwonai.api.vacancy.dto.VacancySearchCriteria
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class VacancyRankingService(
    private val vacancyDataset: VacancyDataset
) {
    @Transactional(readOnly = true)
    fun findTop(criteria: VacancySearchCriteria, limit: Int = 3): List<RankedVacancy> {
        val snapshot = vacancyDataset.snapshot()
        return snapshot.vacancies
            .asSequence()
            .mapNotNull { vacancy -> vacancy.toDistanceCandidate(criteria, snapshot) }
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
                    common = candidate.common,
                    spatial = candidate.spatial,
                    categoryId = candidate.scoreEntity.id.categoryId,
                    categoryName = snapshot.categoryName(candidate.scoreEntity.id.categoryId),
                    rank = index + 1,
                    score = candidate.score,
                    distanceM = candidate.distanceM
                )
            }
            .toList()
    }

    private fun VacancyEntity.toDistanceCandidate(
        criteria: VacancySearchCriteria,
        snapshot: VacancyDatasetSnapshot
    ): VacancyDistanceCandidate? {
        val common = snapshot.commonByProperty[id] ?: return null
        // Spatial filter (radius) is the source of truth — see findTop. Two different
        // area_id encodings exist for the same dong (e.g. 11140660 vs 11440540 for
        // 서교동), so a strict area_id match drops vacancies that sit well within
        // the user's radius. Distance + budget are enough.
        if (!fitsBudget(criteria)) return null

        val scoreEntity = snapshot.scoreFor(id, criteria.categoryId) ?: return null
        val spatial = snapshot.spatialFor(id, scoreEntity)
        val vacancyLat = latitude?.toDouble() ?: return null
        val vacancyLng = longitude?.toDouble() ?: return null
        return VacancyDistanceCandidate(
            vacancy = this,
            common = common,
            scoreEntity = scoreEntity,
            spatial = spatial,
            score = scoreEntity.scorePercent(),
            distanceM = distanceMeters(criteria.latitude, criteria.longitude, vacancyLat, vacancyLng)
        )
    }

    private fun VacancyEntity.fitsBudget(criteria: VacancySearchCriteria): Boolean {
        val rent = monthlyRent
        val depositAmount = deposit
        val maintenance = maintenanceFee
        criteria.rentMax?.let { if (rent == null || rent > it) return false }
        criteria.depositMax?.let { if (depositAmount == null || depositAmount > it) return false }
        criteria.maintenanceFeeMax?.let {
            if (maintenance == null || maintenance > it) return false
        }
        return true
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
    val common: VacancyCommonFeatureEntity,
    val scoreEntity: VacancyCategoryScoreEntity,
    val spatial: VacancyCategorySpatialEntity?,
    val score: BigDecimal,
    val distanceM: Int
)
