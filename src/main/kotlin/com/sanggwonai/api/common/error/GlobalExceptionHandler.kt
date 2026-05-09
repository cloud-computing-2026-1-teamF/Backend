package com.sanggwonai.api.common.error

import com.sanggwonai.api.common.logging.ApiLog
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorEnvelope> {
        log.warn(
            ApiLog.apiError(
                request = request,
                traceId = request.traceId(),
                status = ex.status.value(),
                code = ex.code.toWireCode(),
                message = ex.errorType.message,
                details = ex.details,
                error = ex,
                time = java.time.Instant.now()
            ),
            ex
        )
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
        log.warn(
            ApiLog.apiError(
                request = request,
                traceId = request.traceId(),
                status = ErrorType.VALIDATION_FAILED.status.value(),
                code = ErrorType.VALIDATION_FAILED.code.toWireCode(),
                message = ErrorType.VALIDATION_FAILED.message,
                details = details.ifEmpty { null },
                error = ex,
                time = java.time.Instant.now()
            ),
            ex
        )
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
        log.error(
            ApiLog.apiError(
                request = request,
                traceId = request.traceId(),
                status = ErrorType.INTERNAL_SERVER_ERROR.status.value(),
                code = ErrorType.INTERNAL_SERVER_ERROR.code.toWireCode(),
                message = ErrorType.INTERNAL_SERVER_ERROR.message,
                details = null,
                error = ex,
                time = java.time.Instant.now()
            ),
            ex
        )
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
