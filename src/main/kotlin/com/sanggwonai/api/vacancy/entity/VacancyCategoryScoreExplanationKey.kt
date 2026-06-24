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

    @Column(name = "contribution_direction", nullable = false, length = 16)
    var contributionDirection: String = "",

    @Column(name = "contribution_rank", nullable = false)
    var contributionRank: Short = 0
) : Serializable
