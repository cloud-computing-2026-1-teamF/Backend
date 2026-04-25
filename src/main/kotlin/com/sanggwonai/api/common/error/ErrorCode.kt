package com.sanggwonai.api.common.error

enum class ErrorCode {
    AUTH_REQUIRED,
    INVALID_CREDENTIALS,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION_FAILED,
    RATE_LIMITED,
    ANALYSIS_FAILED,
    UPSTREAM_UNAVAILABLE
}
