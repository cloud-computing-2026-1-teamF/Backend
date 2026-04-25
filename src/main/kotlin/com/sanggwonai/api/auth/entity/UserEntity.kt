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

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(nullable = false, length = 50)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var tier: UserTier,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)
