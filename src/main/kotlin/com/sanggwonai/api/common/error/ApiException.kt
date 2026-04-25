package com.sanggwonai.api.common.error

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: ErrorCode,
    override val message: String,
    val details: Map<String, String>? = null
) : RuntimeException(message)
