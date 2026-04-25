package com.sanggwonai.api.auth.controller.response

data class LoginResponse(
    val user: UserResponse,
    val tokens: TokensResponse
)
