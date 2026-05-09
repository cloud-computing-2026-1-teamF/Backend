package com.sanggwonai.api.analysis.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "analysis_vacancy_recommendations")
class AnalysisVacancyRecommendationEntity(
    @Id
    @Column(nullable = false, length = 40)
    val id: String,

    @Column(name = "analysis_id", nullable = false, length = 40)
    val analysisId: String,

    @Column(name = "vacancy_id", nullable = false, length = 40)
    val vacancyId: String,

    @Column(name = "rank_order", nullable = false)
    val rank: Int,

    @Column(nullable = false, precision = 5, scale = 2)
    val score: BigDecimal,

    @Column(name = "distance_m", nullable = false)
    val distanceM: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)

