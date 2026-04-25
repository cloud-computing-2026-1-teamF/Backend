package com.sanggwonai.api.auth.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class AuthContextResolver(
    private val jwtTokenProvider: JwtTokenProvider
) {

    fun resolveOrThrow(authorizationHeader: String?): AuthContext {
        val token = extractToken(authorizationHeader)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "인증이 필요해요")
        return AuthContext(userId = jwtTokenProvider.parseUserId(token))
    }

    private fun extractToken(header: String?): String? {
        if (header.isNullOrBlank()) {
            return null
        }
        if (!header.startsWith("Bearer ")) {
            return null
        }
        return header.substringAfter("Bearer ").trim().ifBlank { null }
    }
}
