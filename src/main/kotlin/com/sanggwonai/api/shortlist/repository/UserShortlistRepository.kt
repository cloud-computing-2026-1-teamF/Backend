package com.sanggwonai.api.shortlist.repository

import com.sanggwonai.api.shortlist.entity.UserShortlistEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserShortlistRepository : JpaRepository<UserShortlistEntity, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<UserShortlistEntity>
    fun findByUserIdAndVacancyId(userId: String, vacancyId: String): UserShortlistEntity?
    fun deleteByUserIdAndVacancyId(userId: String, vacancyId: String): Long
}
