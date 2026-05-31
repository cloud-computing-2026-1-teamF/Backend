package com.sanggwonai.api.report.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * vacancy_review_insights(jsonb) 조회. (changelog-021)
 * insight 컬럼에는 스크래퍼 옵션B 결과(section_05_review_insight)가 그대로 들어있어,
 * 백엔드는 파싱만 해서 그대로 통과시킨다(재계산 금지).
 */
@Repository
class VacancyReviewInsightRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    /** 매물(property_id)의 리뷰 태그 인사이트. 미수집이면 null → 보고서 chapter_8 비활성. */
    fun find(propertyId: String): Map<String, Any?>? = runCatching {
        val json = jdbcTemplate.query(
            "select insight from vacancy_review_insights where property_id = :pid",
            mapOf("pid" to propertyId)
        ) { rs, _ -> rs.getString("insight") }.firstOrNull()
        json?.let { objectMapper.readValue(it, object : TypeReference<Map<String, Any?>>() {}) }
    }.getOrNull()  // 테이블 미존재/쿼리·파싱 실패 시 ch8 생략(보고서는 정상 생성)
}
