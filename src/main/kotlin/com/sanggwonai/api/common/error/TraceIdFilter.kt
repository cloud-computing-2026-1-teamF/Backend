package com.sanggwonai.api.common.error

import com.sanggwonai.api.common.util.IdGenerator
import com.sanggwonai.api.common.logging.ApiLog
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class TraceIdFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(TraceIdFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader(TRACE_ID_HEADER)
            ?.takeIf { it.matches(TRACE_ID_PATTERN) }
            ?: IdGenerator.next("req")
        val startedAt = System.nanoTime()
        request.setAttribute(TRACE_ID_ATTR, traceId)
        response.setHeader("X-Trace-Id", traceId)
        MDC.put("traceId", traceId)

        log.info(ApiLog.requestStarted(request = request, traceId = traceId, time = Instant.now()))
        try {
            filterChain.doFilter(request, response)
        } catch (ex: Exception) {
            log.error(
                ApiLog.requestAborted(
                    request = request,
                    response = response,
                    traceId = traceId,
                    durationMs = elapsedMs(startedAt),
                    time = Instant.now(),
                    error = ex
                ),
                ex
            )
            throw ex
        } finally {
            logByStatus(response.status)(
                ApiLog.requestFinished(
                    request = request,
                    response = response,
                    traceId = traceId,
                    durationMs = elapsedMs(startedAt),
                    time = Instant.now()
                )
            )
            MDC.clear()
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private fun logByStatus(status: Int): (String) -> Unit {
        return when {
            status >= 500 -> log::error
            status >= 400 -> log::warn
            else -> log::info
        }
    }

    companion object {
        const val TRACE_ID_ATTR: String = "trace_id"
        const val TRACE_ID_HEADER: String = "X-Trace-Id"
        private val TRACE_ID_PATTERN = Regex("^[A-Za-z0-9_.:-]{6,120}$")
    }
}
