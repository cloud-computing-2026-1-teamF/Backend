package com.sanggwonai.api.report.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal

/** 가영 투자회수 실데이터 1행 (property_id × category_id). 단위: 만원/개월. */
data class InvestmentPayback(
    val initialInvestmentMan: Int?,
    val storeAvgSalesMan: Int?,
    val monthlyNetProfitMan: Int?,
    val paybackMonths: BigDecimal?,
    val paybackLabel: String?,
    val salesBasis: String?
)

/**
 * vacancy_investment_payback 조회 (changelog-022).
 * 미적재 매물/업종이면 null -> ContextAssembler 가 백엔드 추정으로 폴백.
 */
@Repository
class VacancyInvestmentPaybackRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun find(propertyId: String, categoryId: String): InvestmentPayback? = runCatching {
        jdbcTemplate.query(
            """
            select initial_investment_man, store_avg_sales_man, monthly_net_profit_man,
                   payback_months, payback_label, sales_basis
            from vacancy_investment_payback
            where property_id = :pid and category_id = :cid
            """.trimIndent(),
            mapOf("pid" to propertyId, "cid" to categoryId)
        ) { rs, _ ->
            InvestmentPayback(
                initialInvestmentMan = (rs.getObject("initial_investment_man") as? Number)?.toInt(),
                storeAvgSalesMan = (rs.getObject("store_avg_sales_man") as? Number)?.toInt(),
                monthlyNetProfitMan = (rs.getObject("monthly_net_profit_man") as? Number)?.toInt(),
                paybackMonths = rs.getBigDecimal("payback_months"),
                paybackLabel = rs.getString("payback_label"),
                salesBasis = rs.getString("sales_basis")
            )
        }.firstOrNull()
    }.getOrNull()  // 테이블 미존재/쿼리 실패 시 백엔드 추정으로 폴백
}
