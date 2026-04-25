package com.sanggwonai.api.auth.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date

@Component
class JwtTokenProvider(
    private val authProperties: AuthProperties,
    private val clock: Clock
) {
    private val key = Keys.hmacShaKeyFor(authProperties.jwtSecret.toByteArray(StandardCharsets.UTF_8))

    fun issueAccessToken(userId: String): String {
        val now = Instant.now(clock)
        val expiresAt = now.plusSeconds(authProperties.accessTokenExpirySeconds)
        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
    }

    fun parseUserId(token: String): String {
        try {
            val claims: Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
            return claims.subject
        } catch (_: Exception) {
            throw ApiException.of(ErrorType.AUTH_REQUIRED)
        }
    }
}
