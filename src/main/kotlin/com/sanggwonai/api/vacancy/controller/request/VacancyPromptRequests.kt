package com.sanggwonai.api.vacancy.controller.request

import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter

data class VacancyStructuredSearchRequest(
    val filters: VacancyStructuredFilter = VacancyStructuredFilter()
)

data class VacancyPromptParseRequest(
    val prompt: String
)

data class VacancyPromptSearchRequest(
    val prompt: String,
    val page: Int? = null,
    val size: Int? = null
)
