package com.sanggwonai.api.analysis.repository

import com.sanggwonai.api.analysis.entity.AnalysisEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AnalysisRepository : JpaRepository<AnalysisEntity, String> {

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
