package com.sanggwonai.api.report.repository

import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 50m 반경 동종 가게 1곳 + 방문자 키워드 태그(가게 1 : 태그 N). voted_total/voter_count = 옵션B 가중용. */
data class PoolStore(
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val votedTotal: Int?,
    val voterCount: Int?,
    val tags: List<PoolTag>
)

data class PoolTag(val tag: String, val count: Int)

/**
 * crawl_naver_stores / crawl_naver_store_tags 에서 한 매물 좌표 반경 내 '동종' 가게를 조회 (옵션B 런타임).
 *
 * 정확한 구면거리는 인덱스를 못 타므로 2단계:
 *   1) 바운딩박스(±dLat/±dLng) 범위 SQL — idx_crawl_naver_stores_geo(lat,lng) 사용, 빠름.
 *   2) Kotlin haversine 으로 radiusM 이내만 정밀 필터(박스 모서리 제거).
 * 동종 판정은 category LIKE(synonym 토큰 OR). 태그는 같은 쿼리로 조인해 가게별로 묶는다.
 * 크롤 테이블이 비었거나 없으면 빈 리스트 -> 호출측에서 chapter_8 비활성.
 */
@Repository
class ReviewStorePoolRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    fun findWithin(lat: Double, lng: Double, radiusM: Int, categoryTokens: List<String>): List<PoolStore> {
        if (categoryTokens.isEmpty() || radiusM <= 0) return emptyList()

        val dLat = radiusM / 111_000.0
        val dLng = radiusM / (111_000.0 * cos(Math.toRadians(lat)).coerceAtLeast(1e-6))
        val params = MapSqlParameterSource()
            .addValue("latMin", lat - dLat).addValue("latMax", lat + dLat)
            .addValue("lngMin", lng - dLng).addValue("lngMax", lng + dLng)
        val catClause = categoryTokens.indices.joinToString(" or ") { "lower(s.category) like :cat$it" }
        categoryTokens.forEachIndexed { i, t -> params.addValue("cat$i", "%${t.lowercase()}%") }

        val sql = """
            select s.id, s.naver_place_id, s.lat, s.lng, s.voted_total, s.voter_count, t.tag, t.count
            from crawl_naver_stores s
            join crawl_naver_store_tags t on t.store_id = s.id
            where s.lat between :latMin and :latMax
              and s.lng between :lngMin and :lngMax
              and ($catClause)
            order by s.id, t.count desc
        """.trimIndent()

        val byStore = LinkedHashMap<Long, Mutable>()
        runCatching {
            jdbc.query(sql, params, RowCallbackHandler { rs ->
                val id = rs.getLong("id")
                val pool = byStore.getOrPut(id) {
                    Mutable(
                        placeId = rs.getString("naver_place_id"),
                        lat = rs.getDouble("lat"),
                        lng = rs.getDouble("lng"),
                        votedTotal = (rs.getObject("voted_total") as? Number)?.toInt(),
                        voterCount = (rs.getObject("voter_count") as? Number)?.toInt()
                    )
                }
                rs.getString("tag")?.let {
                    pool.tags.add(PoolTag(it, (rs.getObject("count") as? Number)?.toInt() ?: 0))
                }
            })
        }.getOrNull()  // 테이블 미존재/빈 풀 -> 빈 결과(ch8 비활성)

        return byStore.values
            .filter { haversineM(lat, lng, it.lat, it.lng) <= radiusM }
            .map { PoolStore(it.placeId, it.lat, it.lng, it.votedTotal, it.voterCount, it.tags) }
    }

    private class Mutable(
        val placeId: String,
        val lat: Double,
        val lng: Double,
        val votedTotal: Int?,
        val voterCount: Int?,
        val tags: MutableList<PoolTag> = mutableListOf()
    )

    private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
