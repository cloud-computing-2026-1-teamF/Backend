package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import java.math.BigDecimal

internal fun VacancyEntity.deduplicationKey(): String {
    val addressParts = listOf(
        normalize(roadAddress),
        normalize(lotAddress),
        normalize(detailAddress),
        normalize(floor)
    )
    if (addressParts.all { it.isEmpty() }) return "id:$id"

    return listOf(
        "vacancy",
        *addressParts.toTypedArray(),
        normalize(transactionType),
        normalize(dedicatedArea),
        normalize(supplyArea),
        normalize(deposit),
        normalize(monthlyRent),
        normalize(maintenanceFee),
        normalize(premium),
        normalize(salePrice)
    ).joinToString("|")
}

internal fun VacancyDto.deduplicationKey(): String {
    val addressParts = listOf(
        normalize(roadAddress),
        normalize(lotAddress),
        normalize(detailAddress),
        normalize(floor)
    )
    if (addressParts.all { it.isEmpty() }) return "id:$id"

    return listOf(
        "vacancy",
        *addressParts.toTypedArray(),
        normalize(transactionType),
        normalize(dedicatedArea),
        normalize(supplyArea),
        normalize(deposit),
        normalize(monthlyRent),
        normalize(maintenanceFee),
        normalize(premium),
        normalize(salePrice)
    ).joinToString("|")
}

private fun normalize(value: String?): String {
    return value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase()
        .orEmpty()
}

private fun normalize(value: BigDecimal?): String {
    return value?.stripTrailingZeros()?.toPlainString().orEmpty()
}

private fun normalize(value: Long?): String {
    return value?.toString().orEmpty()
}
