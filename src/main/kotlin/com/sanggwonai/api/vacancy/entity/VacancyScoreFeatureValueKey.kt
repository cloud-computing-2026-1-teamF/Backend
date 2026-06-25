package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class VacancyScoreFeatureValueKey(
    @Column(name = "property_id", nullable = false, length = 40)
    var propertyId: String = "",

    @Column(name = "category_id", nullable = false, length = 40)
    var categoryId: String = "",

    @Column(name = "feature_key", nullable = false, length = 80)
    var featureKey: String = ""
) : Serializable
