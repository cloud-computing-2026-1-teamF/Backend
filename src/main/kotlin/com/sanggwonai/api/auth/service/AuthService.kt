package com.sanggwonai.api.auth.service

import com.sanggwonai.api.auth.dto.LoginData
import com.sanggwonai.api.auth.dto.MeData
import com.sanggwonai.api.auth.dto.RefreshData
import com.sanggwonai.api.auth.dto.TokenBundleDto
import com.sanggwonai.api.auth.controller.request.LoginRequest
import com.sanggwonai.api.auth.controller.request.SignupRequest
import com.sanggwonai.api.auth.entity.RefreshTokenEntity
import com.sanggwonai.api.auth.entity.UserEntity
import com.sanggwonai.api.auth.entity.UserTier
import com.sanggwonai.api.auth.mapper.UserMapper
import com.sanggwonai.api.auth.repository.RefreshTokenRepository
import com.sanggwonai.api.auth.repository.UserRepository
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.common.util.IdGenerator
import com.sanggwonai.api.auth.entity.AuthProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Clock
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userMapper: UserMapper,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authProperties: AuthProperties,
    private val clock: Clock,
    private val restTemplate: RestTemplate
) {

    @Transactional(readOnly = true)
    fun me(authContext: AuthContext): MeData {
        val user = userRepository.findById(authContext.userId)
            .orElseThrow { ApiException.of(ErrorType.AUTH_REQUIRED) }
        return MeData(user = userMapper.toDto(user))
    }

    @Transactional
    fun login(request: LoginRequest): LoginData {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow {
                ApiException.of(ErrorType.INVALID_CREDENTIALS)
            }
        if (user.oauthProvider != AuthProvider.EMAIL || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException.of(ErrorType.INVALID_CREDENTIALS)
        }
        val tokens = issueTokens(user)
        return LoginData(user = userMapper.toDto(user), tokens = tokens)
    }

    @Transactional
    fun signup(request: SignupRequest): LoginData {
        if (userRepository.existsByEmail(request.email)) {
            throw ApiException.of(ErrorType.EMAIL_CONFLICT)
        }
        val now = Instant.now(clock)
        val user = userRepository.save(
            UserEntity(
                id = IdGenerator.next("usr"),
                email = request.email.lowercase(),
                passwordHash = passwordEncoder.encode(request.password)
                    ?: throw ApiException.of(ErrorType.PASSWORD_ENCODING_FAILED),
                name = request.name,
                tier = UserTier.FREE,
                oauthProvider = AuthProvider.EMAIL,
                createdAt = now
            )
        )
        val tokens = issueTokens(user)
        return LoginData(user = userMapper.toDto(user), tokens = tokens)
    }

    @Transactional
    fun refresh(refreshToken: String): RefreshData {
        val now = Instant.now(clock)
        val tokenEntity = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
            .orElseThrow { ApiException.of(ErrorType.REFRESH_TOKEN_INVALID) }

        if (tokenEntity.expiresAt.isBefore(now)) {
            tokenEntity.revoked = true
            throw ApiException.of(ErrorType.REFRESH_TOKEN_EXPIRED)
        }

        val accessToken = jwtTokenProvider.issueAccessToken(tokenEntity.user.id)
        return RefreshData(
            accessToken = accessToken,
            expiresIn = authProperties.accessTokenExpirySeconds
        )
    }

    @Transactional
    fun kakaoLogin(code: String): LoginData {
        val tokenResponse = restTemplate.postForObject(
            "https://kauth.kakao.com/oauth/token",
            org.springframework.http.HttpEntity(
                "grant_type=authorization_code&client_id=${authProperties.kakaoClientId}&redirect_uri=${authProperties.kakaoRedirectUri}&code=$code",
                org.springframework.http.HttpHeaders().apply {
                    contentType = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
                }
            ),
            Map::class.java
        ) ?: throw ApiException.of(ErrorType.SOCIAL_LOGIN_FAILED)

        val kakaoAccessToken = tokenResponse["access_token"] as String

        val userResponse = restTemplate.exchange(
            "https://kapi.kakao.com/v2/user/me",
            org.springframework.http.HttpMethod.GET,
            org.springframework.http.HttpEntity<Void>(
                org.springframework.http.HttpHeaders().apply {
                    setBearerAuth(kakaoAccessToken)
                }
            ),
            Map::class.java
        ).body ?: throw ApiException.of(ErrorType.SOCIAL_LOGIN_FAILED)

        val kakaoId = userResponse["id"].toString()
        @Suppress("UNCHECKED_CAST")
        val kakaoAccount = userResponse["kakao_account"] as? Map<String, Any>
        val email = kakaoAccount?.get("email") as? String
        val nickname = ((kakaoAccount?.get("profile") as? Map<String, Any>)?.get("nickname") as? String) ?: "카카오유저"

        val now = Instant.now(clock)
        val user = userRepository.findByKakaoId(kakaoId).orElseGet {
            userRepository.save(
                UserEntity(
                    id = IdGenerator.next("usr"),
                    email = email ?: "$kakaoId@kakao.local",
                    passwordHash = null,
                    name = nickname,
                    tier = UserTier.FREE,
                    oauthProvider = AuthProvider.KAKAO,
                    kakaoId = kakaoId,
                    createdAt = now
                )
            )
        }
        val tokens = issueTokens(user)
        return LoginData(user = userMapper.toDto(user), tokens = tokens)
    }

    private fun issueTokens(user: UserEntity): TokenBundleDto {
        val now = Instant.now(clock)
        val refreshToken = IdGenerator.next("rt")
        refreshTokenRepository.findAllByUserAndRevokedFalse(user).forEach { it.revoked = true }
        refreshTokenRepository.save(
            RefreshTokenEntity(
                token = refreshToken,
                user = user,
                expiresAt = now.plusSeconds(authProperties.refreshTokenExpiryDays * 24 * 60 * 60),
                revoked = false,
                createdAt = now
            )
        )
        return TokenBundleDto(
            accessToken = jwtTokenProvider.issueAccessToken(user.id),
            refreshToken = refreshToken,
            expiresIn = authProperties.accessTokenExpirySeconds
        )
    }
}
