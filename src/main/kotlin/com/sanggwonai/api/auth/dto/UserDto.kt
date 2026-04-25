package com.sanggwonai.api.auth.dto

import java.time.Instant

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val tier: String,
    val createdAt: Instant
)
