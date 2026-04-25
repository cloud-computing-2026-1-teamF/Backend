package com.sanggwonai.api.common.error

import com.fasterxml.jackson.annotation.JsonInclude

data class ErrorEnvelope(
    val error: ErrorBody
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
    val trace_id: String
)
