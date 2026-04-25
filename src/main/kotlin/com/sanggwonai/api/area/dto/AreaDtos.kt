package com.sanggwonai.api.area.dto

data class AreaSearchResponseDto(
    val id: String,
    val name: String,
    val region: String,
    val fullName: String,
    val center: CenterDto
)

data class CenterDto(
    val lat: Double,
    val lng: Double
)
