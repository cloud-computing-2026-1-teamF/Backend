package com.sanggwonai.api.auth.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import org.springframework.stereotype.Component

@Component
class AuthContextResolver(
    private val jwtTokenProvider: JwtTokenProvider
) {

    fun resolveOrThrow(authorizationHeader: String?): AuthContext {
        val token = extractToken(authorizationHeader)
            ?: throw ApiException.of(ErrorType.AUTH_REQUIRED)
        return AuthContext(userId = jwtTokenProvider.parseUserId(token))
    }

    fun resolveOrNull(authorizationHeader: String?): AuthContext? {
        return try {
            resolveOrThrow(authorizationHeader)
        } catch (_: ApiException) {
            null
        }
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
