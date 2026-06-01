package com.sanggwonai.api.report.service

import com.sanggwonai.api.report.repository.PoolStore
import com.sanggwonai.api.report.repository.PoolTag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 옵션B 가중 집계 검증 (DB 불필요한 순수 단위 테스트).
 * export_competitors.py 의 산식(Σ share·√voterCount, 정규화, top-N)이 Kotlin 포팅에서 보존되는지 확인.
 */
class ReviewTagAggregatorTest {

    private val agg = ReviewTagAggregator()

    private fun store(place: String, voter: Int, vararg tags: Pair<String, Int>) =
        PoolStore(place, 37.55, 126.92, tags.sumOf { it.second }, voter,
            tags.map { PoolTag(it.first, it.second) })

    @Test
    fun `붐비는 가게(voter 큰)의 공통 태그가 수요 상위로 가중된다`() {
        val stores = listOf(
            // voter 300 인 인기점 2곳이 '집중하기좋아요' 공유 -> 수요 1위 기대
            store("a", 300, "집중하기좋아요" to 200, "조용해요" to 50),
            store("b", 280, "집중하기좋아요" to 150, "디저트가맛있어요" to 30),
            // voter 5 인 한산한 가게의 단독 태그는 가중 작아 밀림
            store("c", 5, "가성비좋아요" to 8, "양이많아요" to 2)
        )

        val insight = agg.build(stores, 50, "카페디저트")

        @Suppress("UNCHECKED_CAST")
        val demand = insight["demand_tags"] as List<Map<String, Any?>>
        assertTrue(demand.isNotEmpty(), "demand_tags 비어있음")
        assertEquals("집중하기좋아요", demand.first()["tag"], "가장 가중 높은 태그가 1위여야 함")
        assertEquals(1, demand.first()["rank"])

        @Suppress("UNCHECKED_CAST")
        val scope = insight["scope"] as Map<String, Any?>
        assertEquals(3, scope["store_count"])
        assertEquals(3, scope["tagged_store_count"])
        assertEquals(50, scope["radius_m"])
        assertEquals(true, insight["collected"])
    }

    @Test
    fun `빈 풀이면 collected=false, store_count=0`() {
        val insight = agg.build(emptyList(), 50, "한식")
        assertEquals(false, insight["collected"])
        @Suppress("UNCHECKED_CAST")
        val scope = insight["scope"] as Map<String, Any?>
        assertEquals(0, scope["store_count"])
        @Suppress("UNCHECKED_CAST")
        assertTrue((insight["demand_tags"] as List<*>).isEmpty())
    }

    @Test
    fun `votedTotal 로 정규화되어 태그 횟수 절대값이 아닌 비중으로 평가된다`() {
        // 리뷰 적은 가게(voted 10)와 많은 가게(voted 1000)가 같은 '조용해요' 비중(0.5)을 가지면
        // 절대 횟수와 무관하게 비중 기반으로 합산돼야 한다(한 태그가 횟수만으로 독식하지 않음).
        val stores = listOf(
            store("x", 50, "조용해요" to 5, "깨끗해요" to 5),       // voted 10, 각 0.5
            store("y", 50, "조용해요" to 500, "넓어요" to 500)      // voted 1000, 각 0.5
        )
        val insight = agg.build(stores, 50, "카페디저트")
        @Suppress("UNCHECKED_CAST")
        val demand = insight["demand_tags"] as List<Map<String, Any?>>
        // '조용해요'는 두 가게 공유(store_share=1.0) -> 단독 태그들보다 점수 높아야 1위
        assertEquals("조용해요", demand.first()["tag"])
        assertEquals(1.0, demand.first()["store_share"])
    }
}
