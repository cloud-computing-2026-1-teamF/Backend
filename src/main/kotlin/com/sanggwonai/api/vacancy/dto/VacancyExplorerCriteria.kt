package com.sanggwonai.api.vacancy.dto

import java.math.BigDecimal

data class VacancyExplorerCriteria(
    val areaId: String?,
    val categoryId: String?,
    val transactionType: String?,
    val q: String?,
    val latitude: Double?,
    val longitude: Double?,
    val radiusM: Int?,
    val rentMax: Long?,
    val depositMax: Long?,
    val maintenanceFeeMax: Long?,
    val premiumMax: Long?,
    val salePriceMax: Long?,
    val scoreMin: BigDecimal?,
    val areaMin: BigDecimal?,
    val areaMax: BigDecimal?,
    val page: Int,
    val size: Int,
    val sort: VacancyExplorerSort
)

enum class VacancyExplorerSort(
    val wireValue: String
) {
    ScoreDesc(wireValue = "score_desc"),
    RentAsc(wireValue = "rent_asc"),
    RentDesc(wireValue = "rent_desc"),
    DepositAsc(wireValue = "deposit_asc"),
    AreaDesc(wireValue = "area_desc"),
    UpdatedDesc(wireValue = "updated_desc");

    companion object {
        fun from(value: String?): VacancyExplorerSort {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ScoreDesc
        }
    }
}
