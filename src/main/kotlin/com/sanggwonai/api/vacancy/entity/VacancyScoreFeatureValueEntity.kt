package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "vacancy_score_feature_values")
class VacancyScoreFeatureValueEntity(
    @EmbeddedId
    val id: VacancyScoreFeatureValueKey,

    @Column(name = "current_value", precision = 20, scale = 6)
    val currentValue: BigDecimal?,

    @Column(name = "average_value", precision = 20, scale = 6)
    val averageValue: BigDecimal?,

    @Column(name = "value_percentile", precision = 20, scale = 6)
    val valuePercentile: BigDecimal?,

    @Column(name = "value_percentile_label", length = 40)
    val valuePercentileLabel: String?,

    @Column(name = "raw_feature_name", length = 160)
    val rawFeatureName: String?,

    @Column(name = "source", nullable = false, length = 80)
    val source: String
)
