package com.sanggwonai.api.auth.controller.response

data class TokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)
