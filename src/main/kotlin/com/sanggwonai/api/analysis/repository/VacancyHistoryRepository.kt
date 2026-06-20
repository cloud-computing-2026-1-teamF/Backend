package com.sanggwonai.api.analysis.repository

import com.sanggwonai.api.analysis.dto.VacancyHistoryDto
import com.sanggwonai.api.analysis.dto.VacancyHistorySummaryDto
import com.sanggwonai.api.analysis.dto.VacancyOccupancyHistoryDto
import com.sanggwonai.api.analysis.dto.VacancyScoreTrendPointDto
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class VacancyHistoryRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun findByVacancyIds(propertyIds: List<String>, categoryId: String): Map<String, VacancyHistoryDto> {
        val ids = propertyIds.distinct()
        if (ids.isEmpty()) return emptyMap()

        val scoreRows = findScoreRows(ids, categoryId).groupBy { it.propertyId }
        val occupancyRows = findOccupancyRows(ids).groupBy { it.propertyId }

        return ids.mapNotNull { propertyId ->
            val trend = scoreRows[propertyId].orEmpty()
            val timeline = occupancyRows[propertyId].orEmpty()
            if (trend.isEmpty() && timeline.isEmpty()) {
                null
            } else {
                propertyId to VacancyHistoryDto(
                    scoreTrend = trend.map { it.toDto() },
                    occupancyTimeline = timeline.map { it.toDto() },
                    summary = summarize(trend, timeline)
                )
            }
        }.toMap()
    }

    private fun findScoreRows(propertyIds: List<String>, categoryId: String): List<ScoreHistoryRow> = runCatching {
        jdbcTemplate.query(
            """
            select property_id, category_id, score_year, survival_score, score_delta,
                   confidence_label, data_basis, source
            from vacancy_score_history
            where property_id in (:propertyIds)
              and category_id = :categoryId
            order by property_id, score_year
            """.trimIndent(),
            mapOf("propertyIds" to propertyIds, "categoryId" to categoryId)
        ) { rs, _ ->
            ScoreHistoryRow(
                propertyId = rs.getString("property_id"),
                categoryId = rs.getString("category_id"),
                year = rs.getInt("score_year"),
                score = rs.getBigDecimal("survival_score"),
                delta = rs.getBigDecimal("score_delta"),
                confidenceLabel = rs.getString("confidence_label"),
                basis = rs.getString("data_basis"),
                source = rs.getString("source")
            )
        }
    }.getOrDefault(emptyList())

    private fun findOccupancyRows(propertyIds: List<String>): List<OccupancyHistoryRow> = runCatching {
        jdbcTemplate.query(
            """
            select id, property_id, started_on, ended_on, tenant_label, business_category,
                   status, monthly_rent_man, deposit_man, exit_reason_code,
                   exit_reason_summary, source
            from vacancy_occupancy_history
            where property_id in (:propertyIds)
            order by property_id, started_on nulls first, id
            """.trimIndent(),
            mapOf("propertyIds" to propertyIds)
        ) { rs, _ ->
            OccupancyHistoryRow(
                id = rs.getString("id"),
                propertyId = rs.getString("property_id"),
                startedOn = rs.getObject("started_on", LocalDate::class.java),
                endedOn = rs.getObject("ended_on", LocalDate::class.java),
                tenantLabel = rs.getString("tenant_label"),
                businessCategory = rs.getString("business_category"),
                status = rs.getString("status"),
                monthlyRent = (rs.getObject("monthly_rent_man") as? Number)?.toLong(),
                deposit = (rs.getObject("deposit_man") as? Number)?.toLong(),
                exitReasonCode = rs.getString("exit_reason_code"),
                exitReasonSummary = rs.getString("exit_reason_summary"),
                source = rs.getString("source")
            )
        }
    }.getOrDefault(emptyList())

    private fun summarize(
        trend: List<ScoreHistoryRow>,
        timeline: List<OccupancyHistoryRow>
    ): VacancyHistorySummaryDto {
        val first = trend.firstOrNull()?.score
        val last = trend.lastOrNull()?.score
        val delta = if (first != null && last != null) last.subtract(first) else null
        val direction = when {
            delta == null -> "flat"
            delta >= BigDecimal("3.0") -> "up"
            delta <= BigDecimal("-3.0") -> "down"
            else -> "flat"
        }
        val activeYears = timeline.count { it.status != "vacant" }
        val vacancyYears = timeline.count { it.status == "vacant" }
        val lastExitReason = timeline
            .asReversed()
            .firstOrNull { !it.exitReasonSummary.isNullOrBlank() }
            ?.exitReasonSummary

        return VacancyHistorySummaryDto(
            scoreDirection = direction,
            scoreDelta = delta,
            scoreLabel = trend.lastOrNull()?.confidenceLabel ?: "추세 데이터 준비 중",
            occupancyPatternLabel = when {
                timeline.isEmpty() -> "점유 이력 준비 중"
                vacancyYears == 0 -> "장기 점유형 매물"
                activeYears >= 2 -> "업종 교체 이력 보유"
                else -> "최근 공실 전환"
            },
            lastExitReason = lastExitReason,
            source = trend.firstOrNull()?.source ?: timeline.firstOrNull()?.source ?: "unknown"
        )
    }
}

private data class ScoreHistoryRow(
    val propertyId: String,
    val categoryId: String,
    val year: Int,
    val score: BigDecimal,
    val delta: BigDecimal?,
    val confidenceLabel: String?,
    val basis: String?,
    val source: String?
) {
    fun toDto() = VacancyScoreTrendPointDto(
        year = year,
        score = score,
        delta = delta,
        confidenceLabel = confidenceLabel,
        basis = basis,
        source = source
    )
}

private data class OccupancyHistoryRow(
    val id: String,
    val propertyId: String,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val tenantLabel: String,
    val businessCategory: String?,
    val status: String,
    val monthlyRent: Long?,
    val deposit: Long?,
    val exitReasonCode: String?,
    val exitReasonSummary: String?,
    val source: String?
) {
    fun toDto() = VacancyOccupancyHistoryDto(
        id = id,
        startedOn = startedOn,
        endedOn = endedOn,
        tenantLabel = tenantLabel,
        businessCategory = businessCategory,
        status = status,
        monthlyRent = monthlyRent,
        deposit = deposit,
        exitReasonCode = exitReasonCode,
        exitReasonSummary = exitReasonSummary,
        source = source
    )
}
