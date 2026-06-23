package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class VacancyCategoryHorizonScoreKey(
    @Column(name = "property_id", nullable = false, length = 40)
    var propertyId: String = "",

    @Column(name = "category_id", nullable = false, length = 40)
    var categoryId: String = "",

    @Column(name = "horizon_years", nullable = false)
    var horizonYears: Int = 0
) : Serializable
