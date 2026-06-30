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

    @Column(name = "source", nullable = false, length = 80)
    val source: String,

    @Column(name = "contribution_log_odds", precision = 20, scale = 6)
    val contributionLogOdds: BigDecimal?,

    @Column(name = "contribution_pp", precision = 20, scale = 6)
    val contributionPp: BigDecimal?,

    @Column(name = "percentile_label", length = 40)
    val percentileLabel: String?
)
