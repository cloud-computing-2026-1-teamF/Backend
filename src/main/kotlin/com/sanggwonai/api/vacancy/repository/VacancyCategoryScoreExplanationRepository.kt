package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreExplanationEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreExplanationKey
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyCategoryScoreExplanationRepository :
    JpaRepository<VacancyCategoryScoreExplanationEntity, VacancyCategoryScoreExplanationKey> {
    fun findByIdPropertyIdAndIdCategoryIdOrderByIdFeatureRankAsc(
        propertyId: String,
        categoryId: String
    ): List<VacancyCategoryScoreExplanationEntity>

    fun findByIdPropertyIdAndIdCategoryIdAndSourceOrderByIdExplanationToneAscIdFeatureRankAsc(
        propertyId: String,
        categoryId: String,
        source: String
    ): List<VacancyCategoryScoreExplanationEntity>
}
