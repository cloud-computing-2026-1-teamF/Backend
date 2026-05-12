package com.sanggwonai.api.analysis.repository

import com.sanggwonai.api.analysis.entity.AnalysisVacancyRecommendationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisVacancyRecommendationRepository : JpaRepository<AnalysisVacancyRecommendationEntity, String> {
    fun findByAnalysisIdOrderByRankAsc(analysisId: String): List<AnalysisVacancyRecommendationEntity>
    fun findByAnalysisIdIn(analysisIds: Collection<String>): List<AnalysisVacancyRecommendationEntity>
}

