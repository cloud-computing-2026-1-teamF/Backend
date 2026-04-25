package com.sanggwonai.api.auth.controller.response

import java.time.Instant

data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val tier: String,
    val createdAt: Instant
)
