package com.sanggwonai.api.auth.dto

data class TokenBundleDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)
