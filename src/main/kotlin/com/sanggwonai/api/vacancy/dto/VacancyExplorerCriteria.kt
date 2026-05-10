package com.sanggwonai.api.vacancy.dto

import org.springframework.data.domain.Sort
import java.math.BigDecimal

data class VacancyExplorerCriteria(
    val areaId: String?,
    val q: String?,
    val rentMax: Long?,
    val depositMax: Long?,
    val maintenanceFeeMax: Long?,
    val scoreMin: BigDecimal?,
    val areaMin: BigDecimal?,
    val areaMax: BigDecimal?,
    val page: Int,
    val size: Int,
    val sort: VacancyExplorerSort
)

enum class VacancyExplorerSort(
    val wireValue: String,
    private val orders: List<Sort.Order>
) {
    ScoreDesc(
        wireValue = "score_desc",
        orders = listOf(
            Sort.Order.desc("survivalScore").nullsLast(),
            Sort.Order.asc("id")
        )
    ),
    RentAsc(
        wireValue = "rent_asc",
        orders = listOf(
            Sort.Order.asc("monthlyRent").nullsLast(),
            Sort.Order.desc("survivalScore").nullsLast(),
            Sort.Order.asc("id")
        )
    ),
    RentDesc(
        wireValue = "rent_desc",
        orders = listOf(
            Sort.Order.desc("monthlyRent").nullsLast(),
            Sort.Order.desc("survivalScore").nullsLast(),
            Sort.Order.asc("id")
        )
    ),
    DepositAsc(
        wireValue = "deposit_asc",
        orders = listOf(
            Sort.Order.asc("deposit").nullsLast(),
            Sort.Order.desc("survivalScore").nullsLast(),
            Sort.Order.asc("id")
        )
    ),
    AreaDesc(
        wireValue = "area_desc",
        orders = listOf(
            Sort.Order.desc("locationArea").nullsLast(),
            Sort.Order.desc("survivalScore").nullsLast(),
            Sort.Order.asc("id")
        )
    ),
    UpdatedDesc(
        wireValue = "updated_desc",
        orders = listOf(
            Sort.Order.desc("updatedAt"),
            Sort.Order.asc("id")
        )
    );

    fun toSort(): Sort = Sort.by(orders)

    companion object {
        fun from(value: String?): VacancyExplorerSort {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ScoreDesc
        }
    }
}

