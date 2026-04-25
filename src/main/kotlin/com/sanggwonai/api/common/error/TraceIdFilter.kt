package com.sanggwonai.api.common.error

import com.sanggwonai.api.common.util.IdGenerator
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TraceIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = IdGenerator.next("req")
        request.setAttribute(TRACE_ID_ATTR, traceId)
        response.setHeader("X-Trace-Id", traceId)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val TRACE_ID_ATTR: String = "trace_id"
    }
}
