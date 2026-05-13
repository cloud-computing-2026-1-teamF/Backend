package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import java.math.BigDecimal

internal fun VacancyEntity.deduplicationKey(): String {
    return deduplicationKey(
        id = id,
        buildingName = buildingName,
        roadAddress = roadAddress,
        lotAddress = lotAddress,
        detailAddress = detailAddress,
        transactionType = transactionType,
        dedicatedArea = dedicatedArea,
        floor = floor
    )
}

internal fun VacancyDto.deduplicationKey(): String {
    return deduplicationKey(
        id = id,
        buildingName = buildingName,
        roadAddress = roadAddress,
        lotAddress = lotAddress,
        detailAddress = detailAddress,
        transactionType = transactionType,
        dedicatedArea = dedicatedArea,
        floor = floor
    )
}

private fun deduplicationKey(
    id: String,
    buildingName: String?,
    roadAddress: String?,
    lotAddress: String?,
    detailAddress: String?,
    transactionType: String?,
    dedicatedArea: BigDecimal?,
    floor: String?
): String {
    val building = normalize(buildingName)
    val road = normalize(roadAddress)
    val lot = normalize(lotAddress)
    val floorValue = normalize(floor)
    val transaction = normalize(transactionType)
    val area = normalize(dedicatedArea)

    if (building.isNotEmpty() && (road.isNotEmpty() || lot.isNotEmpty())) {
        return listOf(
            "building",
            building,
            road,
            lot,
            transaction,
            floorValue,
            area
        ).joinToString("|")
    }

    if ((road.isNotEmpty() || lot.isNotEmpty()) && area.isNotEmpty()) {
        return listOf(
            "address-area",
            road,
            lot,
            transaction,
            floorValue,
            area
        ).joinToString("|")
    }

    val detail = normalize(detailAddress)
    val addressParts = listOf(road, lot, detail, floorValue)
    if (addressParts.all { it.isEmpty() }) return "id:$id"
    return listOf(
        "vacancy",
        road,
        lot,
        detail,
        transaction,
        floorValue,
        area
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
