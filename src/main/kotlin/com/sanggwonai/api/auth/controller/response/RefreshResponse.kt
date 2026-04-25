package com.sanggwonai.api.auth.controller.response

data class RefreshResponse(
    val accessToken: String,
    val expiresIn: Long
)
