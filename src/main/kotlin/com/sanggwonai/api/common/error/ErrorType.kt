package com.sanggwonai.api.common.error

import org.springframework.http.HttpStatus

enum class ErrorType(
    val status: HttpStatus,
    val code: ErrorCode,
    val message: String
) {
    AUTH_REQUIRED(
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.AUTH_REQUIRED,
        message = "인증이 필요해요"
    ),
    INVALID_CREDENTIALS(
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.INVALID_CREDENTIALS,
        message = "이메일 또는 비밀번호가 일치하지 않아요"
    ),
    EMAIL_CONFLICT(
        status = HttpStatus.CONFLICT,
        code = ErrorCode.CONFLICT,
        message = "이미 가입된 이메일이에요"
    ),
    REFRESH_TOKEN_MISSING(
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.AUTH_REQUIRED,
        message = "리프레시 토큰이 없어요"
    ),
    REFRESH_TOKEN_INVALID(
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.AUTH_REQUIRED,
        message = "리프레시 토큰이 유효하지 않아요"
    ),
    REFRESH_TOKEN_EXPIRED(
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.AUTH_REQUIRED,
        message = "리프레시 토큰이 만료되었어요"
    ),
    PASSWORD_ENCODING_FAILED(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        code = ErrorCode.UPSTREAM_UNAVAILABLE,
        message = "비밀번호 암호화에 실패했어요"
    ),
    INVALID_BUSINESS_TYPE(
        status = HttpStatus.UNPROCESSABLE_ENTITY,
        code = ErrorCode.VALIDATION_FAILED,
        message = "유효하지 않은 업종이에요"
    ),
    INVALID_AREA(
        status = HttpStatus.UNPROCESSABLE_ENTITY,
        code = ErrorCode.VALIDATION_FAILED,
        message = "유효하지 않은 지역이에요"
    ),
    VACANCY_NOT_FOUND(
        status = HttpStatus.NOT_FOUND,
        code = ErrorCode.NOT_FOUND,
        message = "공실을 찾을 수 없어요"
    ),
    ANALYSIS_NOT_FOUND(
        status = HttpStatus.NOT_FOUND,
        code = ErrorCode.NOT_FOUND,
        message = "분석을 찾을 수 없어요"
    ),
    API_NOT_FOUND(
        status = HttpStatus.NOT_FOUND,
        code = ErrorCode.NOT_FOUND,
        message = "요청한 API를 찾을 수 없어요"
    ),
    ANALYSIS_FORBIDDEN(
        status = HttpStatus.FORBIDDEN,
        code = ErrorCode.FORBIDDEN,
        message = "접근 권한이 없어요"
    ),
    ANALYSIS_RATE_LIMIT_EXCEEDED(
        status = HttpStatus.TOO_MANY_REQUESTS,
        code = ErrorCode.RATE_LIMITED,
        message = "일일 분석 한도를 초과했어요"
    ),
    QUERY_REQUIRED(
        status = HttpStatus.UNPROCESSABLE_ENTITY,
        code = ErrorCode.VALIDATION_FAILED,
        message = "q 파라미터가 필요해요"
    ),
    VALIDATION_FAILED(
        status = HttpStatus.UNPROCESSABLE_ENTITY,
        code = ErrorCode.VALIDATION_FAILED,
        message = "요청 값이 올바르지 않아요"
    ),
    INTERNAL_SERVER_ERROR(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        code = ErrorCode.UPSTREAM_UNAVAILABLE,
        message = "서버 오류가 발생했어요"
    ),
    SOCIAL_LOGIN_FAILED(
        status = HttpStatus.UNAUTHORIZED,
        code = ErrorCode.AUTH_REQUIRED,
        message = "소셜 로그인에 실패했어요"
    )
}
