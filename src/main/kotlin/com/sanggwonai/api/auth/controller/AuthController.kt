package com.sanggwonai.api.auth.controller

import com.sanggwonai.api.auth.dto.LoginRequest
import com.sanggwonai.api.auth.dto.SignupRequest
import com.sanggwonai.api.auth.facade.AuthFacade
import com.sanggwonai.api.common.api.ApiResponse
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorCode
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authFacade: AuthFacade
) {

    @GetMapping("/me")
    fun me(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse(authFacade.me(authorization)))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<*>> {
        val data = authFacade.login(request)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.tokens.refreshToken).toString())
            .body(ApiResponse(data))
    }

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<ApiResponse<*>> {
        val data = authFacade.signup(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.tokens.refreshToken).toString())
            .body(ApiResponse(data))
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = REFRESH_COOKIE, required = false) refreshToken: String?
    ): ResponseEntity<ApiResponse<*>> {
        if (refreshToken.isNullOrBlank()) {
            throw ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "리프레시 토큰이 없어요")
        }
        return ResponseEntity.ok(ApiResponse(authFacade.refresh(refreshToken)))
    }

    private fun buildRefreshCookie(token: String): ResponseCookie {
        return ResponseCookie.from(REFRESH_COOKIE, token)
            .httpOnly(true)
            .secure(false)
            .path("/")
            .sameSite("Lax")
            .maxAge(30L * 24L * 60L * 60L)
            .build()
    }

    companion object {
        private const val REFRESH_COOKIE = "refresh_token"
    }
}
