package com.sanggwonai.api.vacancy.dto

data class VacancySearchCriteria(
    val areaId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val rentMax: Long?,
    val depositMax: Long?,
    val maintenanceFeeMax: Long?
)

