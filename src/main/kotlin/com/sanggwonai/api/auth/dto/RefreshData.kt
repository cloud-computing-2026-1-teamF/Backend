package com.sanggwonai.api.auth.dto

data class RefreshData(
    val accessToken: String,
    val expiresIn: Long
)
