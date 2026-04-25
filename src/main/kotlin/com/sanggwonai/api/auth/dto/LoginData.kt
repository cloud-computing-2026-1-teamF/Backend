package com.sanggwonai.api.auth.dto

data class LoginData(
    val user: UserDto,
    val tokens: TokenBundleDto
)
