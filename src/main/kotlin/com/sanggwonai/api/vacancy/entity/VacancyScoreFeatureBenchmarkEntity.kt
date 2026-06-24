package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "vacancy_score_feature_benchmarks")
class VacancyScoreFeatureBenchmarkEntity(
    @Id
    @Column(name = "feature_key", nullable = false, length = 80)
    val featureKey: String,

    @Column(name = "feature_label", nullable = false, length = 120)
    val featureLabel: String,

    @Column(name = "average_value", nullable = false, precision = 20, scale = 6)
    val averageValue: BigDecimal,

    @Column(name = "display_unit", nullable = false, length = 24)
    val displayUnit: String,

    @Column(name = "higher_is_positive", nullable = false)
    val higherIsPositive: Boolean,

    @Column(name = "source", nullable = false, length = 80)
    val source: String
)
