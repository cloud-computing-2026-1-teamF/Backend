package com.sanggwonai.api.common.logging

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant

object ApiLog {
    fun requestStarted(
        request: HttpServletRequest,
        traceId: String,
        time: Instant
    ): String = json(
        type = "api_request",
        level = "INFO",
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.request.started",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "clientIp" to request.clientIp(),
            "userAgent" to request.userAgent(),
            "contentType" to request.contentType.orEmpty()
        )
    )

    fun requestFinished(
        request: HttpServletRequest,
        response: HttpServletResponse,
        traceId: String,
        durationMs: Long,
        time: Instant
    ): String = json(
        type = "api_response",
        level = levelForStatus(response.status),
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.request.finished",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "status" to response.status,
            "outcome" to outcome(response.status),
            "durationMs" to durationMs,
            "clientIp" to request.clientIp()
        )
    )

    fun requestAborted(
        request: HttpServletRequest,
        response: HttpServletResponse,
        traceId: String,
        durationMs: Long,
        time: Instant,
        error: Throwable
    ): String = json(
        type = "api_aborted",
        level = "ERROR",
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.request.aborted",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "status" to response.status,
            "durationMs" to durationMs,
            "exception" to error.javaClass.name,
            "message" to error.message.orEmpty()
        )
    )

    fun apiError(
        request: HttpServletRequest,
        traceId: String,
        status: Int,
        code: String,
        message: String,
        details: Map<String, String>?,
        error: Throwable,
        time: Instant
    ): String = json(
        type = "api_error",
        level = levelForStatus(status),
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.error.handled",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "status" to status,
            "code" to code,
            "message" to message,
            "details" to details,
            "exception" to error.javaClass.name
        )
    )

    private fun json(
        type: String,
        level: String,
        time: Instant,
        traceId: String,
        fields: List<Pair<String, Any?>>
    ): String {
        val orderedFields = listOf(
            "type" to type,
            "level" to level,
            "time" to time.toString(),
            "traceId" to traceId
        ) + fields

        return buildString {
            appendLine("{")
            orderedFields.forEachIndexed { index, (key, value) ->
                append("  ")
                append(quote(key))
                append(": ")
                append(jsonValue(value))
                if (index < orderedFields.lastIndex) {
                    append(',')
                }
                appendLine()
            }
            append("}")
        }
    }

    private fun jsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> mapValue(value)
            else -> quote(value.toString().ifBlank { "-" })
        }
    }

    private fun mapValue(value: Map<*, *>): String {
        if (value.isEmpty()) {
            return "{}"
        }
        return value.entries.joinToString(prefix = "{ ", postfix = " }") { (key, nested) ->
            "${quote(key.toString())}: ${jsonValue(nested)}"
        }
    }

    private fun quote(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun levelForStatus(status: Int): String = when {
        status >= 500 -> "ERROR"
        status >= 400 -> "WARN"
        else -> "INFO"
    }

    private fun outcome(status: Int): String = when {
        status >= 500 -> "SERVER_ERROR"
        status >= 400 -> "CLIENT_ERROR"
        status >= 300 -> "REDIRECTION"
        else -> "SUCCESS"
    }

    private fun HttpServletRequest.clientIp(): String {
        return getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: remoteAddr.orEmpty()
    }

    private fun HttpServletRequest.userAgent(): String = getHeader("User-Agent").orEmpty()
}
