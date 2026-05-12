package com.sanggwonai.api.shortlist.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_vacancy_shortlist")
class UserShortlistEntity(
    @Id
    @Column(nullable = false, length = 40)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 40)
    val userId: String,

    @Column(name = "vacancy_id", nullable = false, length = 40)
    val vacancyId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)
