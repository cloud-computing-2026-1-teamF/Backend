package com.sanggwonai.api.auth.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val jwtSecret: String,
    val accessTokenExpirySeconds: Long,
    val refreshTokenExpiryDays: Long
)
