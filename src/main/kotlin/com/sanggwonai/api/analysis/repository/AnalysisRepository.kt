package com.sanggwonai.api.analysis.repository

import com.sanggwonai.api.analysis.entity.AnalysisEntity
import com.sanggwonai.api.analysis.entity.AnalysisStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AnalysisRepository : JpaRepository<AnalysisEntity, String> {

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): List<AnalysisEntity>

    fun findByUserIdAndSavedOrderByCreatedAtDesc(userId: String, saved: Boolean, pageable: Pageable): List<AnalysisEntity>

    fun countByUserId(userId: String): Long

    fun countByUserIdAndStatus(userId: String, status: AnalysisStatus): Long

    fun countByUserIdAndSavedTrue(userId: String): Long

    @Query(
        """
        select count(a) from AnalysisEntity a
        where a.userId = :userId
          and a.createdAt between :from and :to
        """
    )
    fun countCreatedByUserInRange(
        @Param("userId") userId: String,
        @Param("from") from: Instant,
        @Param("to") to: Instant
    ): Long
}
