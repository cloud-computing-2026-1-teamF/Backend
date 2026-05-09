package com.sanggwonai.api.vacancy.dto

import com.sanggwonai.api.vacancy.entity.VacancyEntity
import java.math.BigDecimal

data class RankedVacancy(
    val vacancy: VacancyEntity,
    val rank: Int,
    val score: BigDecimal,
    val distanceM: Int
)

