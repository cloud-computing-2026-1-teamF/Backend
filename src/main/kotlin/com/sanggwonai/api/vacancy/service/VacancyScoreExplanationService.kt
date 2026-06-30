package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyScoreExplanationDto
import com.sanggwonai.api.vacancy.dto.toScoreExplanationDto
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureBenchmarkEntity
import com.sanggwonai.api.vacancy.repository.VacancyCategoryScoreExplanationRepository
import com.sanggwonai.api.vacancy.repository.VacancyScoreFeatureValueRepository
import java.math.BigDecimal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VacancyScoreExplanationService(
    private val explanationRepository: VacancyCategoryScoreExplanationRepository,
    private val featureValueRepository: VacancyScoreFeatureValueRepository
) {
    @Transactional(readOnly = true)
    fun resolve(
        propertyId: String,
        categoryId: String?,
        benchmarksByKey: Map<String, VacancyScoreFeatureBenchmarkEntity>,
        vacancy: VacancyEntity,
        common: VacancyCommonFeatureEntity?,
        spatial: VacancyCategorySpatialEntity?,
        scorePercent: BigDecimal?
    ): VacancyScoreExplanationDto? {
        val normalizedCategoryId = categoryId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val explanations = explanationRepository
            .findByIdPropertyIdAndIdCategoryIdAndSourceOrderByIdExplanationToneAscIdFeatureRankAsc(
                propertyId,
                normalizedCategoryId,
                STRENGTH_WEAKNESS_SOURCE
            )
            .ifEmpty {
                explanationRepository.findByIdPropertyIdAndIdCategoryIdOrderByIdFeatureRankAsc(
                    propertyId,
                    normalizedCategoryId
                )
            }
        if (explanations.isEmpty()) return null

        val valuesByKey = featureValueRepository
            .findByIdPropertyIdAndIdCategoryId(propertyId, normalizedCategoryId)
            .associateBy { it.id.featureKey }

        return toScoreExplanationDto(
            entities = explanations,
            benchmarksByKey = benchmarksByKey,
            featureValuesByKey = valuesByKey,
            vacancy = vacancy,
            common = common,
            spatial = spatial,
            scorePercent = scorePercent
        )
    }

    companion object {
        private const val STRENGTH_WEAKNESS_SOURCE = "normalized_shap_2026"
    }
}
