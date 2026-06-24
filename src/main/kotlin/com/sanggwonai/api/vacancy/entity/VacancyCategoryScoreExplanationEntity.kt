package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "vacancy_category_score_explanations")
class VacancyCategoryScoreExplanationEntity(
    @EmbeddedId
    val id: VacancyCategoryScoreExplanationKey,

    @Column(name = "feature_key", nullable = false, length = 80)
    val featureKey: String,

    @Column(name = "feature_label", nullable = false, length = 120)
    val featureLabel: String,

    @Column(name = "feature_display_value", length = 160)
    val featureDisplayValue: String?,

    @Column(name = "impact_value", nullable = false, precision = 12, scale = 6)
    val impactValue: BigDecimal,

    @Column(name = "impact_percent", nullable = false, precision = 6, scale = 2)
    val impactPercent: BigDecimal,

    @Column(name = "source", nullable = false, length = 80)
    val source: String
)
