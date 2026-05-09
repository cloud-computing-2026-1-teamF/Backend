package com.sanggwonai.api.common.logging

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant

object ApiLog {
    fun requestStarted(
        request: HttpServletRequest,
        traceId: String,
        time: Instant
    ): String = block(
        title = "API REQUEST",
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
    ): String = block(
        title = "API RESPONSE",
        level = levelForStatus(response.status),
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.request.finished",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "status" to response.status.toString(),
            "outcome" to outcome(response.status),
            "durationMs" to durationMs.toString(),
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
    ): String = block(
        title = "API ABORTED",
        level = "ERROR",
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.request.aborted",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "status" to response.status.toString(),
            "durationMs" to durationMs.toString(),
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
    ): String = block(
        title = "API ERROR",
        level = levelForStatus(status),
        time = time,
        traceId = traceId,
        fields = listOf(
            "event" to "http.error.handled",
            "method" to request.method,
            "path" to request.requestURI,
            "query" to request.queryString.orEmpty(),
            "status" to status.toString(),
            "code" to code,
            "message" to message,
            "details" to (details?.entries?.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }.orEmpty()),
            "exception" to error.javaClass.name
        )
    )

    private fun block(
        title: String,
        level: String,
        time: Instant,
        traceId: String,
        fields: List<Pair<String, String>>
    ): String {
        val width = 78
        val header = "┌─ $title ${"─".repeat((width - title.length - 4).coerceAtLeast(1))}"
        val body = buildList {
            add("│ level=$level")
            add("│ time=$time")
            add("│ traceId=$traceId")
            fields.forEach { (key, value) ->
                add("│ $key=${value.ifBlank { "-" }}")
            }
        }
        return (listOf(header) + body + "└${"─".repeat(width - 1)}").joinToString("\n")
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
