package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.math.RoundingMode

@Entity
@Table(name = "vacancy_category_scores")
class VacancyCategoryScoreEntity(
    @EmbeddedId
    val id: VacancyCategoryKey,

    @Column(name = "생존점수", precision = 8, scale = 6)
    val survivalScore: BigDecimal?,

    @Column(name = "추천여부")
    val recommendedValue: Short?
) {
    @get:Transient
    val recommended: Boolean?
        get() = recommendedValue?.let { it.toInt() != 0 }

    fun scorePercent(): BigDecimal {
        return (survivalScore ?: BigDecimal.ZERO)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
    }
}
