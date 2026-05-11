package com.sanggwonai.api.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(nullable = false, length = 40)
    val id: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = true)
    var passwordHash: String?,

    @Column(nullable = false, length = 50)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var tier: UserTier,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    var oauthProvider: AuthProvider,

    @Column(name = "kakao_id", nullable = true, unique = true, length = 50)
    var kakaoId: String? = null,

    @Column(name = "naver_id", nullable = true, unique = true, length = 50)
    var naverId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)
