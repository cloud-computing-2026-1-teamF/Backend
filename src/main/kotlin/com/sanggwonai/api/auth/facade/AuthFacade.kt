package com.sanggwonai.api.auth.facade

import com.sanggwonai.api.auth.dto.LoginData
import com.sanggwonai.api.auth.dto.LoginRequest
import com.sanggwonai.api.auth.dto.MeData
import com.sanggwonai.api.auth.dto.RefreshData
import com.sanggwonai.api.auth.dto.SignupRequest
import com.sanggwonai.api.auth.service.AuthContextResolver
import com.sanggwonai.api.auth.service.AuthService
import org.springframework.stereotype.Component

@Component
class AuthFacade(
    private val authService: AuthService,
    private val authContextResolver: AuthContextResolver
) {
    fun me(authorizationHeader: String?): MeData {
        val authContext = authContextResolver.resolveOrThrow(authorizationHeader)
        return authService.me(authContext)
    }

    fun login(request: LoginRequest): LoginData = authService.login(request)

    fun signup(request: SignupRequest): LoginData = authService.signup(request)

    fun refresh(refreshToken: String): RefreshData = authService.refresh(refreshToken)
}
