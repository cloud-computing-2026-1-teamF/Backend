package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.dto.VacancyMetricDistribution
import com.sanggwonai.api.vacancy.dto.VacancyMetricReference
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class VacancyMetricReferenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun find(categoryId: String, vacancyId: String): VacancyMetricReference? {
        return jdbcTemplate.query(
            """
            select *
            from vacancy_metric_references
            where category_id = :categoryId
              and property_id = :vacancyId
            """.trimIndent(),
            mapOf("categoryId" to categoryId, "vacancyId" to vacancyId)
        ) { rs, _ -> rs.toMetricReference() }
            .firstOrNull()
    }

    fun findCategorySummary(categoryId: String): VacancyMetricReference? {
        return jdbcTemplate.query(
            """
            select *
            from vacancy_metric_references
            where category_id = :categoryId
            order by property_id
            limit 1
            """.trimIndent(),
            mapOf("categoryId" to categoryId)
        ) { rs, _ ->
            rs.toMetricReference(
                selectedVacancyId = null,
                clearSelectedValues = true
            )
        }.firstOrNull()
    }

    private fun ResultSet.toMetricReference(
        selectedVacancyId: String? = getString("property_id"),
        clearSelectedValues: Boolean = false
    ): VacancyMetricReference {
        return VacancyMetricReference(
            categoryId = getString("category_id"),
            vacancyId = selectedVacancyId,
            peerCount = getInt("peer_count"),
            footTrafficDaily = distribution("foot", clearSelectedValues),
            competition500m = distribution("competition", clearSelectedValues),
            averageSalesMonthly = distribution("sales", clearSelectedValues)
        )
    }

    private fun ResultSet.distribution(prefix: String, clearSelectedValues: Boolean): VacancyMetricDistribution {
        return VacancyMetricDistribution(
            selected = if (clearSelectedValues) null else getBigDecimal("${prefix}_selected"),
            average = getBigDecimal("${prefix}_average"),
            median = getBigDecimal("${prefix}_median"),
            min = getBigDecimal("${prefix}_min"),
            max = getBigDecimal("${prefix}_max"),
            p10 = getBigDecimal("${prefix}_p10"),
            p25 = getBigDecimal("${prefix}_p25"),
            p75 = getBigDecimal("${prefix}_p75"),
            p90 = getBigDecimal("${prefix}_p90"),
            percentile = if (clearSelectedValues) null else getBigDecimal("${prefix}_percentile")
        )
    }
}
