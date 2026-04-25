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
import com.sanggwonai.api.common.error.ErrorCode
import com.sanggwonai.api.common.util.IdGenerator
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val clock: Clock
) {

    @Transactional(readOnly = true)
    fun me(authContext: AuthContext): MeData {
        val user = userRepository.findById(authContext.userId)
            .orElseThrow { ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "인증이 필요해요") }
        return MeData(user = userMapper.toDto(user))
    }

    @Transactional
    fun login(request: LoginRequest): LoginData {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow {
                ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 일치하지 않아요")
            }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 일치하지 않아요")
        }
        val tokens = issueTokens(user)
        return LoginData(user = userMapper.toDto(user), tokens = tokens)
    }

    @Transactional
    fun signup(request: SignupRequest): LoginData {
        if (userRepository.existsByEmail(request.email)) {
            throw ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "이미 가입된 이메일이에요")
        }
        val now = Instant.now(clock)
        val user = userRepository.save(
            UserEntity(
                id = IdGenerator.next("usr"),
                email = request.email.lowercase(),
                passwordHash = passwordEncoder.encode(request.password)
                    ?: throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPSTREAM_UNAVAILABLE, "비밀번호 암호화에 실패했어요"),
                name = request.name,
                tier = UserTier.FREE,
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
            .orElseThrow { ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "리프레시 토큰이 유효하지 않아요") }

        if (tokenEntity.expiresAt.isBefore(now)) {
            tokenEntity.revoked = true
            throw ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "리프레시 토큰이 만료되었어요")
        }

        val accessToken = jwtTokenProvider.issueAccessToken(tokenEntity.user.id)
        return RefreshData(
            accessToken = accessToken,
            expiresIn = authProperties.accessTokenExpirySeconds
        )
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
