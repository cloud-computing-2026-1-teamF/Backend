package com.sanggwonai.api.auth.controller

import com.sanggwonai.api.auth.controller.request.LoginRequest
import com.sanggwonai.api.auth.controller.request.SignupRequest
import com.sanggwonai.api.auth.controller.response.LoginResponse
import com.sanggwonai.api.auth.controller.response.MeResponse
import com.sanggwonai.api.auth.controller.response.RefreshResponse
import com.sanggwonai.api.auth.controller.response.TokensResponse
import com.sanggwonai.api.auth.controller.response.UserResponse
import com.sanggwonai.api.auth.dto.LoginData
import com.sanggwonai.api.auth.dto.MeData
import com.sanggwonai.api.auth.dto.RefreshData
import com.sanggwonai.api.auth.dto.TokenBundleDto
import com.sanggwonai.api.auth.dto.UserDto
import com.sanggwonai.api.auth.facade.AuthFacade
import com.sanggwonai.api.common.api.ApiResponse
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "인증", description = "회원 인증/인가 관련 API 모음임")
class AuthController(
    private val authFacade: AuthFacade
) {

    @GetMapping("/me")
    @Operation(
        summary = "내 정보 조회함",
        description = "Access 토큰 기준으로 현재 로그인 사용자 정보를 조회함.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    fun me(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?
    ): ResponseEntity<ApiResponse<MeResponse>> {
        val data = authFacade.me(authorization)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @PostMapping("/login")
    @Operation(
        summary = "로그인 수행함",
        description = "이메일/비밀번호로 로그인하고 사용자 정보와 토큰 묶음을 반환함."
    )
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val data = authFacade.login(request)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.tokens.refreshToken).toString())
            .body(ApiResponse(toResponse(data)))
    }

    @PostMapping("/signup")
    @Operation(
        summary = "회원가입 수행함",
        description = "신규 계정을 생성하고 즉시 로그인 처리된 토큰을 반환함."
    )
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val data = authFacade.signup(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.tokens.refreshToken).toString())
            .body(ApiResponse(toResponse(data)))
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Access 토큰 재발급함",
        description = "HttpOnly 쿠키의 refresh token으로 access token을 재발급함."
    )
    fun refresh(
        @CookieValue(name = REFRESH_COOKIE, required = false) refreshToken: String?
    ): ResponseEntity<ApiResponse<RefreshResponse>> {
        if (refreshToken.isNullOrBlank()) {
            throw ApiException.of(ErrorType.REFRESH_TOKEN_MISSING)
        }
        return ResponseEntity.ok(ApiResponse(toResponse(authFacade.refresh(refreshToken))))
    }

    @PostMapping("/kakao")
    @Operation(
        summary = "카카오 로그인 수행함",
        description = "카카오 인가 코드로 로그인하고 사용자 정보와 토큰 묶음을 반환함."
    )
    fun kakaoLogin(@RequestBody body: Map<String, String>): ResponseEntity<ApiResponse<LoginResponse>> {
        val code = body["code"] ?: throw ApiException.of(ErrorType.VALIDATION_FAILED)
        val data = authFacade.kakaoLogin(code)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.tokens.refreshToken).toString())
            .body(ApiResponse(toResponse(data)))
    }

    private fun toResponse(data: MeData): MeResponse = MeResponse(user = toResponse(data.user))

    private fun toResponse(data: LoginData): LoginResponse {
        return LoginResponse(
            user = toResponse(data.user),
            tokens = toResponse(data.tokens)
        )
    }

    private fun toResponse(data: RefreshData): RefreshResponse {
        return RefreshResponse(
            accessToken = data.accessToken,
            expiresIn = data.expiresIn
        )
    }

    private fun toResponse(data: UserDto): UserResponse {
        return UserResponse(
            id = data.id,
            email = data.email,
            name = data.name,
            tier = data.tier,
            createdAt = data.createdAt
        )
    }

    private fun toResponse(data: TokenBundleDto): TokensResponse {
        return TokensResponse(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken,
            expiresIn = data.expiresIn
        )
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
