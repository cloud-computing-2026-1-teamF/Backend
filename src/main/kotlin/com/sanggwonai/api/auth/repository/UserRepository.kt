package com.sanggwonai.api.auth.repository

import com.sanggwonai.api.auth.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): Optional<UserEntity>
    fun existsByEmail(email: String): Boolean
    fun findByKakaoId(kakaoId: String): Optional<UserEntity>
}
