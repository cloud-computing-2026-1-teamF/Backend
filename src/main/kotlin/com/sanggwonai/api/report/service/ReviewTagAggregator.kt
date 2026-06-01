package com.sanggwonai.api.report.service

import com.sanggwonai.api.report.repository.PoolStore
import org.springframework.stereotype.Component
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 50m 동종 가게 태그 -> 옵션B 가중 집계 (스크래퍼 export_competitors.py 의 Kotlin 포팅).
 *
 * 별점 미사용 — voter_count 를 '잘되는/붐비는 집' 프록시로 사용.
 *   가게 내 태그 비중   share = count / votedTotal
 *   동네 수요 점수      score(tag) = Σ share·√voterCount      (가게 신뢰도 가중 합산)
 *   잘되는집 공통       voter_count 상위 ~1/3 가게의 store_share 큰 태그
 *   차별화              gap = 잘되는집_store_share − 수요_store_share (>0)
 *
 * 출력은 옛 precompute(section_05_review_insight)와 동일 형태 -> 프롬프트/PDF 무변경.
 * vacancy_id / category_id 는 null 로 두고 호출측(ReportContextAssembler)이 채운다.
 */
@Component
class ReviewTagAggregator {

    fun build(stores: List<PoolStore>, radiusM: Int, categoryLabel: String?): Map<String, Any?> {
        val demand = computeDemand(stores)
        val nTagged = stores.count { shares(it).isNotEmpty() }
        val popular = popularCommon(stores)
        val differentiators = differentiators(demand, popular)
        return linkedMapOf(
            "collected" to stores.isNotEmpty(),
            "vacancy_id" to null,
            "category" to categoryLabel,
            "category_id" to null,
            "scope" to linkedMapOf(
                "radius_m" to radiusM,
                "same_category_only" to true,
                "store_count" to stores.size,
                "tagged_store_count" to nTagged
            ),
            "demand_tags" to demand.take(DEMAND_TOP_N).map {
                linkedMapOf("tag" to it.tag, "score" to round3(it.score), "store_share" to it.storeShare, "rank" to it.rank)
            },
            "popular_store_common_tags" to popular,
            "differentiator_tags" to differentiators,
            "weighting" to "demand=Σ(share·√voterCount), popular=voterCount top~1/3",
            "low_sample" to (nTagged <= 1)
        )
    }

    private class DemandRow(val tag: String, val score: Double, val storeShare: Double, var rank: Int)

    /** 가게 내 태그 비중 share = count / votedTotal. votedTotal 없으면 태그 count 합으로 대체. */
    private fun shares(s: PoolStore): Map<String, Double> {
        val voted = s.votedTotal ?: s.tags.sumOf { it.count }
        if (voted <= 0 || s.tags.isEmpty()) return emptyMap()
        return s.tags.associate { it.tag to it.count.toDouble() / voted }
    }

    /** 동네 수요 = Σ share_i · √voter_i. store_share = (태그 보유 가게수)/(태그 보유 전체 가게수). */
    private fun computeDemand(stores: List<PoolStore>): List<DemandRow> {
        val wscore = HashMap<String, Double>()
        val have = HashMap<String, Int>()
        var nTagged = 0
        for (s in stores) {
            val sh = shares(s)
            if (sh.isEmpty()) continue
            nTagged++
            val w = sqrt((s.voterCount ?: 1).coerceAtLeast(1).toDouble())
            for ((tag, share) in sh) {
                wscore[tag] = (wscore[tag] ?: 0.0) + share * w
                have[tag] = (have[tag] ?: 0) + 1
            }
        }
        val n = nTagged.coerceAtLeast(1)
        val rows = wscore.entries
            .map { (tag, score) -> DemandRow(tag, score, round2((have[tag] ?: 0).toDouble() / n), 0) }
            .sortedByDescending { it.score }
        rows.forEachIndexed { i, r -> r.rank = i + 1 }
        return rows
    }

    /** 잘되는집(voter_count 상위 ~1/3) 공통 태그. store_share 큰 순, 동률이면 평균 voter_count. */
    private fun popularCommon(stores: List<PoolStore>): List<Map<String, Any?>> {
        val ranked = stores.filter { (it.voterCount ?: 0) > 0 }.sortedByDescending { it.voterCount ?: 0 }
        if (ranked.size < MIN_TAGGED_FOR_POPULAR) return emptyList()
        val k = maxOf(1, Math.round(ranked.size * POPULAR_RATIO).toInt())
        val pop = ranked.take(k)
        val have = HashMap<String, Int>()
        val voters = HashMap<String, MutableList<Int>>()
        for (s in pop) {
            for (tag in shares(s).keys) {
                have[tag] = (have[tag] ?: 0) + 1
                voters.getOrPut(tag) { mutableListOf() }.add(s.voterCount ?: 0)
            }
        }
        return have.entries
            .map { (tag, cnt) ->
                linkedMapOf<String, Any?>(
                    "tag" to tag,
                    "store_share" to round2(cnt.toDouble() / pop.size),
                    "avg_voter_count" to (voters[tag]?.average()?.roundToInt() ?: 0)
                )
            }
            .sortedWith(
                compareByDescending<Map<String, Any?>> { it["store_share"] as Double }
                    .thenByDescending { it["avg_voter_count"] as Int }
            )
            .take(POPULAR_TOP_N)
    }

    /** 잘되는집엔 강한데 동네 전체 수요엔 약한 태그. gap = pop_store_share − demand_store_share (>0). */
    private fun differentiators(demand: List<DemandRow>, popular: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val demandSs = demand.associate { it.tag to it.storeShare }
        return popular
            .mapNotNull { p ->
                val tag = p["tag"] as String
                val gap = round2((p["store_share"] as Double) - (demandSs[tag] ?: 0.0))
                if (gap > 0) linkedMapOf<String, Any?>("tag" to tag, "gap" to gap) else null
            }
            .sortedByDescending { it["gap"] as Double }
            .take(DIFF_TOP_N)
    }

    private fun round2(x: Double): Double = Math.round(x * 100) / 100.0
    private fun round3(x: Double): Double = Math.round(x * 1000) / 1000.0

    companion object {
        private const val DEMAND_TOP_N = 7
        private const val POPULAR_TOP_N = 5
        private const val DIFF_TOP_N = 3
        private const val POPULAR_RATIO = 0.34          // voter_count 상위 1/3 = '잘되는/붐비는 집'
        private const val MIN_TAGGED_FOR_POPULAR = 3    // 태그 보유 가게가 이보다 적으면 잘되는집/차별화 무의미
    }
}
