package com.sanggwonai.api.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    @Column(nullable = false, length = 120)
    val token: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)
