package com.sanggwonai.api.vacancy.dto

data class MenuPriceEstimateDto(
    val vacancyId: String,
    val menuName: String,
    val recommendedPrice: Long,
    val minPrice: Long,
    val maxPrice: Long,
    val currency: String,
    val confidence: String,
    val positioning: String,
    val signals: List<String>,
    val estimatedLatencyMs: Long,
    val source: String
)
