package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "vacancy_category_score_explanations")
class VacancyCategoryScoreExplanationEntity(
    @EmbeddedId
    val id: VacancyCategoryScoreExplanationKey,

    @Column(name = "feature_key", nullable = false, length = 80)
    val featureKey: String,

    @Column(name = "source", nullable = false, length = 80)
    val source: String
)
