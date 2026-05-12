package com.sanggwonai.api.vacancy.dto

data class VacancySearchCriteria(
    val areaId: String,
    val categoryId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val transactionType: String?,
    val rentMax: Long?,
    val depositMax: Long?,
    val maintenanceFeeMax: Long?,
    val premiumMax: Long?,
    val salePriceMax: Long?
)
