package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.business.repository.BusinessTypeRepository
import com.sanggwonai.api.vacancy.entity.VacancyCategoryKey
import com.sanggwonai.api.vacancy.entity.VacancyCategoryHorizonScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyAccessibilityFoottrafficEntity
import com.sanggwonai.api.vacancy.repository.VacancyAccessibilityFoottrafficRepository
import com.sanggwonai.api.vacancy.repository.VacancyCategoryHorizonScoreRepository
import com.sanggwonai.api.vacancy.repository.VacancyCategoryScoreRepository
import com.sanggwonai.api.vacancy.repository.VacancyCategorySpatialRepository
import com.sanggwonai.api.vacancy.repository.VacancyCommonFeatureRepository
import com.sanggwonai.api.vacancy.repository.VacancyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class VacancyDataset(
    private val vacancyRepository: VacancyRepository,
    private val commonFeatureRepository: VacancyCommonFeatureRepository,
    private val categoryScoreRepository: VacancyCategoryScoreRepository,
    private val categoryHorizonScoreRepository: VacancyCategoryHorizonScoreRepository,
    private val categorySpatialRepository: VacancyCategorySpatialRepository,
    private val accessibilityFoottrafficRepository: VacancyAccessibilityFoottrafficRepository,
    private val businessTypeRepository: BusinessTypeRepository
) {
    @Volatile
    private var cachedSnapshot: VacancyDatasetSnapshot? = null

    @Transactional(readOnly = true)
    fun snapshot(): VacancyDatasetSnapshot {
        cachedSnapshot?.let { return it }
        return synchronized(this) {
            cachedSnapshot ?: loadSnapshot().also { cachedSnapshot = it }
        }
    }

    private fun loadSnapshot(): VacancyDatasetSnapshot {
        val scores = categoryScoreRepository.findAll()
        val horizonScoresByKey = categoryHorizonScoreRepository.findAll()
            .groupBy { VacancyCategoryKey(it.id.propertyId, it.id.categoryId) }
            .mapValues { (_, scores) -> scores.sortedBy { it.id.horizonYears } }
        val bestScoresByProperty = scores
            .groupBy { it.id.propertyId }
            .mapValues { (_, propertyScores) -> bestScore(propertyScores) }

        val spatials = categorySpatialRepository.findAll()
        val spatialsByKey = spatials.associateBy { it.id }
        val vacancies = vacancyRepository.findAllByOrderByIdAsc()

        return VacancyDatasetSnapshot(
            vacancies = vacancies,
            vacancyById = vacancies.associateBy { it.id },
            commonByProperty = commonFeatureRepository.findAll().associateBy { it.propertyId },
            accessibilityByProperty = accessibilityFoottrafficRepository.findAll().associateBy { it.propertyId },
            scoreByKey = scores.associateBy { it.id },
            horizonScoresByKey = horizonScoresByKey,
            bestScoreByProperty = bestScoresByProperty,
            spatialByKey = spatialsByKey,
            categoryNameById = businessTypeRepository.findAllByOrderByBusinessKeyAsc()
                .associate { it.businessKey to it.label }
        )
    }

    private fun bestScore(scores: List<VacancyCategoryScoreEntity>): VacancyCategoryScoreEntity {
        return scores.minWith(
            compareByDescending<VacancyCategoryScoreEntity> { it.survivalScore ?: BigDecimal.ZERO }
                .thenByDescending { it.recommendedValue?.toInt() ?: 0 }
                .thenBy { it.id.categoryId }
        )
    }
}

data class VacancyDatasetSnapshot(
    val vacancies: List<VacancyEntity>,
    val vacancyById: Map<String, VacancyEntity>,
    val commonByProperty: Map<String, VacancyCommonFeatureEntity>,
    val accessibilityByProperty: Map<String, VacancyAccessibilityFoottrafficEntity>,
    val scoreByKey: Map<VacancyCategoryKey, VacancyCategoryScoreEntity>,
    val horizonScoresByKey: Map<VacancyCategoryKey, List<VacancyCategoryHorizonScoreEntity>>,
    val bestScoreByProperty: Map<String, VacancyCategoryScoreEntity>,
    val spatialByKey: Map<VacancyCategoryKey, VacancyCategorySpatialEntity>,
    val categoryNameById: Map<String, String>
) {
    fun bestScoreFor(propertyId: String): VacancyCategoryScoreEntity? {
        return bestScoreByProperty[propertyId]
    }

    fun categoryScoreFor(propertyId: String, categoryId: String): VacancyCategoryScoreEntity? {
        return scoreByKey[VacancyCategoryKey(propertyId, categoryId)]
    }

    fun horizonScoresFor(propertyId: String, categoryId: String?): List<VacancyCategoryHorizonScoreEntity> {
        return categoryId
            ?.let { horizonScoresByKey[VacancyCategoryKey(propertyId, it)] }
            .orEmpty()
    }

    fun spatialFor(propertyId: String, score: VacancyCategoryScoreEntity?): VacancyCategorySpatialEntity? {
        return score?.let { spatialByKey[it.id] }
    }

    fun categoryName(categoryId: String?): String? {
        return categoryId?.let(categoryNameById::get)
    }
}
