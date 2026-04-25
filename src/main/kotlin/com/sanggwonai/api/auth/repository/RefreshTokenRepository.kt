package com.sanggwonai.api.auth.repository

import com.sanggwonai.api.auth.entity.RefreshTokenEntity
import com.sanggwonai.api.auth.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, String> {
    fun findByTokenAndRevokedFalse(token: String): Optional<RefreshTokenEntity>
    fun findAllByUserAndRevokedFalse(user: UserEntity): List<RefreshTokenEntity>
}
