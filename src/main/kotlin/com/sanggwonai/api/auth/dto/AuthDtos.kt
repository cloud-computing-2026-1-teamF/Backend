package com.sanggwonai.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(
    @field:Email(message = "RFC 5322 형식이 아님")
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, message = "8자 이상이어야 합니다")
    val password: String
)

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

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val tier: String,
    val createdAt: Instant
)

data class MeData(
    val user: UserDto
)

data class TokenBundleDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

data class LoginData(
    val user: UserDto,
    val tokens: TokenBundleDto
)

data class RefreshData(
    val accessToken: String,
    val expiresIn: Long
)
