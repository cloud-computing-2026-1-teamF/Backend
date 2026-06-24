package com.sanggwonai.api.vacancy.controller.response

import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter

data class VacancyPromptParseResponse(
    val filters: VacancyStructuredFilter,
    val source: String,
    val schemaVersion: String
)

data class VacancyPromptSearchResponse(
    val filters: VacancyStructuredFilter,
    val source: String,
    val schemaVersion: String,
    val result: VacancySearchResponse
)

data class VacancyPromptSchemaResponse(
    val schemaVersion: String,
    val jsonSchema: Map<String, Any?>,
    val openAiTextFormat: Map<String, Any?>,
    val enums: Map<String, Any>,
    val example: VacancyStructuredFilter
)

data class MenuPriceEstimateResponse(
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
