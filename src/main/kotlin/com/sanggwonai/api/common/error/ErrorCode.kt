package com.sanggwonai.api.common.error

enum class ErrorCode(val wire: String) {
    AUTH_REQUIRED("auth_required"),
    INVALID_CREDENTIALS("invalid_credentials"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    CONFLICT("conflict"),
    VALIDATION_FAILED("validation_failed"),
    RATE_LIMITED("rate_limited"),
    ANALYSIS_FAILED("analysis_failed"),
    UPSTREAM_UNAVAILABLE("upstream_unavailable")
}
