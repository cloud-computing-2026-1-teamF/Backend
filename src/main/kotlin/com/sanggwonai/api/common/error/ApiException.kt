package com.sanggwonai.api.common.error

class ApiException private constructor(
    val errorType: ErrorType,
    val details: Map<String, String>? = null
) : RuntimeException(errorType.message) {
    val status = errorType.status
    val code = errorType.code

    companion object {
        fun of(errorType: ErrorType, details: Map<String, String>? = null): ApiException {
            return ApiException(errorType = errorType, details = details)
        }
    }
}
