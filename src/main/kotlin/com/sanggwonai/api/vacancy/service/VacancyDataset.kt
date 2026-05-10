package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.business.repository.BusinessTypeRepository
import com.sanggwonai.api.vacancy.entity.VacancyCategoryKey
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.repository.VacancyCategoryScoreRepository
import com.sanggwonai.api.vacancy.repository.VacancyCategorySpatialRepository
import com.sanggwonai.api.vacancy.repository.VacancyCommonFeatureRepository
import com.sanggwonai.api.vacancy.repository.VacancyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class VacancyDataset(
    private val vacancyRepository: VacancyRepository,
    private val commonFeatureRepository: VacancyCommonFeatureRepository,
    private val categoryScoreRepository: VacancyCategoryScoreRepository,
    private val categorySpatialRepository: VacancyCategorySpatialRepository,
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
        val bestScoresByProperty = scores
            .groupBy { it.id.propertyId }
            .mapValues { (_, propertyScores) ->
                propertyScores.maxWith(compareBy<VacancyCategoryScoreEntity> { it.survivalScore ?: java.math.BigDecimal.ZERO }
                    .thenBy { it.recommendedValue?.toInt() ?: 0 })
            }

        val spatials = categorySpatialRepository.findAll()
        val spatialsByKey = spatials.associateBy { it.id }

        return VacancyDatasetSnapshot(
            vacancies = vacancyRepository.findAllByOrderByIdAsc(),
            commonByProperty = commonFeatureRepository.findAll().associateBy { it.propertyId },
            scoreByKey = scores.associateBy { it.id },
            bestScoreByProperty = bestScoresByProperty,
            spatialByKey = spatialsByKey,
            categoryNameById = businessTypeRepository.findAllByOrderByBusinessKeyAsc()
                .associate { it.businessKey to it.label }
        )
    }
}

data class VacancyDatasetSnapshot(
    val vacancies: List<VacancyEntity>,
    val commonByProperty: Map<String, VacancyCommonFeatureEntity>,
    val scoreByKey: Map<VacancyCategoryKey, VacancyCategoryScoreEntity>,
    val bestScoreByProperty: Map<String, VacancyCategoryScoreEntity>,
    val spatialByKey: Map<VacancyCategoryKey, VacancyCategorySpatialEntity>,
    val categoryNameById: Map<String, String>
) {
    fun scoreFor(propertyId: String, categoryId: String?): VacancyCategoryScoreEntity? {
        return categoryId?.let { scoreByKey[VacancyCategoryKey(propertyId, it)] }
            ?: bestScoreByProperty[propertyId]
    }

    fun spatialFor(propertyId: String, score: VacancyCategoryScoreEntity?): VacancyCategorySpatialEntity? {
        return score?.let { spatialByKey[it.id] }
    }

    fun categoryName(categoryId: String?): String? {
        return categoryId?.let(categoryNameById::get)
    }
}
