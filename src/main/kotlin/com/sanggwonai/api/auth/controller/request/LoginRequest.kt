package com.sanggwonai.api.auth.controller.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:Email(message = "RFC 5322 형식이 아님")
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, message = "8자 이상이어야 합니다")
    val password: String
)
