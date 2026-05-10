package com.sanggwonai.api.vacancy.dto

import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import java.math.BigDecimal

data class RankedVacancy(
    val vacancy: VacancyEntity,
    val common: VacancyCommonFeatureEntity?,
    val spatial: VacancyCategorySpatialEntity?,
    val categoryId: String?,
    val categoryName: String?,
    val rank: Int,
    val score: BigDecimal,
    val distanceM: Int
)
