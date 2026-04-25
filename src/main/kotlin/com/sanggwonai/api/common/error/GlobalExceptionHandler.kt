package com.sanggwonai.api.common.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorEnvelope> {
        return ResponseEntity
            .status(ex.status)
            .body(
                ErrorEnvelope(
                    ErrorBody(
                        code = ex.code.toWireCode(),
                        message = ex.errorType.message,
                        details = ex.details,
                        trace_id = request.traceId()
                    )
                )
            )
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        BindException::class,
        ConstraintViolationException::class,
        MissingServletRequestParameterException::class,
        IllegalArgumentException::class
    )
    fun handleValidation(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorEnvelope> {
        val details = linkedMapOf<String, String>()
        when (ex) {
            is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.forEach { details[it.field] = it.defaultMessage ?: "invalid" }
            is BindException -> ex.bindingResult.fieldErrors.forEach { details[it.field] = it.defaultMessage ?: "invalid" }
            is ConstraintViolationException -> ex.constraintViolations.forEach {
                val key = it.propertyPath.lastOrNull()?.name ?: "value"
                details[key] = it.message
            }
            is MissingServletRequestParameterException -> details[ex.parameterName] = "required parameter"
            is IllegalArgumentException -> details["request"] = ex.message ?: "invalid request"
        }
        return ResponseEntity
            .status(ErrorType.VALIDATION_FAILED.status)
            .body(
                ErrorEnvelope(
                    ErrorBody(
                        code = ErrorType.VALIDATION_FAILED.code.toWireCode(),
                        message = ErrorType.VALIDATION_FAILED.message,
                        details = details.ifEmpty { null },
                        trace_id = request.traceId()
                    )
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorEnvelope> {
        return ResponseEntity
            .status(ErrorType.INTERNAL_SERVER_ERROR.status)
            .body(
                ErrorEnvelope(
                    ErrorBody(
                        code = ErrorType.INTERNAL_SERVER_ERROR.code.toWireCode(),
                        message = ErrorType.INTERNAL_SERVER_ERROR.message,
                        trace_id = request.traceId()
                    )
                )
            )
    }

    private fun HttpServletRequest.traceId(): String {
        return this.getAttribute(TraceIdFilter.TRACE_ID_ATTR)?.toString() ?: "req_unknown"
    }

    private fun ErrorCode.toWireCode(): String = this.wire
}
