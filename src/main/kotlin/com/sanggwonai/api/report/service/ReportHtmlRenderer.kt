package com.sanggwonai.api.report.service

import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 보고서 렌더러 — OpenAI 해석 JSON + DB 사실을 standalone HTML 리포트로 합성.
 *
 * 설계: 숫자·차트는 input_data(DB 사실)에서 직접, 문장(해석)은 AI report(output_schema)에서.
 *  - 게이지·시간대 라인차트: inline SVG
 *  - 막대·KPI·표·콜아웃·리스크·액션: standalone CSS
 *
 * 7섹션: 임원요약 / 입지특성 / 업종적합도 / 추천Top3 / 투자회수(opt) / 리뷰(opt) / 부록.
 */
@Component
class ReportHtmlRenderer {

    fun render(report: Map<String, Any?>, input: Map<String, Any?>): ByteArray {
        return buildHtml(report, input).toByteArray(Charsets.UTF_8)
    }

    // ───────────────────────── HTML 조립 ─────────────────────────
    private fun buildHtml(report: Map<String, Any?>, input: Map<String, Any?>): String = buildString {
        val meta = asMap(report["report_metadata"]) ?: emptyMap<Any?, Any?>()
        val title = str(meta["보고서_제목"]).ifBlank { "AI 입지 분석 보고서" }
        append("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"utf-8\"/>")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>")
        append("<title>").append(esc(title)).append("</title><style>").append(CSS).append("</style></head><body>")
        append("<main class=\"report-shell\">")
        append("<nav class=\"report-nav\"><div><b>상권을 부탁해</b><span>AI 입지 분석 보고서</span></div>")
        append("<a href=\"#appendix\">분석 기준 보기</a></nav>")
        // v6.5 매물별 탭 레이아웃: report 에 property_reports 가 있으면 매물 탭 보고서로 렌더.
        if (report.containsKey("property_reports")) {
            renderPerProperty(this, report, input)
            append("</main></body></html>")
            return@buildString
        }
        sectionSummary(this, report, input)
        sectionProperty(this, report, input)
        sectionLocation(this, report, input)
        sectionFit(this, report, input)
        sectionTop3(this, report, input)
        sectionMarketSignals(this, report, input)
        // v6.3 신규: 이 자리의 내력(점수 추세 + 점유 이력/무덤자리). input 섹션 존재 시에만.
        if (path(input, "section_07_location_history") != null)
            sectionLocationHistory(this, report, input)
        // 렌더 여부는 백엔드가 조립한 input 섹션 존재만으로 판정한다. LLM 자기보고(활성_여부)에
        // 의존하면, 데이터가 있는데도 LLM 이 활성_여부=false 로 내보내면 섹션이 조용히 사라진다.
        if (path(input, "section_06_investment_payback") != null)
            sectionPayback(this, report, input)
        if (path(input, "section_05_review_insight") != null)
            sectionReview(this, report, input)
        sectionAppendix(this, report)
        append("</main></body></html>")
    }

    // ── §1 표지 + 임원 요약 ──────────────────────────────────────
    private fun sectionSummary(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>, cover: Boolean = true) {
        val meta = asMap(report["report_metadata"]) ?: emptyMap<Any?, Any?>()
        val sa = asMap(path(input, "saved_analysis")) ?: emptyMap<Any?, Any?>()
        val top1 = asList(sa["top3"]).firstOrNull().let { asMap(it) } ?: emptyMap<Any?, Any?>()
        val ch1 = asMap(report["chapter_1_executive_summary"]) ?: emptyMap<Any?, Any?>()

        val title = str(meta["보고서_제목"]).ifBlank {
            "[${str(sa["region"])}] ${str(sa["category"])} 입지 분석 보고서"
        }
        val grade = str(meta["보고서_등급"])
        val stamp = str(meta["추천_도장"])
        val score = (intOf(sa["topScore"]) ?: intOf(top1["score"]) ?: 0).coerceIn(0, 100)

        if (!cover) { sb.append("<div class=\"sheet\">"); summaryBody(sb, sa, top1, ch1, score); return }
        sb.append("<div class=\"sheet first\">")
        // cover
        sb.append("<div class=\"cover\"><div class=\"brand\">상권을 부탁해 · AI 입지 분석 보고서</div>")
        sb.append("<h1>").append(esc(title)).append("</h1>")
        str(meta["보고서_부제"]).ifBlank { null }?.let { sb.append("<div class=\"csub\">").append(esc(it)).append("</div>") }
        sb.append("<div class=\"badges\">")
        if (grade.isNotBlank()) sb.append("<span class=\"badge grade\">등급 ").append(esc(grade)).append("</span>")
        if (stamp.isNotBlank()) sb.append("<span class=\"badge stamp\">").append(esc(stamp)).append("</span>")
        sb.append("<span class=\"badge\">").append(esc(str(sa["categoryEmoji"]))).append(" ").append(esc(str(sa["category"]))).append("</span>")
        sb.append("<span class=\"badge\">📍 ").append(esc(str(sa["region"])))
        intOf(sa["radius"])?.let { sb.append(" · 반경 ").append(it).append("m") }
        sb.append("</span>")
        str(sa["date"]).ifBlank { null }?.let { sb.append("<span class=\"badge\">").append(esc(it)).append("</span>") }
        sb.append("</div></div>")
        summaryBody(sb, sa, top1, ch1, score)
    }

    /** §1 임원 요약 본문 (표지 제외). 매물별 탭에서도 동일하게 재사용. */
    private fun summaryBody(sb: StringBuilder, sa: Map<*, *>, top1: Map<*, *>, ch1: Map<*, *>, score: Int) {
        // body
        sb.append("<div class=\"pad\">")
        secHead(sb, "1", "임원 요약", null)
        // hero: gauge + kpis + callout
        sb.append("<table class=\"hero\"><tr><td class=\"gaugecell\">")
        sb.append("<div class=\"gauge\">").append(gaugeSvg(score))
        sb.append("<div class=\"gctr\"><b style=\"color:").append(scoreColor(score)).append("\">").append(score)
            .append("%</b><span>예상 생존율</span></div></div>")
        sb.append("</td><td class=\"herobody\">")
        // KPI cards from input
        sb.append("<table class=\"kpis\"><tr>")
        kpi(sb, "하루 유동인구", commaOrDash(top1["foot"]), "명")
        kpi(sb, "동네 월평균 매출" + (str(sa["category"]).ifBlank { null }?.let { "($it)" } ?: ""), wonShort(dbl(top1["rev"])), "")
        kpi(sb, "500m 경쟁점", commaOrDash(top1["comp"]), "곳")
        sb.append("</tr></table>")
        str(ch1["한_줄_결론"]).ifBlank { str(ch1["의사결정_권장"]) }.ifBlank { null }
            ?.let { sb.append("<div class=\"callout\">").append(esc(it)).append("</div>") }
        sb.append("</td></tr></table>")
        // summary body
        str(ch1["요약_본문"]).ifBlank { null }?.let { sb.append("<p class=\"body\">").append(esc(it)).append("</p>") }

        // v6.3 점수 분해 + 미래 전망 (왜 이 점수인가 / 앞으로 N년)
        val feats = asList(path(top1, "scoreExplanation", "top_features"))
        val horizons = asList(sa["horizon_forecast"]).ifEmpty { asList(top1["horizon"]) }
        if (feats.isNotEmpty() || horizons.isNotEmpty()) {
            sb.append("<table class=\"twocol\"><tr><td>")
            if (feats.isNotEmpty()) {
                sb.append("<div class=\"panel\"><h3>왜 이 예상 생존율인가 <span class=\"hint\">— 상권 평균 대비</span></h3>")
                feats.take(5).forEach { f ->
                    val fm = asMap(f) ?: return@forEach
                    effectBar(sb, str(fm["label"]), str(fm["effect"]), dbl(fm["current"]), dbl(fm["average"]))
                }
                str(ch1["점수_분해"]).ifBlank { null }?.let { sb.append("<p class=\"body sm faint\">").append(esc(it)).append("</p>") }
                sb.append("</div>")
            }
            sb.append("</td><td>")
            if (horizons.isNotEmpty()) {
                sb.append("<div class=\"panel\"><h3>미래 전망 <span class=\"hint\">기간별 예상 생존율</span></h3>")
                horizons.sortedBy { intOf(asMap(it)?.get("years")) ?: 99 }.forEach { hz ->
                    val hm = asMap(hz) ?: return@forEach
                    val yr = intOf(hm["years"]) ?: return@forEach
                    val sc = (intOf(hm["score"]) ?: 0).coerceIn(0, 100)
                    barColored(sb, "${yr}년", sc, "${sc}%", scoreColor(sc), yr == 3)
                }
                str(ch1["미래_전망"]).ifBlank { null }?.let { sb.append("<p class=\"body sm faint\">").append(esc(it)).append("</p>") }
                sb.append("</div>")
            }
            sb.append("</td></tr></table>")
        }

        // risks | actions (two columns)
        val risks = asList(ch1["리스크_요인"])
        val acts = asList(ch1["액션_아이템"])
        if (risks.isNotEmpty() || acts.isNotEmpty()) {
            sb.append("<table class=\"twocol\"><tr><td>")
            if (risks.isNotEmpty()) {
                sb.append("<div class=\"colh warn\">⚠ 핵심 리스크</div>")
                risks.take(3).forEach { r ->
                    val rm = asMap(r) ?: return@forEach
                    val sev = str(rm["심각도"])
                    sb.append("<div class=\"risk\"><div class=\"risktop\"><span class=\"rt\">").append(esc(str(rm["리스크"])))
                        .append("</span><span class=\"pill ").append(sevClass(sev)).append("\">").append(esc(sevLabel(sev)))
                        .append("</span></div><div class=\"rd\">").append(esc(str(rm["대응_방안"]))).append("</div></div>")
                }
            }
            sb.append("</td><td>")
            if (acts.isNotEmpty()) {
                sb.append("<div class=\"colh ok\">✓ 액션 아이템</div>")
                acts.sortedBy { intOf(asMap(it)?.get("우선순위")) ?: 9 }.take(4).forEachIndexed { i, a ->
                    val am = asMap(a) ?: return@forEachIndexed
                    sb.append("<table class=\"act\"><tr><td class=\"ano\"><span>").append((intOf(am["우선순위"]) ?: (i + 1)))
                        .append("</span></td><td class=\"atx\"><b>").append(esc(str(am["할_일"]))).append("</b>")
                    str(am["이유"]).ifBlank { null }?.let { sb.append("<p>").append(esc(it)).append("</p>") }
                    sb.append("</td><td class=\"adur\">").append(esc(str(am["예상_소요시간"]))).append("</td></tr></table>")
                }
            }
            sb.append("</td></tr></table>")
        }
        sb.append("</div></div>")
    }

    // ── §2 공실 운영 조건 ────────────────────────────────────────
    private fun sectionProperty(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val top1 = asList(path(input, "saved_analysis", "top3")).firstOrNull().let { asMap(it) } ?: return
        val ch2 = asMap(report["chapter_2_property_operation_context"]) ?: emptyMap<Any?, Any?>()
        val property = asMap(top1["property"]) ?: emptyMap<Any?, Any?>()
        val lease = asMap(top1["lease"]) ?: emptyMap<Any?, Any?>()
        val facilities = asMap(top1["facilities"]) ?: emptyMap<Any?, Any?>()
        val rentTotal = (dbl(lease["monthlyRent"]) ?: dbl(top1["rent"]) ?: 0.0) + (dbl(lease["maintenanceFee"]) ?: dbl(top1["mgmt"]) ?: 0.0)
        val revenue = dbl(top1["rev"])
        val burden = if (revenue != null && revenue > 0) rentTotal / revenue else null

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "2", "공실 운영 조건", "상세 페이지 핵심 정보를 보고서에 반영")
        sb.append("<div class=\"ops-grid\">")

        sb.append("<section class=\"info-card accent-orange\"><h3>임대 조건</h3><div class=\"tile-grid\">")
        metricTile(sb, "월세+관리비", wonShort(rentTotal), "매달 고정 부담")
        metricTile(sb, "보증금", wonShort(dbl(lease["deposit"]) ?: dbl(top1["deposit"])), "초기 현금")
        metricTile(sb, "권리금", wonShort(dbl(lease["premium"])), "인수 비용")
        metricTile(sb, "전용면적", areaText(dbl(lease["dedicatedArea"]) ?: dbl(top1["area"])), "운영 가능 면적")
        metricTile(sb, "층 / 총층", listOf(
            floorText(str(property["floor"]).ifBlank { str(top1["floor"]) }),
            str(property["totalFloors"]).ifBlank { null }?.let { "${it}층" }
        ).filterNotNull().filter { it.isNotBlank() && it != "-" }.joinToString(" / ").ifBlank { "-" }, "동선 확인")
        metricTile(sb, "임대 부담률", burden?.let { "%.1f%%".format(it * 100) } ?: "-", "월 임대비 / 평균매출")
        sb.append("</div>")
        comment(sb, str(ch2["임대_조건_코멘트"]))
        sb.append("</section>")

        sb.append("<section class=\"info-card accent-blue\"><h3>매물 프로필</h3><div class=\"info-list\">")
        infoRow(sb, "도로명주소", str(property["roadAddress"]).ifBlank { str(top1["addr"]) })
        infoRow(sb, "지번주소", str(property["lotAddress"]))
        infoRow(sb, "건물유형", str(property["buildingType"]))
        infoRow(sb, "건물용도", str(property["buildingUse"]))
        infoRow(sb, "거래유형", str(property["transactionType"]).ifBlank { str(top1["transactionType"]) })
        infoRow(sb, "공시지가", wonRaw(dbl(lease["officialLandPrice"])))
        infoRow(sb, "관심도", "조회 ${commaOrDash(property["viewCount"])} · 찜 ${commaOrDash(property["favoriteCount"])}")
        sb.append("</div>")
        comment(sb, str(ch2["현장_체크_코멘트"]))
        sb.append("</section>")

        sb.append("<section class=\"info-card accent-teal\"><h3>시설·운영 옵션</h3><div class=\"facility-cloud\">")
        facilityChip(sb, "주차", boolText(facilities["parkingAvailable"], dbl(facilities["parkingCount"])?.let { "${it.roundToInt()}면" }))
        facilityChip(sb, "엘리베이터", boolText(facilities["elevatorAvailable"], intOf(facilities["elevatorCount"])?.let { "${it}대" }))
        facilityChip(sb, "화장실", listOf(str(facilities["restroomType"]), dbl(facilities["restroomCount"])?.let { "${it.roundToInt()}개" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "-" })
        facilityChip(sb, "냉난방", listOf(str(facilities["heatingType"]), if (facilities["airConditioner"] == true) "에어컨" else null, if (facilities["heater"] == true) "난방기" else null).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "-" })
        facilityChip(sb, "운영 옵션", listOf(
            if (facilities["lateNightOperationAvailable"] == true) "심야영업" else null,
            if (facilities["priceNegotiable"] == true) "가격협의" else null,
            if (facilities["rentAdjustable"] == true) "임대료 조정" else null,
            if (facilities["rentFreePeriodAvailable"] == true) "무상임대기간" else null
        ).filterNotNull().joinToString(" · ").ifBlank { "-" })
        facilityChip(sb, "공간 옵션", listOf(
            if (facilities["terrace"] == true) "테라스" else null,
            if (facilities["rooftop"] == true) "루프탑" else null,
            if (facilities["interior"] == true) "인테리어" else null,
            if (facilities["storage"] == true) "창고" else null
        ).filterNotNull().joinToString(" · ").ifBlank { "-" })
        sb.append("</div>")
        comment(sb, str(ch2["시설_운영_코멘트"]))
        sb.append("</section>")

        sb.append("</div></div></div>")
    }

    // ── §3 입지 특성 ────────────────────────────────────────────
    private fun sectionLocation(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val sa = asMap(path(input, "saved_analysis")) ?: emptyMap<Any?, Any?>()
        val top1 = asList(sa["top3"]).firstOrNull().let { asMap(it) } ?: emptyMap<Any?, Any?>()
        val ch4 = asMap(report["chapter_4_location_characteristics"]) ?: emptyMap<Any?, Any?>()
        val mref = asMap(path(input, "vacancy_metric_reference"))

        @Suppress("UNCHECKED_CAST")
        val hourly = (top1["footHourly"] as? List<*>)?.mapNotNull { intOf(it) } ?: emptyList()

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "3", "입지 특성", null)

        // 시간대별 유동인구 라인차트
        if (hourly.size >= 12) {
            val peak = hourly.indices.maxByOrNull { hourly[it] } ?: 0
            sb.append("<div class=\"panel\"><h3>시간대별 유동인구 <span class=\"hint\">— 피크 ").append(peak).append("시</span></h3>")
            sb.append(lineChartSvg(hourly))
            sb.append("<table class=\"axis\"><tr><td>0시</td><td style=\"text-align:center\">6시</td>")
                .append("<td style=\"text-align:center\">12시</td><td style=\"text-align:center\">18시</td>")
                .append("<td style=\"text-align:right\">24시</td></tr></table>")
            str(path(ch4, "section_4_1_floating_population", "시간대_패턴_해석")).ifBlank { null }
                ?.let { sb.append("<p class=\"body sm\">").append(esc(it)).append("</p>") }
            sb.append("</div>")
        }

        // 매출분포 + 경쟁강도 (two columns)
        sb.append("<table class=\"twocol\"><tr><td>")
        sb.append("<div class=\"panel\"><h3>동네 매출 분포 내 위치</h3>")
        val sales = asMap(mref?.get("averageSalesMonthly"))
        val sMin = dbl(sales?.get("min")); val sMed = dbl(sales?.get("median")); val sMax = dbl(sales?.get("max"))
        val sSel = dbl(sales?.get("selected")) ?: dbl(top1["rev"])
        if (sSel != null) {
            val baseline = sMed ?: listOfNotNull(sMin, sMax).takeIf { it.size == 2 }?.average()
            val scaleMax = max(sSel, baseline ?: sSel).coerceAtLeast(1.0)
            val selectedWidth = (((sSel / scaleMax) * 100).coerceIn(6.0, 100.0)).roundToInt()
            val baselineWidth = baseline?.let { (((it / scaleMax) * 100).coerceIn(6.0, 100.0)).roundToInt() }
            val delta = baseline?.takeIf { abs(it) > 0.0 }?.let { ((sSel - it) / it) * 100.0 }
            sb.append("<div class=\"rev-compare\"><div class=\"rev-stats\">")
            sb.append("<div class=\"rev-stat accent\"><span>내 매물 추정</span><b>").append(wonMan(sSel)).append("</b></div>")
            sb.append("<div class=\"rev-stat\"><span>동네 중앙값</span><b>").append(wonMan(baseline)).append("</b></div>")
            sb.append("<div class=\"rev-stat\"><span>중앙값 대비</span><b class=\"")
                .append(if ((delta ?: 0.0) >= 0) "green" else "red")
                .append("\">").append(delta?.let { signedPercentText(it) } ?: "-").append("</b></div>")
            sb.append("</div><div class=\"rev-bars\">")
            revBar(sb, "내 매물", selectedWidth, wonMan(sSel), true)
            if (baseline != null && baselineWidth != null) revBar(sb, "중앙값", baselineWidth, wonMan(baseline), false)
            sb.append("</div></div>")
        }
        str(path(ch4, "section_4_3_estimated_revenue", "매출_환경_해석")).ifBlank {
            "내 매물 추정 ${wonMan(sSel)} 수준입니다."
        }.let { sb.append("<p class=\"body sm\">").append(esc(it)).append("</p>") }
        sb.append("</div></td><td>")

        sb.append("<div class=\"panel\"><h3>경쟁 강도 <span class=\"hint\">500m 동일 업종</span></h3>")
        val mine = intOf(top1["comp"]) ?: 0
        val peer = intOf(path(mref, "competition500m", "average")) ?: intOf(path(mref, "competition500m", "median"))
        val barMax = max(mine, peer ?: mine).coerceAtLeast(1)
        bar(sb, "내 상권", pct(mine, barMax), mine.toString(), true, null)
        if (peer != null) bar(sb, "업종 평균", pct(peer, barMax), peer.toString(), false, null)
        if (peer != null && peer > 0) {
            val ratio = mine.toDouble() / peer
            sb.append("<p class=\"body sm\">평균 대비 <b class=\"red\">약 ").append("%.1f".format(ratio).removeSuffix(".0"))
                .append("배</b> ").append(if (ratio >= 1.3) "과밀 → 차별화 필수." else "수준입니다.").append("</p>")
        }
        str(path(ch4, "section_4_2_competition", "포지셔닝_제안")).ifBlank { null }
            ?.let { sb.append("<p class=\"body sm faint\">").append(esc(it)).append("</p>") }
        sb.append("</div></td></tr></table>")

        // 접근성
        val nearby = asMap(top1["nearby"])
        val accText = str(path(ch4, "section_4_4_accessibility", "접근성_종합_평가"))
        if (nearby != null || accText.isNotBlank()) {
            sb.append("<div class=\"panel\" style=\"margin-top:14px\"><h3>교통 · 접근성</h3>")
            sb.append("<table class=\"kvs\">")
            kv(sb, "지하철", summarizeTransit(str(nearby?.get("subway"))))
            kv(sb, "버스", summarizeTransit(str(nearby?.get("bus"))))
            kv(sb, "주차", str(nearby?.get("parking")))
            sb.append("</table>")
            if (accText.isNotBlank()) sb.append("<p class=\"body sm\">").append(esc(accText)).append("</p>")
            sb.append("</div>")
        }
        sb.append("</div></div>")
    }

    // ── §4 업종 적합도 (9개) ─────────────────────────────────────
    private fun sectionFit(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val scores = asMap(path(input, "selected_vacancy_extra", "nine_category_scores")) ?: emptyMap<Any?, Any?>()
        val selected = str(path(input, "saved_analysis", "category"))
        val ch5 = asMap(report["chapter_5_business_fit_analysis"]) ?: emptyMap<Any?, Any?>()

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "4", "업종 적합도 — 9개 업종 비교", null)
        str(path(ch5, "선택_카테고리_평가", "해석")).ifBlank { null }
            ?.let { sb.append("<p class=\"body\" style=\"margin-top:-2px\">").append(esc(it)).append("</p>") }

        val rows = scores.entries.mapNotNull { e ->
            val s = intOf(e.value) ?: return@mapNotNull null
            (e.key?.toString() ?: "") to s
        }.sortedByDescending { it.second }
        if (rows.isNotEmpty()) {
            sb.append("<div style=\"margin-top:8px\">")
            rows.forEach { (name, s) ->
                val hl = name == selected || (selected.isNotBlank() && name.contains(selected))
                bar(sb, if (hl) "$name (선택)" else name, s, "$s", hl, null)
            }
            sb.append("</div>")
        }
        str(ch5["best_3_카테고리"]).ifBlank { null }
            ?.let { sb.append("<div class=\"callout amber\">상위 3개: ").append(esc(it)).append("</div>") }
        sb.append("</div></div>")
    }

    // ── §5 추천 매물 Top 3 ──────────────────────────────────────
    private fun sectionTop3(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val top3 = asList(path(input, "saved_analysis", "top3"))
        val ch3 = asMap(report["chapter_3_top3_property_analysis"]) ?: emptyMap<Any?, Any?>()
        val payProps = asList(path(input, "section_06_investment_payback", "properties"))
        val recRank = intOf(path(ch3, "최종_권장_매물", "rank"))
        val details = asList(ch3["매물_상세_분석"])

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "5", "추천 매물 Top 3", null)
        str(ch3["chapter_intro"]).ifBlank { null }
            ?.let { sb.append("<p class=\"body\" style=\"margin-top:-2px\">").append(esc(it)).append("</p>") }

        sb.append("<table class=\"cmp\"><thead><tr><th>매물</th><th>층</th><th class=\"r\">월세</th>")
            .append("<th class=\"r\">보증금</th><th class=\"r\">예상 생존율</th><th class=\"r\">월순이익</th><th>회수 평가</th></tr></thead><tbody>")
        top3.forEachIndexed { i, t ->
            val tm = asMap(t) ?: return@forEachIndexed
            val rank = intOf(tm["rank"]) ?: (i + 1)
            val vid = str(tm["vacancyId"])
            val pay = payProps.mapNotNull { asMap(it) }.firstOrNull { str(it["vacancy_id"]) == vid }
                ?: payProps.mapNotNull { asMap(it) }.firstOrNull { intOf(it["rank"]) == rank }
            val net = dbl(pay?.get("월순이익_만원"))
            val label = str(pay?.get("투자회수평가"))
            val isRec = (recRank != null && recRank == rank) || (recRank == null && tm["recommended"] == true && i == 0)
            sb.append("<tr").append(if (isRec) " class=\"best\"" else "").append(">")
            sb.append("<td>").append(esc(shortAddr(str(tm["addr"]))))
            if (isRec) sb.append(" <span class=\"recmark\">추천</span>")
            sb.append("</td><td>").append(esc(str(tm["floor"]).ifBlank { "-" })).append("</td>")
            sb.append("<td class=\"r\">").append(wonShort(dbl(tm["rent"]))).append("</td>")
            sb.append("<td class=\"r\">").append(wonShort(dbl(tm["deposit"]))).append("</td>")
            sb.append("<td class=\"r\"><b>").append(intOf(tm["score"]) ?: 0).append("%</b></td>")
            if (net != null) {
                val cls = if (net < 0) "red" else "green"
                sb.append("<td class=\"r ").append(cls).append("\">").append(if (net >= 0) "+" else "").append(net.roundToLong()).append("만</td>")
            } else sb.append("<td class=\"r faint\">-</td>")
            sb.append("<td>").append(if (label.isBlank()) "-" else "<span class=\"tag ${labelTagClass(label)}\">${esc(label)}</span>").append("</td>")
            sb.append("</tr>")
        }
        sb.append("</tbody></table>")

        // 매물별 강점/약점 (AI)
        if (details.isNotEmpty()) {
            sb.append("<table class=\"swslist\">")
            details.take(3).forEach { d ->
                val dm = asMap(d) ?: return@forEach
                sb.append("<tr><td class=\"swr\">").append(intOf(dm["rank"]) ?: "").append("순위</td><td>")
                str(dm["한_줄_평가"]).ifBlank { null }?.let { sb.append("<div class=\"swt\">").append(esc(it)).append("</div>") }
                str(dm["강점"]).ifBlank { null }?.let { sb.append("<div class=\"sw g\">＋ ").append(esc(it)).append("</div>") }
                str(dm["약점"]).ifBlank { null }?.let { sb.append("<div class=\"sw w\">－ ").append(esc(it)).append("</div>") }
                sb.append("</td></tr>")
            }
            sb.append("</table>")
        }
        str(path(ch3, "최종_권장_매물", "권장_근거")).ifBlank { null }
            ?.let { sb.append("<div class=\"callout\">최종 권장: ").append(esc(it)).append("</div>") }
        sb.append("</div></div>")
    }

    // ── §6 상권 수요·경쟁 시그널 ────────────────────────────────
    private fun sectionMarketSignals(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>, no: String = "6") {
        val top1 = asList(path(input, "saved_analysis", "top3")).firstOrNull().let { asMap(it) } ?: return
        val ch6 = asMap(report["chapter_6_market_signal_diagnosis"]) ?: emptyMap<Any?, Any?>()
        val pop = asMap(top1["population"]) ?: emptyMap<Any?, Any?>()
        val commercial = asMap(top1["commercial"]) ?: emptyMap<Any?, Any?>()

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, no, "상권 수요·경쟁 시그널", "공실 상세 지표 기반")

        sb.append("<div class=\"signal-layout\">")
        sb.append("<section class=\"info-card accent-blue\"><h3>인구 구성</h3><div class=\"tile-grid compact\">")
        metricTile(sb, "분기 유동", peopleText(dbl(pop["floatingQuarterly"])), "반경 상권 수요")
        metricTile(sb, "분기 상주", peopleText(dbl(pop["residentQuarterly"])), "생활권 수요")
        metricTile(sb, "분기 직장", peopleText(dbl(pop["workerQuarterly"])), "평일 점심·퇴근")
        metricTile(sb, "2030 비율", percentText(dbl(pop["age2030PopulationRatio"])), "젊은 수요")
        metricTile(sb, "여성 비율", percentText(dbl(pop["femalePopulationRatio"])), "타깃 성향")
        metricTile(sb, "직장/유동", percentText(dbl(pop["workerToFloatingRatio"])), "오피스 의존도")
        metricTile(sb, "저녁 인구", percentText(dbl(pop["eveningPopulationRatio"])), "저녁 체류")
        metricTile(sb, "주말 인구", percentText(dbl(pop["weekendPopulationRatio"])), "주말 유입")
        sb.append("</div>")
        comment(sb, str(ch6["수요_구성_코멘트"]))
        sb.append("</section>")

        sb.append("<section class=\"info-card accent-orange\"><h3>경쟁과 성장</h3>")
        sb.append("<table class=\"modern-table\"><thead><tr><th>반경</th><th>식당</th><th>카페</th><th>동종</th><th>성장률</th></tr></thead><tbody>")
        competitionRow(sb, "250m", commercial, "250m")
        competitionRow(sb, "500m", commercial, "500m")
        competitionRow(sb, "1000m", commercial, "1000m")
        sb.append("</tbody></table>")
        comment(sb, str(ch6["경쟁_성장_코멘트"]))
        sb.append("</section>")

        sb.append("<section class=\"info-card accent-teal\"><h3>매출과 안정성</h3><div class=\"tile-grid compact\">")
        metricTile(sb, "가게당 평균 매출(전체 업종)", dbl(commercial["averageSalesPerStore"])?.let { wonRaw(it) } ?: wonShort(dbl(top1["rev"])), "월 추정")
        metricTile(sb, "개업률", percentText(dbl(commercial["openingRate"])), "진입 활력")
        metricTile(sb, "폐업률", percentText(dbl(commercial["closureRate"])), "안정성")
        metricTile(sb, "저녁 매출", percentText(dbl(commercial["eveningSalesRatio"])), "저녁 수요")
        metricTile(sb, "심야 매출", percentText(dbl(commercial["lateNightSalesRatio"])), "야간 수요")
        metricTile(sb, "주말 매출", percentText(dbl(commercial["weekendSalesRatio"])), "주말 수요")
        metricTile(sb, "2030 매출", percentText(dbl(commercial["age2030SalesRatio"])), "젊은 소비")
        metricTile(sb, "여성 매출", percentText(dbl(commercial["femaleSalesRatio"])), "여성 소비")
        metricTile(sb, "음식 지출", wonRaw(dbl(commercial["foodSpending"])), "상권 음식 소비")
        metricTile(sb, "가게당 지출", wonRaw(dbl(commercial["spendingPerStore"])), "구매력")
        sb.append("</div>")
        val mode = listOf(
            if (commercial["commercialGrowthType"] == true) "성장형 상권" else null,
            if (commercial["commercialTurnoverType"] == true) "교체 활발형 상권" else null
        ).filterNotNull().joinToString(" · ")
        if (mode.isNotBlank()) sb.append("<div class=\"mode-strip\">").append(esc(mode)).append("</div>")
        comment(sb, str(ch6["매출_리스크_코멘트"]))
        sb.append("</section>")

        sb.append("</div></div></div>")
    }

    // ── §7 투자 회수 ────────────────────────────────────────────
    private fun sectionPayback(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val pay = asMap(path(input, "section_06_investment_payback")) ?: return
        val ch9 = asMap(report["chapter_9_investment_payback"]) ?: emptyMap<Any?, Any?>()
        val props = asList(pay["properties"])

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "7", "투자 회수 분석", null)

        sb.append("<table class=\"twocol\"><tr><td>")
        sb.append("<div class=\"panel\"><h3>매물별 월 순이익</h3>")
        val maxAbs = props.mapNotNull { dbl(asMap(it)?.get("월순이익_만원"))?.let { v -> abs(v) } }.maxOrNull() ?: 1.0
        props.take(3).forEach { p ->
            val pm = asMap(p) ?: return@forEach
            val net = dbl(pm["월순이익_만원"]) ?: 0.0
            val w = pct(abs(net).roundToInt(), maxAbs.roundToInt().coerceAtLeast(1))
            val color = if (net < 0) RED else GREEN
            barColored(sb, shortAddr(str(pm["주소_간략"])), w, (if (net >= 0) "+" else "") + net.roundToLong(), color, net < 0)
        }
        sb.append("<p class=\"body sm faint\">* 초기투자비에 인테리어·설비 공사비 미포함 (실제 투자비는 더 높을 수 있습니다)</p></div>")
        sb.append("</td><td>")
        sb.append("<div class=\"panel\"><h3>1순위 투자 회수 요약</h3>")
        miniKpi(sb, "초기투자비", wonShort(dbl(pay["초기투자비_만원"])), "")
        val evalLabel = str(pay["투자회수평가"])
        miniKpi(sb, "투자 회수 평가", evalLabel.ifBlank { "-" }, "", if (evalLabel.contains("적자")) RED else INK)
        intOf(path(pay, "bep_action", "피크_손익분기_잔수"))?.let {
            val ticket = dbl(path(pay, "bep_action", "객단가원")) ?: 0.0
            val peakRevenue = if (ticket > 0) ceil((it * ticket) / 10_000.0) * 10_000.0 else null
            miniKpi(sb, "손익분기 — 피크 필요 매출", peakRevenue?.let { v -> "시간당 약 ${wonRaw(v)}" } ?: "확인 필요", "", INK)
        }
        sb.append("</div></td></tr></table>")
        str(ch9["회수_해석"]).ifBlank { str(path(ch9, "bep_action")) }.ifBlank { null }
            ?.let { sb.append("<div class=\"callout blue\">").append(esc(it)).append("</div>") }
        str(pay["기존시설_문구"]).ifBlank { null }
            ?.let { sb.append("<div class=\"callout green\">").append(esc(it)).append("</div>") }
        sb.append("</div></div>")
    }

    // ── §8 리뷰 인사이트 (옵션) ─────────────────────────────────
    private fun sectionReview(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val ch8 = asMap(report["chapter_8_review_insight"]) ?: return
        val props = asList(path(input, "section_05_review_insight", "properties"))
        // AI 매물별 코멘트: 순위 -> 코멘트
        val commentByRank = HashMap<Int, String>()
        asList(ch8["매물별_리뷰"]).forEachIndexed { i, c ->
            val cm = asMap(c) ?: return@forEachIndexed
            commentByRank[intOf(cm["순위"]) ?: (i + 1)] = str(cm["코멘트"])
        }

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "8", "주변 리뷰 인사이트", str(path(ch8, "데이터_범위")))
        sb.append("<p class=\"body\" style=\"margin-top:-2px\">추천 매물 각각의 반경 50m 동종 가게 방문자 태그를 모아, 매물별 동네 수요를 봅니다.</p>")

        props.forEachIndexed { i, p ->
            val pm = asMap(p) ?: return@forEachIndexed
            val rank = intOf(pm["rank"]) ?: (i + 1)
            val addr = shortAddr(str(pm["주소_간략"]))
            val tags = asList(pm["demand_tags"])
            val maxScore = tags.mapNotNull { dbl(asMap(it)?.get("score")) }.maxOrNull() ?: 1.0

            sb.append("<div class=\"panel\"><h3>매물 ").append(rank)
            if (addr.isNotBlank()) sb.append(" <span class=\"hint\">").append(esc(addr)).append("</span>")
            sb.append("</h3>")
            if (tags.isEmpty()) {
                sb.append("<p class=\"body sm faint\">반경 50m 내 동종 가게 태그가 없습니다.</p>")
            } else {
                tags.take(5).forEachIndexed { j, t ->
                    val tm = asMap(t) ?: return@forEachIndexed
                    val sc = dbl(tm["score"]) ?: 0.0
                    val w = if (maxScore > 0) (sc / maxScore * 100).roundToInt() else 50
                    val strength = if (w >= 66) "강" else if (w >= 33) "중" else "하"
                    bar(sb, str(tm["tag"]), w, strength, j == 0, null)
                }
            }
            commentByRank[rank]?.ifBlank { null }
                ?.let { sb.append("<p class=\"body sm\">").append(esc(it)).append("</p>") }
            sb.append("</div>")
        }

        str(path(ch8, "리뷰_종합_해석")).ifBlank { null }
            ?.let { sb.append("<div class=\"callout blue\">").append(esc(it)).append("</div>") }
        sb.append("</div></div>")
    }

    // ── §9 부록 ────────────────────────────────────────────────
    private fun sectionAppendix(sb: StringBuilder, report: Map<String, Any?>, no: String = "9") {
        val ch7 = asMap(report["chapter_7_appendix"]) ?: emptyMap<Any?, Any?>()
        sb.append("<div class=\"sheet\" id=\"appendix\"><div class=\"pad\">")
        secHead(sb, no, "부록 — 본 보고서의 한계", null)
        sb.append("<div class=\"disc\"><ul>")
        val limit = str(ch7["본_보고서의_한계"])
        val items = (if (limit.contains('\n')) limit.split('\n') else limit.split(Regex("(?<=다\\.)\\s+")))
            .map { it.trim() }.filter { it.length > 4 }
        if (items.isNotEmpty()) items.forEach { sb.append("<li>").append(esc(it)).append("</li>") }
        else {
            sb.append("<li>가게 자체 특성(맛·서비스·운영 역량)은 분석에 반영되지 않습니다.</li>")
            sb.append("<li>매출·순이익은 동네 평균 추정치로, 개별 매물 실제 매출과 다를 수 있습니다.</li>")
            sb.append("<li>본 보고서는 참고용이며, 최종 결정 전 현장 실사·전문가 상담을 권장합니다.</li>")
        }
        sb.append("</ul></div>")
        sb.append("<p class=\"body sm faint\" style=\"margin-top:12px\">예상 생존율: AI가 예측한 3년 생존 확률 · 공공데이터(인허가·상권분석·소상공인·공시지가) 기반</p>")
        sb.append("<p class=\"body sm\" style=\"margin-top:16px\"><b>투자 회수 계산 방법</b></p>")
        sb.append("<div class=\"disc\"><ul>")
        sb.append("<li><b>초기투자비</b> = 보증금 + 권리금 + 중개수수료 + (월세+관리비) × 3개월 운전자금</li>")
        sb.append("<li><b>월 순이익</b> = 동종 업종 점포당 평균 추정매출 × 영업이익률 − (월세+관리비)</li>")
        sb.append("<li><b>투자 회수기간</b> = 초기투자비 ÷ 월 순이익</li>")
        sb.append("<li><b>평균 추정매출</b>: 서울시 상권분석서비스 추정매출 데이터 기반 (상권 → 상권배후지 → 행정동 순으로 우선 적용)</li>")
        sb.append("<li><b>영업이익률</b>: 업종별 고정값 적용 (예: 일식 13%, 한식 12%, 카페 17% 등)</li>")
        sb.append("<li><b>손익분기 피크 필요 매출</b> = 손익분기 월매출 ÷ 영업일수(26일) × 피크 매출 비중(45%)</li>")
        sb.append("</ul></div>")
        sb.append("</div></div>")
    }

    // ── §내력 이 자리의 내력 (v6.3 신규) ─────────────────────────
    private fun sectionLocationHistory(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>, no: String = "★") {
        val hist = asMap(path(input, "section_07_location_history")) ?: return
        val chh = asMap(report["chapter_5_location_history"]) ?: emptyMap<Any?, Any?>()
        val trend = asList(hist["score_trend"])
        val occ = asMap(hist["occupancy"]) ?: emptyMap<Any?, Any?>()
        val timeline = asList(occ["timeline"])

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, no, "이 자리의 내력", "과거 예상 생존율 추세 + 점유 이력")

        // 무덤자리 요약 KPI
        val tenantCount = intOf(occ["tenant_count"]) ?: timeline.size
        val closedCount = intOf(occ["closed_count"]) ?: timeline.count { str(asMap(it)?.get("status")) == "closed" }
        val dir = str(occ["score_direction"])
        sb.append("<table class=\"kpis\"><tr>")
        kpi(sb, "거쳐간 가게", tenantCount.toString(), "곳")
        kpi(sb, "그중 폐업", closedCount.toString(), "곳")
        kpi(sb, "생존율 방향", when (dir) { "up" -> "회복 ↑"; "down" -> "악화 ↓"; else -> "보합 →" }, "")
        sb.append("</tr></table>")

        sb.append("<table class=\"twocol\"><tr><td>")
        // 과거 생존율 추세선
        sb.append("<div class=\"panel\"><h3>과거 예상 생존율 추세 <span class=\"hint\">연도별</span></h3>")
        val pts = trend.mapNotNull { p -> val pm = asMap(p) ?: return@mapNotNull null
            val y = intOf(pm["year"]) ?: return@mapNotNull null; val s = dbl(pm["score"]) ?: return@mapNotNull null; y to s }
        if (pts.size >= 2) {
            sb.append(scoreTrendSvg(pts))
            sb.append("<table class=\"axis\"><tr><td>").append(pts.first().first)
                .append("</td><td style=\"text-align:right\">").append(pts.last().first).append("</td></tr></table>")
        }
        str(chh["점수_추세_해석"]).ifBlank { null }?.let { sb.append("<p class=\"body sm\">").append(esc(it)).append("</p>") }
        sb.append("</div></td><td>")
        // 점유 이력 타임라인
        sb.append("<div class=\"panel\"><h3>점유 이력 <span class=\"hint\">인허가 실데이터</span></h3>")
        if (timeline.isEmpty()) sb.append("<p class=\"body sm faint\">점유 이력 데이터가 없습니다.</p>")
        else timeline.take(6).forEach { ev ->
            val em = asMap(ev) ?: return@forEach
            val closed = str(em["status"]) == "closed"
            val span = listOf(str(em["from"]).take(4), str(em["to"]).take(4).ifBlank { "현재" }).joinToString("–")
            sb.append("<div class=\"tlev\"><span class=\"tldot ").append(if (closed) "c" else "o").append("\"></span>")
            sb.append("<span class=\"tlnm\">").append(esc(str(em["tenant"])))
            str(em["category"]).ifBlank { null }?.let { sb.append(" <small>· ").append(esc(it)).append("</small>") }
            sb.append("</span><span class=\"tlpill ").append(if (closed) "c" else "o").append("\">")
                .append(if (closed) "폐업" else "영업중").append("</span><span class=\"tlyr\">").append(esc(span)).append("</span></div>")
        }
        str(chh["업종_교체_패턴_해석"]).ifBlank { null }?.let { sb.append("<p class=\"body sm\">").append(esc(it)).append("</p>") }
        sb.append("</div></td></tr></table>")

        str(chh["종합_판정"]).ifBlank { null }?.let { sb.append("<div class=\"callout\">").append(esc(it)).append("</div>") }
        sb.append("<p class=\"body sm faint\">⚠ 인허가 데이터에는 폐업 '사유'가 없어, 업태·존속기간 패턴으로 해석합니다.</p>")
        sb.append("</div></div>")
    }


    // ═══════════════ v6.5 매물별 탭 레이아웃 ═══════════════
    private fun top3Of(input: Map<String, Any?>) = asList(path(input, "saved_analysis", "top3")).mapNotNull { asMap(it) }

    /** report.property_reports(매물별 해석) 를 input.top3 와 rank 로 짝지어 점수 내림차순 정렬. */
    private fun orderedReports(report: Map<String, Any?>, input: Map<String, Any?>): List<Pair<Map<*, *>, Map<*, *>>> {
        val top3 = top3Of(input)
        return asList(report["property_reports"]).mapNotNull { asMap(it) }.mapIndexedNotNull { i, pr ->
            val rank = intOf(pr["rank"])
            // top3 항목에 rank 가 없을 수 있어(과거 assembler) rank 매칭 실패 시 인덱스로 폴백.
            val prop = top3.firstOrNull { intOf(it["rank"]) == rank } ?: top3.getOrNull(i)
            if (prop == null) null else pr to prop
        }.sortedByDescending { intOf(it.second["score"]) ?: 0 }
    }

    private fun renderPerProperty(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val ordered = orderedReports(report, input)
        coverV65(sb, report, input, ordered.size)
        compareTopV65(sb, report, input, ordered)

        // 매물 탭 네비
        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "★", "매물별 상세 보고서", "탭으로 매물을 전환하세요")
        val dup = dupAddrs(ordered)
        sb.append("<div class=\"ptabs\">")
        ordered.forEachIndexed { idx, (_, prop) ->
            val addr = propLabel(prop, full = true, dup = dup).ifBlank { "매물 ${idx + 1}" }
            sb.append("<button type=\"button\" class=\"ptab").append(if (idx == 0) " on" else "")
                .append("\" data-pp=\"").append(idx).append("\"><span class=\"ptaddr\">")
                .append(esc(addr)).append("</span></button>")
        }
        sb.append("</div></div></div>")

        // 매물별 패널 (탭 전환)
        ordered.forEachIndexed { idx, (pr, prop) ->
            sb.append("<div class=\"pppanel\" data-pp=\"").append(idx).append("\"")
            if (idx != 0) sb.append(" style=\"display:none\"")
            sb.append(">")
            val mInput = microInput(input, prop)
            val mReport = microReport(pr)
            sectionSummary(sb, mReport, mInput, cover = false)
            sectionProperty(sb, mReport, mInput)
            sectionLocation(sb, mReport, mInput)
            sectionFit(sb, mReport, mInput)
            sectionMarketSignals(sb, mReport, mInput, "5")
            if (path(mInput, "section_07_location_history") != null) sectionLocationHistory(sb, mReport, mInput, "6")
            if (path(mInput, "section_06_investment_payback") != null) sectionPayback(sb, mReport, mInput)
            if (mReport.containsKey("chapter_8_review_insight")) sectionReview(sb, mReport, mInput)
            sb.append("</div>")
        }

        sectionYourChoiceV65(sb, report, ordered)
        sectionAppendix(sb, report, "★")

        sb.append("<script>document.querySelectorAll('.ptab').forEach(function(b){b.addEventListener('click',function(){")
            .append("var i=b.getAttribute('data-pp');")
            .append("document.querySelectorAll('.ptab').forEach(function(x){x.classList.toggle('on',x.getAttribute('data-pp')===i)});")
            .append("document.querySelectorAll('.pppanel').forEach(function(p){p.style.display=(p.getAttribute('data-pp')===i)?'':'none'});")
            .append("})});</script>")
    }

    private fun coverV65(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>, n: Int) {
        val sa = asMap(path(input, "saved_analysis")) ?: emptyMap<Any?, Any?>()
        val meta = asMap(report["report_metadata"]) ?: emptyMap<Any?, Any?>()
        sb.append("<div class=\"sheet first\"><div class=\"cover\"><div class=\"brand\">상권을 부탁해 · AI 입지 분석 보고서</div>")
        sb.append("<h1>").append(esc(str(meta["보고서_제목"]).ifBlank { "[${str(sa["region"])}] ${str(sa["category"])} 매물 비교 분석" })).append("</h1>")
        str(meta["보고서_부제"]).ifBlank { null }?.let { sb.append("<div class=\"csub\">").append(esc(it)).append("</div>") }
        sb.append("<div class=\"badges\">")
        str(meta["보고서_등급"]).ifBlank { null }?.let { sb.append("<span class=\"badge grade\">등급 ").append(esc(it)).append("</span>") }
        sb.append("<span class=\"badge\">📍 ").append(esc(str(sa["region"])))
        intOf(sa["radius"])?.let { sb.append(" · 반경 ").append(it).append("m") }
        sb.append("</span><span class=\"badge\">").append(esc(str(sa["category"]))).append("</span>")
        sb.append("<span class=\"badge\">매물 ").append(n).append("개 비교</span></div></div></div>")
    }

    /** 상단 — 세 매물 한눈 비교 (성격 카드 + 핵심지표 3열표). 점수순. */
    private fun compareTopV65(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>, ordered: List<Pair<Map<*, *>, Map<*, *>>>) {
        val ov = asMap(report["comparison_overview"]) ?: emptyMap<Any?, Any?>()
        val chars = asList(ov["매물_성격"]).mapNotNull { asMap(it) }
        val cols = ordered.map { it.second }
        val dup = dupAddrs(ordered)
        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "★", "세 매물 한눈 비교", "본인 우선순위로 고르세요")
        str(ov["인트로"]).ifBlank { null }?.let { sb.append("<p class=\"body\">").append(esc(it)).append("</p>") }
        sb.append("<div class=\"charrow\">")
        ordered.forEach { (_, prop) ->
            val rank = intOf(prop["rank"])
            val ch = chars.firstOrNull { intOf(it["rank"]) == rank }
            val score = intOf(prop["score"]) ?: 0
            sb.append("<div class=\"charcard\"><span class=\"ptag\">").append(esc(str(ch?.get("성격")).ifBlank { "매물" })).append("</span>")
            sb.append("<b>").append(esc(propLabel(prop, full = false, dup = dup))).append("</b>")
            sb.append("<small>").append(esc(str(ch?.get("한줄")).ifBlank { "-" })).append("</small>")
            sb.append("<span class=\"ptscore big\">예상 생존율 ").append(score).append("%</span></div>")
        }
        sb.append("</div>")
        sb.append("<table class=\"cmp3\"><thead><tr><th>지표</th>")
        cols.forEach { sb.append("<th>").append(esc(propLabel(it, full = false, dup = dup))).append("</th>") }
        sb.append("</tr></thead><tbody>")
        cmpRow(sb, "하루 유동", cols, cmp = { dbl(it["foot"]) }) { commaOrDash(it["foot"]) }
        cmpRow(sb, "500m 경쟁", cols, lowGood = true, cmp = { dbl(it["comp"]) }) { intOf(it["comp"])?.toString() ?: "-" }
        cmpRow(sb, "월세", cols, lowGood = true, cmp = { dbl(it["rent"]).takeIf { v -> (v ?: 0.0) > 0 } }) { priceText(it, "rent") }
        cmpRow(sb, "보증금", cols, lowGood = true, cmp = { dbl(it["deposit"]).takeIf { v -> (v ?: 0.0) > 0 } }) { priceText(it, "deposit") }
        cmpRow(sb, "전용면적", cols, highlight = false) { pyeong(dbl(it["area"])) }
        cmpRow(sb, "회수기간", cols, lowGood = true, cmp = { dbl(payByRank(input, intOf(it["rank"]) ?: 0, str(it["vacancyId"]))?.get("투자회수기간_개월")) }) { t ->
            val pay = payByRank(input, intOf(t["rank"]) ?: 0, str(t["vacancyId"]))
            intOf(pay?.get("투자회수기간_개월"))?.let { "${it}개월" }
                ?: if (str(pay?.get("투자회수평가")).contains("미확인")) "미확인" else "적자/미정"
        }
        cmpRow(sb, "층", cols, cmp = { floorScore(str(it["floor"])) }) { floorText(str(it["floor"])) }
        sb.append("</tbody></table>")
        sb.append("<p class=\"body sm faint\">* 굵게 = 해당 지표에서 가장 유리한 매물</p>")
        str(ov["종합_비교평"]).ifBlank { null }?.let { sb.append("<div class=\"callout\">").append(esc(it)).append("</div>") }
        sb.append("</div></div>")
    }

    private fun sectionYourChoiceV65(sb: StringBuilder, report: Map<String, Any?>, ordered: List<Pair<Map<*, *>, Map<*, *>>>) {
        val yc = asMap(report["your_choice"]) ?: return
        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "★", "당신에게 맞는 선택", "우선순위로 고르기")
        asList(yc["축별_가이드"]).mapNotNull { asMap(it) }.forEach { g ->
            val rank = intOf(g["추천_rank"])
            val prop = ordered.firstOrNull { intOf(it.second["rank"]) == rank }?.second
            val addr = shortAddr(str(prop?.get("addr"))).ifBlank { rank?.let { "${it}순위" } ?: "-" }
            sb.append("<div class=\"guide\"><div class=\"gaxis\">").append(esc(str(g["기준"])))
                .append("</div><div class=\"garrow\">→</div><div class=\"gpick\">").append(esc(addr))
                .append("</div><div class=\"greason\">").append(esc(str(g["이유"]))).append("</div></div>")
        }
        str(yc["재확인"]).ifBlank { null }?.let { sb.append("<p class=\"body sm faint\">").append(esc(it)).append("</p>") }
        sb.append("</div></div>")
    }

    // ── micro 어댑터: property_reports[i] + top3[i] → 레거시 단일 보고서 입력으로 변환 ──
    private fun payByRank(input: Map<String, Any?>, rank: Int, vid: String): Map<*, *>? {
        val props = asList(path(input, "section_06_investment_payback", "properties")).mapNotNull { asMap(it) }
        return props.firstOrNull { str(it["vacancy_id"]) == vid } ?: props.firstOrNull { intOf(it["rank"]) == rank }
    }

    private fun cmpRow(sb: StringBuilder, label: String, cols: List<Map<*, *>>, lowGood: Boolean = false, cmp: ((Map<*, *>) -> Double?)? = null, highlight: Boolean = true, value: (Map<*, *>) -> String) {
        val vals = cols.map { value(it) }
        // 비교는 cmp(원본 숫자)가 있으면 그걸로 — '1억'/'2,000만' 처럼 단위가 섞인 표시 문자열은 파싱이 깨지므로.
        val nums = if (cmp != null) cols.map { cmp(it) } else vals.map { it.replace(Regex("[^0-9.-]"), "").toDoubleOrNull() }
        val best = if (!highlight) null else nums.filterNotNull().let { if (it.isEmpty()) null else if (lowGood) it.min() else it.max() }
        sb.append("<tr><td class=\"cmpk\">").append(esc(label)).append("</td>")
        vals.forEachIndexed { i, v ->
            val isBest = best != null && nums[i] != null && nums[i] == best
            sb.append("<td").append(if (isBest) " class=\"win\"" else "").append(">").append(esc(v)).append("</td>")
        }
        sb.append("</tr>")
    }

    /** 전용면적(㎡) → 평 표기. */
    private fun pyeong(areaM2: Double?): String = areaM2?.takeIf { it > 0 }?.let { "${(it / 3.305785).roundToInt()}평" } ?: "-"

    /** "1층"/"지하1층"/"-1"/"2" 등을 정수 층으로 정규화. 지하=음수. */
    private fun floorToInt(floor: String): Int? {
        if (floor.isBlank()) return null
        if (floor.contains("지하") || floor.startsWith("B", true))
            return -(Regex("\\d+").find(floor)?.value?.toIntOrNull() ?: 1)
        return Regex("-?\\d+").find(floor)?.value?.toIntOrNull()
    }

    /** 층 표시용 — 정수/문자 모두 '지하N층 / N층'으로. */
    private fun floorText(floor: String): String {
        val n = floorToInt(floor) ?: return floor.ifBlank { "-" }
        return when { n < 0 -> "지하${-n}층"; n == 0 -> "-"; else -> "${n}층" }
    }

    /** ordered 안에서 주소가 겹치는 매물 집합(같은 건물 다른 층). 라벨에 층을 덧붙여 구분. */
    private fun dupAddrs(ordered: List<Pair<Map<*, *>, Map<*, *>>>): Set<String> =
        ordered.map { str(it.second["addr"]) }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    /** 매물 라벨 — 주소(full=풀주소/false=축약). 같은 주소가 둘 이상이면 '… (지하1층)' 식으로 층 병기. */
    private fun propLabel(prop: Map<*, *>, full: Boolean, dup: Set<String>): String {
        val raw = str(prop["addr"])
        val base = (if (full) raw else shortAddr(raw)).ifBlank { "-" }
        return if (raw in dup) "$base (${floorText(str(prop["floor"]))})" else base
    }

    /** 층 비교용 점수 — 1층이 가장 유리, 그다음 저층, 지하·0층이 최하. */
    private fun floorScore(floor: String): Double? {
        val n = floorToInt(floor) ?: return null
        return when { n == 1 -> 3.0; n >= 2 -> (3.0 - (n - 1) * 0.3).coerceAtLeast(1.5); else -> 1.0 }
    }

    /** 가격 셀 — 0이면 매매 매물은 '매매', 아니면 '-'. (매매의 월세 0 을 '최저'로 강조하지 않기 위함) */
    private fun priceText(prop: Map<*, *>, key: String): String {
        val v = dbl(prop[key]) ?: 0.0
        if (v > 0) return wonShort(v)
        return if (str(prop["transactionType"]).contains("매매")) "매매" else "-"
    }

    /** input 을 복제하되 saved_analysis.top3 를 해당 매물 1건으로 좁히고, 매물별 섹션(수익성/리뷰/내력)을 그 매물 것으로 교체. */
    private fun microInput(input: Map<String, Any?>, prop: Map<*, *>): Map<String, Any?> {
        val sa = asMap(path(input, "saved_analysis")) ?: emptyMap<Any?, Any?>()
        val newSa = LinkedHashMap<String, Any?>()
        sa.forEach { (k, v) -> newSa[k.toString()] = v }
        newSa["top3"] = listOf(prop)
        newSa["topScore"] = prop["score"]
        val m = LinkedHashMap<String, Any?>()
        input.forEach { (k, v) -> m[k] = v }
        m["saved_analysis"] = newSa
        asMap(prop["metric_reference"])?.let { m["vacancy_metric_reference"] = it }
        asMap(prop["selected_vacancy_extra"])?.let { m["selected_vacancy_extra"] = it }
        m["section_06_investment_payback"] = microPay(input, prop)
        m["section_05_review_insight"] = microReview(input, prop)
        m["section_07_location_history"] = microHistory(prop)
        return m
    }

    /** 이 매물의 리뷰만 추림. 테스트 fixture(prop.review) 우선, 없으면 prod 경로(root section_05.properties[rank]). */
    private fun microReview(input: Map<String, Any?>, prop: Map<*, *>): Map<String, Any?>? {
        asMap(prop["review"])?.let { return linkedMapOf("properties" to listOf(it)) }
        val props = asList(path(input, "section_05_review_insight", "properties")).mapNotNull { asMap(it) }
        if (props.isEmpty()) return null
        val rank = intOf(prop["rank"]); val vid = str(prop["vacancyId"])
        val mine = props.firstOrNull { str(it["vacancy_id"]) == vid } ?: props.firstOrNull { intOf(it["rank"]) == rank } ?: return null
        return linkedMapOf("properties" to listOf(mine))
    }

    private fun microPay(input: Map<String, Any?>, prop: Map<*, *>): Map<String, Any?>? {
        val pay = asMap(path(input, "section_06_investment_payback")) ?: return null
        val mine = payByRank(input, intOf(prop["rank"]) ?: -1, str(prop["vacancyId"])) ?: return null
        return linkedMapOf(
            "properties" to listOf(mine),
            "초기투자비_만원" to mine["초기투자비_만원"],
            "투자회수평가" to (mine["투자회수평가"] ?: pay["투자회수평가"]),
            "bep_action" to (mine["bep_action"] ?: pay["bep_action"]),
            "기존시설_문구" to (mine["기존시설_문구"] ?: pay["기존시설_문구"])
        )
    }

    private fun microHistory(prop: Map<*, *>): Map<String, Any?>? {
        val trend = asList(prop["history"])
        val occ = asMap(prop["occupancy"])
        if (trend.isEmpty() && occ == null) return null
        return linkedMapOf("score_trend" to trend, "occupancy" to (occ ?: emptyMap<Any?, Any?>()))
    }

    /** property_reports[i] 의 매물별 해석을 레거시 챕터 키 구조로 변환(렌더 함수 재사용). */
    private fun microReport(pr: Map<*, *>): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        val 요약 = asMap(pr["요약"]) ?: emptyMap<Any?, Any?>()
        m["chapter_1_executive_summary"] = linkedMapOf(
            "한_줄_결론" to 요약["한_줄_평가"],
            "요약_본문" to 요약["종합_해석"],
            "의사결정_권장" to 요약["한_줄_평가"],
            "점수_분해" to 요약["점수_분해"],
            "미래_전망" to 요약["미래_전망"],
            "리스크_요인" to 요약["리스크_요인"],
            "액션_아이템" to 요약["액션_아이템"]
        )
        m["chapter_2_property_operation_context"] = asMap(pr["운영조건"]) ?: emptyMap<Any?, Any?>()
        val 입지 = asMap(pr["입지"]) ?: emptyMap<Any?, Any?>()
        m["chapter_4_location_characteristics"] = linkedMapOf(
            "section_4_1_floating_population" to linkedMapOf("시간대_패턴_해석" to 입지["시간대_패턴_해석"]),
            "section_4_2_competition" to linkedMapOf("포지셔닝_제안" to 입지["포지셔닝_제안"]),
            "section_4_3_estimated_revenue" to linkedMapOf("매출_환경_해석" to 입지["매출_환경_해석"]),
            "section_4_4_accessibility" to linkedMapOf("접근성_종합_평가" to 입지["접근성_종합_평가"])
        )
        val 적합 = asMap(pr["적합도"]) ?: emptyMap<Any?, Any?>()
        m["chapter_5_business_fit_analysis"] = linkedMapOf(
            "선택_카테고리_평가" to linkedMapOf("해석" to 적합["선택_카테고리_해석"]),
            "best_3_카테고리" to 적합["best_3_카테고리"]
        )
        m["chapter_6_market_signal_diagnosis"] = asMap(pr["시장"]) ?: emptyMap<Any?, Any?>()
        asMap(pr["내력"])?.let { m["chapter_5_location_history"] = it }
        asMap(pr["수익성"])?.let { m["chapter_9_investment_payback"] = it }
        asMap(pr["리뷰"])?.let { rv ->
            m["chapter_8_review_insight"] = linkedMapOf(
                "데이터_범위" to "반경 50m 동일 업종 방문자 태그",
                "매물별_리뷰" to listOf(linkedMapOf("순위" to pr["rank"], "코멘트" to rv["코멘트"])),
                "리뷰_종합_해석" to ""
            )
        }
        return m
    }

    // ───────────────────────── 차트/컴포넌트 ─────────────────────
    /** 점수 분해 — 상권 평균 대비 발산형 막대(긍정=초록 우측, 부정=빨강 좌측). */
    private fun effectBar(sb: StringBuilder, label: String, effect: String, current: Double?, average: Double?) {
        val color = when (effect) { "positive" -> GREEN; "negative" -> RED; "neutral" -> "#9AA4B2"; else -> "#C4CCD8" }
        val word = when (effect) { "positive" -> "강점"; "negative" -> "주의"; "neutral" -> "비슷"; else -> "-" }
        val mag = if (current != null && average != null && abs(average) > 0.0)
            ((abs(current - average) / abs(average)) * 100).coerceIn(12.0, 46.0) else 30.0
        val pos = effect == "positive"
        sb.append("<table class=\"xai\"><tr><td class=\"xlab\">").append(esc(label)).append("</td>")
        sb.append("<td class=\"xtrack\"><div class=\"xmid\"></div><div class=\"xfill\" style=\"")
        if (pos) sb.append("left:50%;") else sb.append("right:50%;")
        sb.append("width:").append("%.0f".format(mag)).append("%;background:").append(color).append("\"></div></td>")
        sb.append("<td class=\"xval\" style=\"color:").append(color).append("\">").append(word).append("</td></tr></table>")
    }

    /** 과거 연도별 생존율 추세 — 0~100 스케일 라인차트. */
    private fun scoreTrendSvg(pts: List<Pair<Int, Double>>): String {
        val w = 360; val h = 96; val n = pts.size
        if (n < 2) return ""
        fun x(i: Int) = 8.0 + (w - 16.0) * i / (n - 1)
        fun y(v: Double) = h - 12.0 - (h - 26.0) * (v.coerceIn(0.0, 100.0) / 100.0)
        val line = pts.indices.joinToString(" ") { "${"%.0f".format(x(it))},${"%.0f".format(y(pts[it].second))}" }
        val area = "${"%.0f".format(x(0))},$h $line ${"%.0f".format(x(n - 1))},$h"
        val last = pts.last()
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" style=\"width:100%;height:96px\" viewBox=\"0 0 $w $h\" preserveAspectRatio=\"none\">" +
            "<polyline fill=\"#E9F1FB\" stroke=\"none\" points=\"$area\"/>" +
            "<polyline fill=\"none\" stroke=\"#2D6FE0\" stroke-width=\"2.5\" points=\"$line\"/>" +
            "<circle cx=\"${"%.0f".format(x(n - 1))}\" cy=\"${"%.0f".format(y(last.second))}\" r=\"4\" fill=\"#2D6FE0\"/></svg>"
    }

    private fun gaugeSvg(score: Int): String {
        val r = 62.0
        val c = 2 * PI * r
        val off = c * (1 - score.coerceIn(0, 100) / 100.0)
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"130\" height=\"130\" viewBox=\"0 0 150 150\">" +
            "<circle cx=\"75\" cy=\"75\" r=\"62\" fill=\"none\" stroke=\"#EEF0F4\" stroke-width=\"14\"/>" +
            "<circle cx=\"75\" cy=\"75\" r=\"62\" fill=\"none\" stroke=\"${scoreColor(score)}\" stroke-width=\"14\" " +
            "stroke-linecap=\"round\" stroke-dasharray=\"${"%.1f".format(c)}\" stroke-dashoffset=\"${"%.1f".format(off)}\" " +
            "transform=\"rotate(-90 75 75)\"/></svg>"
    }

    private fun lineChartSvg(hourly: List<Int>): String {
        val w = 720; val h = 120; val n = hourly.size
        if (n < 2) return ""
        val maxV = max(1, hourly.maxOrNull() ?: 1)
        fun x(i: Int) = w.toDouble() * i / (n - 1)
        fun y(v: Int) = h - 10.0 - (h - 22.0) * (v.toDouble() / maxV)
        val line = hourly.indices.joinToString(" ") { "${"%.0f".format(x(it))},${"%.0f".format(y(hourly[it]))}" }
        val area = "0,$h $line $w,$h"
        val pi = hourly.indices.maxByOrNull { hourly[it] } ?: 0
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" style=\"width:100%;height:120px\" viewBox=\"0 0 $w $h\" preserveAspectRatio=\"none\">" +
            "<polyline fill=\"#FBEAE0\" stroke=\"none\" points=\"$area\"/>" +
            "<polyline fill=\"none\" stroke=\"#E85D1F\" stroke-width=\"2.5\" points=\"$line\"/>" +
            "<circle cx=\"${"%.0f".format(x(pi))}\" cy=\"${"%.0f".format(y(hourly[pi]))}\" r=\"4\" fill=\"#E85D1F\"/></svg>"
    }

    private fun kpi(sb: StringBuilder, lab: String, value: String, unit: String) {
        sb.append("<td class=\"kpi\"><div class=\"klab\">").append(esc(lab)).append("</div><div class=\"kval\">")
            .append(esc(value))
        if (unit.isNotBlank()) sb.append("<small> ").append(esc(unit)).append("</small>")
        sb.append("</div></td>")
    }

    private fun miniKpi(sb: StringBuilder, lab: String, value: String, unit: String, color: String = INK) {
        sb.append("<div class=\"mkpi\"><div class=\"klab\">").append(esc(lab)).append("</div><div class=\"mval\" style=\"color:")
            .append(color).append("\">").append(esc(value))
        if (unit.isNotBlank()) sb.append("<small> ").append(esc(unit)).append("</small>")
        sb.append("</div></div>")
    }

    private fun bar(sb: StringBuilder, name: String, widthPct: Int, value: String, hl: Boolean, fill: String?) =
        barColored(sb, name, widthPct, value, fill ?: if (hl) ORANGE else "#F0A877", hl)

    private fun barColored(sb: StringBuilder, name: String, widthPct: Int, value: String, fill: String, hl: Boolean) {
        sb.append("<table class=\"bar").append(if (hl) " hl" else "").append("\"><tr><td class=\"bnm\">").append(esc(name))
            .append("</td><td class=\"btrack\"><div class=\"bbg\"><div class=\"bfill\" style=\"width:")
            .append(widthPct.coerceIn(2, 100)).append("%;background:").append(fill).append("\"></div></div></td><td class=\"bvv\">")
            .append(esc(value)).append("</td></tr></table>")
    }

    private fun revBar(sb: StringBuilder, label: String, widthPct: Int, value: String, selected: Boolean) {
        sb.append("<div class=\"rev-row").append(if (selected) " selected" else "").append("\"><span>")
            .append(esc(label)).append("</span><div class=\"rev-track\"><div class=\"rev-fill\" style=\"width:")
            .append(widthPct.coerceIn(6, 100)).append("%\"></div></div><b>")
            .append(esc(value)).append("</b></div>")
    }

    private fun kv(sb: StringBuilder, k: String, v: String) {
        sb.append("<tr><td class=\"kvk\">").append(esc(k)).append("</td><td class=\"kvv\">")
            .append(if (v.isBlank()) "정보 없음" else esc(v)).append("</td></tr>")
    }

    private fun secHead(sb: StringBuilder, n: String, title: String, hint: String?) {
        sb.append("<h2 class=\"sec\"><span class=\"n\">").append(n).append("</span> ").append(esc(title))
        if (!hint.isNullOrBlank()) sb.append(" <span class=\"hint\">").append(esc(hint)).append("</span>")
        sb.append("</h2>")
    }

    private fun metricTile(sb: StringBuilder, label: String, value: String, sub: String) {
        sb.append("<div class=\"metric-tile\"><span>").append(esc(label)).append("</span><b>")
            .append(esc(value.ifBlank { "-" })).append("</b>")
        if (sub.isNotBlank()) sb.append("<small>").append(esc(sub)).append("</small>")
        sb.append("</div>")
    }

    private fun infoRow(sb: StringBuilder, label: String, value: String) {
        sb.append("<div class=\"info-row\"><span>").append(esc(label)).append("</span><b>")
            .append(esc(value.ifBlank { "-" })).append("</b></div>")
    }

    private fun facilityChip(sb: StringBuilder, label: String, value: String) {
        sb.append("<div class=\"facility-chip\"><span>").append(esc(label)).append("</span><b>")
            .append(esc(value.ifBlank { "-" })).append("</b></div>")
    }

    private fun comment(sb: StringBuilder, value: String) {
        if (value.isBlank()) return
        sb.append("<p class=\"consult-comment\">").append(esc(value)).append("</p>")
    }

    private fun competitionRow(sb: StringBuilder, label: String, commercial: Map<*, *>, suffix: String) {
        sb.append("<tr><td>").append(esc(label)).append("</td>")
        sb.append("<td>").append(countText(intOf(commercial["restaurantCount$suffix"]))).append("</td>")
        sb.append("<td>").append(countText(intOf(commercial["cafeCount$suffix"]))).append("</td>")
        sb.append("<td>").append(countText(intOf(commercial["sameCategoryRestaurantCount$suffix"]))).append("</td>")
        sb.append("<td>").append(esc(percentText(dbl(commercial["industryGrowthRate$suffix"])))).append("</td></tr>")
    }

    // ───────────────────────── 유틸 ──────────────────────────────
    private fun active(ch: Any?): Boolean = (asMap(ch)?.get("활성_여부") != false)
    private fun asMap(v: Any?): Map<*, *>? = v as? Map<*, *>
    private fun asList(v: Any?): List<*> = v as? List<*> ?: emptyList<Any?>()
    private fun path(root: Any?, vararg keys: String): Any? {
        var cur: Any? = root
        for (k in keys) { cur = (cur as? Map<*, *>)?.get(k) ?: return null }
        return cur
    }
    private fun str(v: Any?): String = when (v) {
        null -> ""; is Map<*, *> -> str(v["값"]); is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString()
    }
    private fun dbl(v: Any?): Double? = when (v) {
        is Number -> v.toDouble(); is String -> v.replace(",", "").toDoubleOrNull(); is Map<*, *> -> dbl(v["값"]); else -> null
    }
    private fun intOf(v: Any?): Int? = dbl(v)?.roundToInt()
    private fun pct(value: Int, maxV: Int): Int = ((value.toDouble() / maxV) * 100).roundToInt().coerceIn(0, 100)
    private fun commaOrDash(v: Any?): String = dbl(v)?.let { "%,d".format(it.toLong()) } ?: "-"
    private fun countText(v: Int?): String = v?.let { "%,d개".format(it) } ?: "-"
    private fun peopleText(v: Double?): String {
        val n = v ?: return "-"
        return if (abs(n) >= 10000) "%.1f만명".format(n / 10000.0).replace(".0만", "만")
        else "%,d명".format(n.roundToLong())
    }
    private fun percentText(v: Double?): String {
        val n = v ?: return "-"
        val p = if (abs(n) <= 1.0) n * 100.0 else n
        return "%.1f%%".format(p)
    }
    private fun areaText(v: Double?): String = v?.let { "%.1f㎡".format(it) } ?: "-"
    private fun wonRaw(v: Double?): String {
        val n = v ?: return "-"
        return when {
            abs(n) >= 100000000 -> "%.1f억원".format(n / 100000000.0).replace(".0억", "억")
            abs(n) >= 10000 -> "%,d만원".format((n / 10000.0).roundToLong())
            else -> "%,d원".format(n.roundToLong())
        }
    }
    private fun wonShort(man: Double?): String {
        val v = man ?: return "-"
        return if (abs(v) >= 10000) ("₩" + "%.1f".format(v / 10000.0).removeSuffix(".0") + "억")
        else "₩" + "%,d".format(v.toLong()) + "만"
    }
    private fun wonMan(man: Double?): String {
        val v = man ?: return "-"
        return if (abs(v) >= 10000) "%.1f억원".format(v / 10000.0).replace(".0억", "억")
        else "%,d만원".format(v.roundToLong())
    }
    private fun signedPercentText(value: Double): String {
        val sign = if (value > 0) "+" else ""
        return sign + "%.0f%%".format(value)
    }
    private fun boolText(value: Any?, detail: String? = null): String = when (value) {
        true -> if (detail.isNullOrBlank()) "있음" else "있음 · $detail"
        false -> "없음"
        else -> "-"
    }
    private fun shortAddr(addr: String): String = addr.trim().split(' ').takeLast(2).joinToString(" ").ifBlank { addr }
    /** 정류장/역 목록 덤프를 앞 N개 이름만 요약("A · B · C 외 12곳"). 노선번호 괄호 제거. 짧으면 원문 유지. */
    private fun summarizeTransit(raw: String, maxItems: Int = 3): String {
        val s = raw.trim()
        if (s.length <= 50) return s
        val items = s.split(';', '·', '\n').map { it.substringBefore('(').trim() }.filter { it.isNotBlank() }.distinct()
        if (items.size <= 1) return s.take(48) + "…"
        val head = items.take(maxItems).joinToString(" · ")
        return if (items.size > maxItems) "$head 외 ${items.size - maxItems}곳" else head
    }
    private fun scoreColor(s: Int): String = when { s >= 80 -> GREEN; s >= 60 -> ORANGE; s >= 45 -> AMBER; else -> RED }
    private fun sevClass(s: String): String = when { s.contains("높") -> "hi"; s.contains("중") -> "mid"; else -> "low" }
    private fun sevLabel(s: String): String = when { s.contains("높") -> "리스크 높음"; s.contains("중") -> "리스크 중간"; else -> "리스크 낮음" }
    private fun labelTagClass(l: String): String = when {
        l.contains("적자") -> "r"; l.contains("1년") -> "g"; l.contains("이내") -> "g"; l.contains("3년") -> "a"; else -> "b"
    }
    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("\n", "<br/>")

    companion object {
        private const val ORANGE = "#E85D1F"
        private const val GREEN = "#1A8F4C"
        private const val RED = "#D33A3A"
        private const val AMBER = "#B9791A"
        private const val INK = "#1B2330"
        private val CSS = """
            :root {
              --ink:#111827; --muted:#667085; --soft:#F5F7FB; --line:#E4E8F0;
              --orange:#E85D1F; --blue:#2D6FE0; --teal:#0FB5A6; --green:#1A8F4C;
              --red:#D33A3A; --amber:#B9791A; --paper:#FFFFFF;
              --shadow:0 18px 50px rgba(15,23,42,.09);
            }
            * { box-sizing:border-box; }
            html { scroll-behavior:smooth; }
            body {
              margin:0; background:linear-gradient(180deg,#F7F9FC 0%,#EEF4F8 100%);
              color:var(--ink); font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans KR",sans-serif;
              font-size:15px; line-height:1.62; letter-spacing:0;
            }
            .report-shell { width:min(1180px, calc(100% - 48px)); margin:0 auto; padding:24px 0 72px; }
            .report-nav {
              position:sticky; top:0; z-index:5; display:flex; justify-content:space-between; align-items:center;
              margin:0 0 18px; padding:12px 16px; border:1px solid rgba(228,232,240,.78);
              border-radius:14px; background:rgba(255,255,255,.78); backdrop-filter:blur(16px);
              box-shadow:0 10px 30px rgba(15,23,42,.06);
            }
            .report-nav div { display:flex; gap:10px; align-items:center; }
            .report-nav b { font-weight:900; }
            .report-nav span, .report-nav a { color:var(--muted); font-size:13px; text-decoration:none; }
            .sheet {
              margin:18px 0; border:1px solid rgba(228,232,240,.9); border-radius:18px; background:var(--paper);
              box-shadow:var(--shadow); overflow:hidden;
            }
            .sheet.first { overflow:visible; }
            .pad { padding:26px; }
            .cover {
              position:relative; color:#fff; padding:40px 44px; border-radius:18px;
              background:
                linear-gradient(135deg,rgba(232,93,31,.98),rgba(45,111,224,.92) 48%,rgba(15,181,166,.92)),
                radial-gradient(circle at 85% 0%,rgba(255,255,255,.22),transparent 34%);
              overflow:hidden; box-shadow:0 24px 70px rgba(232,93,31,.22);
            }
            .cover::after {
              content:""; position:absolute; inset:0;
              background-image:linear-gradient(rgba(255,255,255,.12) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.12) 1px,transparent 1px);
              background-size:34px 34px; mask-image:linear-gradient(90deg,transparent,black 28%,black 72%,transparent);
            }
            .cover > * { position:relative; z-index:1; }
            .brand { font-size:13px; font-weight:800; letter-spacing:.08em; text-transform:uppercase; color:rgba(255,255,255,.78); }
            .cover h1 { margin:12px 0 8px; max-width:860px; font-size:42px; line-height:1.14; letter-spacing:0; }
            .csub { max-width:760px; color:rgba(255,255,255,.88); font-size:17px; font-weight:650; }
            .badges { display:flex; flex-wrap:wrap; gap:8px; margin-top:20px; }
            .badge {
              display:inline-flex; align-items:center; min-height:30px; padding:5px 12px; border-radius:999px;
              background:rgba(255,255,255,.16); border:1px solid rgba(255,255,255,.22); color:#fff;
              font-size:13px; font-weight:800;
            }
            .badge.grade { background:#fff; color:#B45309; }
            .badge.stamp { background:#111827; color:#fff; }
            .body { color:#344054; margin:12px 0; }
            .body.sm { font-size:13px; }
            .faint { color:#98A2B3; } .red { color:var(--red); } .green { color:var(--green); }
            .sec {
              display:flex; align-items:center; gap:10px; margin:2px 0 18px; padding-bottom:14px;
              border-bottom:1px solid var(--line); font-size:23px; line-height:1.25; letter-spacing:0; color:var(--ink);
            }
            .sec .n {
              display:inline-grid; place-items:center; width:30px; height:30px; flex:0 0 auto;
              border-radius:9px; color:#fff; background:linear-gradient(135deg,var(--orange),var(--blue));
              font-size:13px; font-weight:900;
            }
            .sec .hint, .hint { color:var(--muted); font-size:13px; font-weight:650; }
            .hero { width:100%; border-collapse:collapse; }
            .gaugecell { width:180px; vertical-align:middle; text-align:center; }
            .gauge { position:relative; width:150px; height:150px; margin:0 auto; }
            .gauge svg { width:150px; height:150px; filter:drop-shadow(0 12px 18px rgba(15,23,42,.08)); }
            .gctr { position:absolute; inset:0; text-align:center; }
            .gctr b { display:block; font-size:42px; line-height:134px; letter-spacing:-.02em; }
            .gctr span { display:block; margin-top:-40px; font-size:12px; color:var(--muted); font-weight:800; }
            .herobody { vertical-align:middle; padding-left:18px; }
            .kpis { width:100%; border-collapse:separate; border-spacing:10px 0; }
            .kpis + .twocol { margin-top:16px; }
            .kpi {
              width:33%; padding:14px 15px; border:1px solid var(--line); border-radius:14px;
              background:linear-gradient(180deg,#fff,#FAFBFD); vertical-align:top;
            }
            .klab { color:var(--muted); font-size:12px; font-weight:800; }
            .kval { margin-top:4px; font-size:25px; line-height:1.15; font-weight:950; letter-spacing:-.02em; }
            .kval small, .mval small { font-size:13px; color:var(--muted); font-weight:700; }
            .callout, .consult-comment {
              margin:16px 0 0; padding:15px 17px; border-radius:14px; border:1px solid rgba(232,93,31,.18);
              background:linear-gradient(180deg,#FFF7F3,#FFFFFF); color:#273142; font-size:14px; line-height:1.72;
            }
            .consult-comment::before { content:"AI 코멘트"; display:block; margin-bottom:5px; color:var(--orange); font-size:11px; font-weight:950; letter-spacing:.06em; }
            .callout.blue { border-color:rgba(45,111,224,.18); background:#F3F7FF; }
            .callout.green { border-color:rgba(26,143,76,.18); background:#F0FBF5; }
            .callout.amber { border-color:rgba(185,121,26,.18); background:#FFF8E8; }
            .twocol { width:100%; border-collapse:separate; border-spacing:0; }
            .twocol > tbody { display:block; width:100%; }
            .twocol > tbody > tr { display:flex; align-items:stretch; margin:0 -8px; }
            .twocol > tbody > tr > td { display:flex; flex-direction:column; width:50%; vertical-align:top; padding:0 8px; }
            .twocol > tbody > tr > td > .panel { flex:1; height:auto; }
            .colh { color:var(--muted); font-size:13px; font-weight:900; margin:4px 0 10px; }
            .risk {
              margin:10px 0; padding:13px 15px; border:1px solid #F2D6D6; border-left:4px solid var(--red);
              border-radius:13px; background:#fff;
            }
            .risktop .rt { font-weight:900; }
            .risktop .pill { float:right; font-size:12px; padding:3px 8px; border-radius:999px; font-weight:850; }
            .pill.hi { background:#FCEAEA; color:var(--red); } .pill.mid { background:#FBF1DD; color:var(--amber); }
            .pill.low { background:#EAF1FD; color:var(--blue); }
            .rd { clear:both; margin-top:7px; color:var(--muted); font-size:13px; }
            .act { width:100%; border-collapse:separate; border-spacing:0; margin:10px 0; border:1px solid var(--line); border-left:4px solid var(--orange); border-radius:13px; background:#fff; }
            .act td { vertical-align:top; }
            .act td.ano { width:44px; padding:13px 0 13px 13px; text-align:center; background:none; }
            .act td.ano span { display:inline-flex; align-items:center; justify-content:center; width:26px; height:26px; border-radius:999px; background:var(--orange); color:#fff; font-size:13px; font-weight:900; }
            .atx { padding:13px 8px 13px 4px; } .atx b { font-size:14px; line-height:1.45; } .atx p { margin:5px 0 0; font-size:13px; color:var(--muted); }
            .adur { width:64px; padding:15px 14px 0 0; text-align:right; color:var(--muted); font-size:12px; white-space:nowrap; vertical-align:top; }
            .ops-grid, .signal-layout { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
            .info-card, .panel {
              position:relative; padding:18px; border:1px solid var(--line); border-radius:16px;
              background:linear-gradient(180deg,#fff,#FAFBFD); overflow:hidden;
            }
            .info-card::before, .panel::before { content:""; position:absolute; top:0; left:0; right:0; height:3px; background:var(--accent,var(--orange)); }
            .accent-orange { --accent:var(--orange); } .accent-blue { --accent:var(--blue); } .accent-teal { --accent:var(--teal); }
            .info-card h3, .panel h3 { margin:0 0 14px; font-size:16px; line-height:1.25; letter-spacing:0; }
            .tile-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px; }
            .tile-grid.compact { grid-template-columns:repeat(3,minmax(0,1fr)); }
            .metric-tile {
              min-height:82px; padding:12px; border:1px solid #EDF0F5; border-radius:13px; background:#fff;
              display:flex; flex-direction:column; justify-content:space-between;
            }
            .metric-tile span { color:var(--muted); font-size:12px; font-weight:850; }
            .metric-tile b { font-size:18px; line-height:1.18; letter-spacing:-.01em; overflow-wrap:anywhere; }
            .metric-tile small { color:#98A2B3; font-size:11px; font-weight:750; }
            .info-list { display:grid; gap:8px; }
            .info-row { display:grid; grid-template-columns:92px minmax(0,1fr); gap:12px; padding:9px 0; border-bottom:1px solid #EEF1F5; }
            .info-row span { color:var(--muted); font-size:12px; font-weight:850; }
            .info-row b { font-size:13px; overflow-wrap:anywhere; }
            .facility-cloud { display:flex; flex-wrap:wrap; gap:8px; }
            .facility-chip { padding:9px 11px; border:1px solid #E7EEF3; border-radius:12px; background:#fff; min-width:calc(50% - 4px); }
            .facility-chip span { display:block; color:var(--muted); font-size:11px; font-weight:850; }
            .facility-chip b { font-size:13px; }
            .modern-table, table.cmp { width:100%; border-collapse:separate; border-spacing:0; overflow:hidden; border:1px solid var(--line); border-radius:14px; font-size:13px; }
            .modern-table th, table.cmp th { background:#F6F8FB; color:var(--muted); text-align:left; padding:10px 12px; font-size:12px; font-weight:900; }
            .modern-table td, table.cmp td { padding:10px 12px; border-top:1px solid #EDF0F5; }
            table.cmp td.r, table.cmp th.r { text-align:right; }
            table.cmp tr.best td { background:#FFF7F2; }
            .mode-strip { margin-top:12px; padding:10px 12px; border-radius:12px; background:#F0FAF8; color:#0B766C; font-weight:900; }
            .panel { margin-bottom:14px; }
            .axis { width:100%; border-collapse:collapse; margin-top:2px; }
            .axis td { font-size:12px; color:var(--muted); }
            .rev-compare {
              margin:12px 0 14px; padding:14px; border-radius:16px;
              background:linear-gradient(135deg,#FFF7F2 0%,#FFFFFF 54%,#F4F8FF 100%);
              box-shadow:inset 0 0 0 1px rgba(226,232,240,.84);
            }
            .rev-stats { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; margin-bottom:13px; }
            .rev-stat {
              min-width:0; padding:10px 11px; border-radius:13px; background:rgba(255,255,255,.92);
              box-shadow:inset 0 0 0 1px rgba(226,232,240,.8);
            }
            .rev-stat.accent { box-shadow:inset 0 0 0 1px rgba(232,93,31,.22), 0 10px 24px rgba(232,93,31,.08); }
            .rev-stat span { display:block; color:var(--muted); font-size:11px; line-height:1.1; font-weight:900; white-space:nowrap; }
            .rev-stat b { display:block; margin-top:4px; color:var(--ink); font-size:17px; line-height:1.18; font-weight:950; white-space:nowrap; letter-spacing:0; }
            .rev-stat b.green { color:var(--green); } .rev-stat b.red { color:var(--red); }
            .rev-bars { display:grid; gap:9px; }
            .rev-row { display:grid; grid-template-columns:54px minmax(0,1fr) 78px; gap:10px; align-items:center; }
            .rev-row span { color:var(--muted); font-size:12px; font-weight:900; white-space:nowrap; }
            .rev-row b { text-align:right; color:#344054; font-size:12px; font-weight:950; white-space:nowrap; }
            .rev-track { height:12px; border-radius:999px; background:#E8ECF2; overflow:hidden; }
            .rev-fill { height:100%; border-radius:999px; background:#A9B4C5; }
            .rev-row.selected span, .rev-row.selected b { color:var(--ink); }
            .rev-row.selected .rev-fill { background:linear-gradient(90deg,var(--orange),var(--blue)); }
            .bar { width:100%; border-collapse:collapse; margin:8px 0; }
            .bar .bnm { width:30%; padding-right:10px; color:var(--muted); font-size:13px; }
            .bar.hl .bnm { color:var(--ink); font-weight:900; }
            .bbg { height:18px; background:#EEF1F5; border-radius:999px; overflow:hidden; }
            .bfill { height:18px; border-radius:999px; }
            .bar .bvv { width:58px; text-align:right; font-size:13px; font-weight:900; }
            .kvs { width:100%; border-collapse:collapse; }
            .kvs .kvk { width:70px; color:var(--muted); font-size:13px; font-weight:850; padding:5px 0; }
            .kvs .kvv { font-size:13px; color:#344054; }
            .recmark, .tag { display:inline-flex; align-items:center; padding:3px 8px; border-radius:999px; font-size:12px; font-weight:900; white-space:nowrap; }
            .recmark { color:#fff; background:var(--orange); }
            .tag.g { background:#E9F7EF; color:var(--green); } .tag.r { background:#FCEAEA; color:var(--red); }
            .tag.a { background:#FBF1DD; color:var(--amber); } .tag.b { background:#EAF1FD; color:var(--blue); }
            .swslist { width:100%; border-collapse:collapse; margin-top:12px; }
            .swslist .swr { width:64px; vertical-align:top; color:var(--muted); font-size:13px; font-weight:900; padding-top:8px; }
            .swslist td { padding:9px 0; border-bottom:1px solid #EEF1F5; }
            .swt { margin-bottom:4px; font-weight:900; }
            .sw { margin:3px 0; color:#344054; font-size:13px; }
            .sw.g { color:var(--green); } .sw.w { color:var(--red); }
            .mkpi { margin-bottom:9px; padding:13px; border:1px solid var(--line); border-radius:13px; background:#fff; }
            .mkpi:last-child { margin-bottom:0; }
            .mval { margin-top:3px; font-size:20px; line-height:1.2; font-weight:950; }
            .disc { padding:18px 20px; border:1px dashed #CBD5E1; border-radius:16px; background:#FAFBFC; }
            .disc ul { margin:0; padding-left:18px; } .disc li { color:#4B5563; margin:5px 0; }
            /* v6.3 점수 분해 발산형 막대 */
            .xai { width:100%; border-collapse:collapse; margin:7px 0; }
            .xai .xlab { width:30%; padding-right:10px; color:var(--ink); font-size:13px; font-weight:700; }
            .xtrack { position:relative; height:16px; background:#EEF1F5; border-radius:999px; }
            .xmid { position:absolute; left:50%; top:-2px; bottom:-2px; width:2px; background:#C4CCD8; }
            .xfill { position:absolute; top:0; bottom:0; border-radius:999px; }
            .xai .xval { width:48px; text-align:right; font-size:12px; font-weight:900; }
            /* v6.3 점유 이력 타임라인 */
            .tlev { display:flex; align-items:center; gap:9px; padding:8px 0; border-bottom:1px dashed #E7EBF1; font-size:13px; }
            .tldot { width:10px; height:10px; border-radius:50%; flex:0 0 auto; }
            .tldot.c { background:var(--red); } .tldot.o { background:var(--green); }
            .tlnm { font-weight:700; color:var(--ink); } .tlnm small { color:var(--muted); font-weight:600; }
            .tlpill { font-size:11px; font-weight:850; padding:2px 8px; border-radius:999px; }
            .tlpill.c { background:#FCEAEA; color:var(--red); } .tlpill.o { background:#E9F7EF; color:var(--green); }
            .tlyr { margin-left:auto; color:var(--muted); font-size:12px; white-space:nowrap; }
            /* v6.4 비교 중심 */
            .prank { display:inline-block; background:var(--blue); color:#fff; font-size:11px; font-weight:900; padding:2px 9px; border-radius:999px; }
            .prank.sm { font-size:10px; padding:1px 7px; }
            .charrow { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; margin-top:8px; }
            .charcard { padding:14px; border:1px solid var(--line); border-radius:14px; background:linear-gradient(180deg,#fff,#FAFBFD); }
            .charcard b { display:block; margin:8px 0 4px; font-size:15px; line-height:1.3; }
            .charcard small { color:var(--muted); font-size:12px; font-weight:700; }
            .cmp3 { width:100%; border-collapse:separate; border-spacing:0; border:1px solid var(--line); border-radius:14px; overflow:hidden; font-size:14px; margin-top:18px; }
            .cmp3 th { background:#F6F8FB; color:var(--muted); padding:11px 14px; font-size:12px; font-weight:900; text-align:center; }
            .cmp3 th:first-child { text-align:left; }
            .cmp3 td { padding:11px 14px; border-top:1px solid #EDF0F5; text-align:center; }
            .cmp3 td.cmpk { text-align:left; color:var(--muted); font-weight:800; font-size:13px; }
            .cmp3 td.win { background:#FFF7F2; color:var(--orange); font-weight:950; }
            .ptag { background:#E9F7EF; color:var(--green); font-size:11px; font-weight:900; padding:2px 9px; border-radius:999px; }
            .charcard .ptscore.big { display:block; margin-top:9px; font-size:13px; font-weight:950; color:var(--orange); }
            /* 매물 전환 탭 — 주소만 */
            .ptabs { display:flex; flex-wrap:wrap; gap:12px; margin-top:8px; }
            .ptab { flex:1 1 0; min-width:170px; display:flex; align-items:center; justify-content:center; padding:15px 18px; border:1.5px solid var(--line); border-radius:14px; background:linear-gradient(180deg,#fff,#FAFBFD); cursor:pointer; transition:transform .15s, box-shadow .15s, border-color .15s; }
            .ptab:hover { border-color:#F0A877; transform:translateY(-1px); box-shadow:0 8px 20px rgba(15,23,42,.07); }
            .ptab.on { border-color:var(--orange); background:linear-gradient(180deg,#FFF7F2,#fff); box-shadow:0 6px 18px rgba(232,93,31,.16); }
            .ptab .ptaddr { font-size:15px; font-weight:900; color:var(--muted); line-height:1.3; text-align:center; }
            .ptab.on .ptaddr { color:var(--ink); }
            .pppanel { display:block; }
            /* 점수 분해 3열 */
            .ccols { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
            .ccol { padding:14px; border:1px solid var(--line); border-radius:14px; background:linear-gradient(180deg,#fff,#FAFBFD); }
            .ccol-h { font-size:13px; font-weight:900; color:var(--ink); margin-bottom:10px; }
            .ccol-h .prank { background:var(--orange); }
            /* 매물별 총평 단락 */
            .verdict { padding:16px 0; border-bottom:1px solid var(--line); }
            .verdict:last-child { border-bottom:none; }
            .verdict-h { display:flex; align-items:center; gap:8px; flex-wrap:wrap; margin-bottom:8px; }
            .verdict-h b { font-size:16px; }
            .verdict-h .vsc { font-size:13px; color:var(--muted); font-weight:800; }
            .fitline { display:flex; gap:8px; margin:7px 0; font-size:13.5px; line-height:1.55; }
            .fitline span { flex:0 0 auto; font-weight:900; color:var(--blue); }
            .guide { display:grid; grid-template-columns:minmax(150px,210px) 24px minmax(90px,130px) minmax(0,1fr); align-items:center; gap:10px; padding:11px 0; border-bottom:1px solid #EEF1F5; font-size:14px; }
            .gaxis { font-weight:850; } .garrow { color:var(--muted); text-align:center; }
            .gpick { color:var(--orange); font-weight:950; } .greason { color:var(--muted); font-size:13px; }
            @media (max-width: 900px) {
              .charrow, .ccols { grid-template-columns:1fr; }
              .cmp3 { font-size:13px; }
              .guide { grid-template-columns:1fr; gap:3px; } .garrow { display:none; }
              .report-shell { width:min(100% - 24px, 1180px); padding-top:12px; }
              .cover { padding:30px 24px; } .cover h1 { font-size:30px; }
              .ops-grid, .signal-layout { grid-template-columns:1fr; }
              .tile-grid.compact { grid-template-columns:repeat(2,minmax(0,1fr)); }
              .rev-stats { grid-template-columns:1fr; }
              .twocol > tbody, .twocol > tbody > tr { display:block; margin:0; }
              .twocol > tbody > tr > td { display:block; width:100%; padding:0; }
              .gaugecell, .herobody { display:block; width:100%; padding:0; }
              .kpis, .kpis tbody, .kpis tr, .kpis td { display:block; width:100%; }
              .kpi { margin:8px 0; }
              .report-nav { position:static; }
            }
            @media print {
              body { background:#fff; }
              .report-shell { width:100%; padding:0; }
              .report-nav { display:none; }
              .sheet { box-shadow:none; break-inside:avoid; page-break-inside:avoid; }
            }
        """.trimIndent()
    }
}
