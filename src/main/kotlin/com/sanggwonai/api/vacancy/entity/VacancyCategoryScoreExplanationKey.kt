package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class VacancyCategoryScoreExplanationKey(
    @Column(name = "property_id", nullable = false, length = 40)
    var propertyId: String = "",

    @Column(name = "category_id", nullable = false, length = 40)
    var categoryId: String = "",

    @Column(name = "explanation_tone", nullable = false, length = 16)
    var explanationTone: String = "model",

    @Column(name = "feature_rank", nullable = false)
    var featureRank: Short = 0
) : Serializable
