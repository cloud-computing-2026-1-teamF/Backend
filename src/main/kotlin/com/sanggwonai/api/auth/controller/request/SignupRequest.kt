package com.sanggwonai.api.auth.controller.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email(message = "RFC 5322 형식이 아님")
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, message = "8자 이상이어야 합니다")
    @field:Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "영문+숫자 조합이어야 합니다")
    val password: String,

    @field:NotBlank
    @field:Size(min = 2, max = 16, message = "2~16자여야 합니다")
    val name: String
)
