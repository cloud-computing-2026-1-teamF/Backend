package com.sanggwonai.api.report.service

import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.abs
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
        sectionSummary(this, report, input)
        sectionProperty(this, report, input)
        sectionLocation(this, report, input)
        sectionFit(this, report, input)
        sectionTop3(this, report, input)
        sectionMarketSignals(this, report, input)
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
    private fun sectionSummary(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
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

        // body
        sb.append("<div class=\"pad\">")
        secHead(sb, "1", "임원 요약", null)
        // hero: gauge + kpis + callout
        sb.append("<table class=\"hero\"><tr><td class=\"gaugecell\">")
        sb.append("<div class=\"gauge\">").append(gaugeSvg(score))
        sb.append("<div class=\"gctr\"><b style=\"color:").append(scoreColor(score)).append("\">").append(score)
            .append("</b><span>생존 점수</span></div></div>")
        sb.append("</td><td class=\"herobody\">")
        // KPI cards from input
        sb.append("<table class=\"kpis\"><tr>")
        kpi(sb, "하루 유동인구", commaOrDash(top1["foot"]), "명")
        kpi(sb, "동네 월평균 매출", wonShort(dbl(top1["rev"])), "")
        kpi(sb, "500m 경쟁점", commaOrDash(top1["comp"]), "곳")
        sb.append("</tr></table>")
        str(ch1["한_줄_결론"]).ifBlank { str(ch1["의사결정_권장"]) }.ifBlank { null }
            ?.let { sb.append("<div class=\"callout\">").append(esc(it)).append("</div>") }
        sb.append("</td></tr></table>")
        // summary body
        str(ch1["요약_본문"]).ifBlank { null }?.let { sb.append("<p class=\"body\">").append(esc(it)).append("</p>") }

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
                    sb.append("<table class=\"act\"><tr><td class=\"ano\">").append((intOf(am["우선순위"]) ?: (i + 1)))
                        .append("</td><td class=\"atx\"><b>").append(esc(str(am["할_일"]))).append("</b>")
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
        metricTile(sb, "층 / 총층", listOf(str(property["floor"]).ifBlank { str(top1["floor"]) }, str(property["totalFloors"]))
            .filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }, "동선 확인")
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
        if (sMin != null && sMax != null && sMax > sMin && sSel != null) {
            val posPct = (((sSel - sMin) / (sMax - sMin)) * 100).coerceIn(0.0, 100.0).roundToInt()
            sb.append("<div class=\"range\"><div class=\"rtrack\"></div><div class=\"rme\" style=\"left:").append(posPct).append("%\"></div>")
            sb.append("<div class=\"rlab\" style=\"left:0%\">").append(wonShort(sMin)).append("</div>")
            sMed?.let { sb.append("<div class=\"rlab\" style=\"left:50%;transform:translateX(-50%)\">중앙 ").append(wonShort(it)).append("</div>") }
            sb.append("<div class=\"rlab\" style=\"left:100%;transform:translateX(-100%)\">").append(wonShort(sMax)).append("</div></div>")
        }
        str(path(ch4, "section_4_3_estimated_revenue", "매출_환경_해석")).ifBlank {
            "내 매물 추정 ${wonShort(sSel)} 수준입니다."
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
            .append("<th class=\"r\">보증금</th><th class=\"r\">점수</th><th class=\"r\">월순이익</th><th>회수 평가</th></tr></thead><tbody>")
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
            sb.append("<td class=\"r\"><b>").append(intOf(tm["score"]) ?: 0).append("</b></td>")
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
    private fun sectionMarketSignals(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val top1 = asList(path(input, "saved_analysis", "top3")).firstOrNull().let { asMap(it) } ?: return
        val ch6 = asMap(report["chapter_6_market_signal_diagnosis"]) ?: emptyMap<Any?, Any?>()
        val pop = asMap(top1["population"]) ?: emptyMap<Any?, Any?>()
        val commercial = asMap(top1["commercial"]) ?: emptyMap<Any?, Any?>()

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "6", "상권 수요·경쟁 시그널", "공실 상세 지표 기반")

        sb.append("<div class=\"signal-layout\">")
        sb.append("<section class=\"info-card accent-blue\"><h3>인구 구성</h3><div class=\"tile-grid compact\">")
        metricTile(sb, "분기 유동", peopleText(dbl(pop["floatingQuarterly"])), "반경 상권 수요")
        metricTile(sb, "분기 상주", peopleText(dbl(pop["residentQuarterly"])), "생활권 수요")
        metricTile(sb, "분기 직장", peopleText(dbl(pop["workerQuarterly"])), "평일 점심·퇴근")
        metricTile(sb, "2030 비율", percentText(dbl(pop["age2030PopulationRatio"])), "젊은 수요")
        metricTile(sb, "여성 비율", percentText(dbl(pop["femalePopulationRatio"])), "타깃 성향")
        metricTile(sb, "직장/유동", percentText(dbl(pop["workerToFloatingRatio"])), "오피스 의존도")
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

        sb.append("<section class=\"info-card accent-teal\"><h3>매출과 리스크</h3><div class=\"tile-grid compact\">")
        metricTile(sb, "가게당 평균 매출", dbl(commercial["averageSalesPerStore"])?.let { wonRaw(it) } ?: wonShort(dbl(top1["rev"])), "월 추정")
        metricTile(sb, "개업률", percentText(dbl(commercial["openingRate"])), "진입 활력")
        metricTile(sb, "폐업률", percentText(dbl(commercial["closureRate"])), "안정성")
        metricTile(sb, "저녁 매출", percentText(dbl(commercial["eveningSalesRatio"])), "저녁 수요")
        metricTile(sb, "심야 매출", percentText(dbl(commercial["lateNightSalesRatio"])), "야간 수요")
        metricTile(sb, "음식 지출", wonRaw(dbl(commercial["foodSpending"])), "상권 소비")
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
            miniKpi(sb, "손익분기 — 피크 필요 판매량", "시간당 약 $it", "", INK)
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
    private fun sectionAppendix(sb: StringBuilder, report: Map<String, Any?>) {
        val ch7 = asMap(report["chapter_7_appendix"]) ?: emptyMap<Any?, Any?>()
        sb.append("<div class=\"sheet\" id=\"appendix\"><div class=\"pad\">")
        secHead(sb, "9", "부록 — 본 보고서의 한계", null)
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
        sb.append("<p class=\"body sm faint\" style=\"margin-top:12px\">분석 모델: 생존율 예측 모델 · 공공데이터(인허가·상권분석·소상공인·공시지가) 기반</p>")
        sb.append("<p class=\"body sm\" style=\"margin-top:16px\"><b>투자 회수 계산 방법</b></p>")
        sb.append("<div class=\"disc\"><ul>")
        sb.append("<li><b>초기투자비</b> = 보증금 + 권리금 + 중개수수료 + (월세+관리비) × 3개월 운전자금</li>")
        sb.append("<li><b>월 순이익</b> = 동종 업종 점포당 평균 추정매출 × 영업이익률 − (월세+관리비)</li>")
        sb.append("<li><b>투자 회수기간</b> = 초기투자비 ÷ 월 순이익</li>")
        sb.append("<li><b>평균 추정매출</b>: 서울시 상권분석서비스 추정매출 데이터 기반 (상권 → 상권배후지 → 행정동 순으로 우선 적용)</li>")
        sb.append("<li><b>영업이익률</b>: 업종별 고정값 적용 (예: 일식 13%, 한식 12%, 카페 17% 등)</li>")
        sb.append("<li><b>손익분기 피크 필요 판매량</b> = 손익분기 월매출 ÷ 영업일수(26일) × 피크 매출 비중(45%) ÷ 객단가</li>")
        sb.append("</ul></div>")
        sb.append("</div></div>")
    }

    // ───────────────────────── 차트/컴포넌트 ─────────────────────
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
            .twocol { width:100%; border-collapse:collapse; }
            .twocol > tbody > tr > td { width:50%; vertical-align:top; padding:0 8px; }
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
            .act { width:100%; border-collapse:collapse; border-bottom:1px solid var(--line); }
            .act td.ano { background:var(--orange); color:#fff; border-radius:999px; text-align:center; font-size:13px; font-weight:900; width:24px; height:24px; }
            .atx { padding:8px 10px; } .atx b { font-size:14px; } .atx p { margin:4px 0 0; font-size:13px; color:var(--muted); }
            .adur { width:64px; padding-top:8px; text-align:right; color:var(--muted); font-size:12px; white-space:nowrap; vertical-align:top; }
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
            .range { position:relative; height:42px; margin:20px 8px 10px; }
            .rtrack { position:absolute; top:15px; left:0; right:0; height:8px; border-radius:999px; background:#EAECF1; }
            .rme { position:absolute; top:7px; width:4px; height:24px; background:var(--orange); border-radius:999px; box-shadow:0 0 0 5px rgba(232,93,31,.12); }
            .rlab { position:absolute; top:27px; font-size:12px; color:var(--muted); }
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
            .mval { margin-top:3px; font-size:20px; line-height:1.2; font-weight:950; }
            .disc { padding:18px 20px; border:1px dashed #CBD5E1; border-radius:16px; background:#FAFBFC; }
            .disc ul { margin:0; padding-left:18px; } .disc li { color:#4B5563; margin:5px 0; }
            @media (max-width: 900px) {
              .report-shell { width:min(100% - 24px, 1180px); padding-top:12px; }
              .cover { padding:30px 24px; } .cover h1 { font-size:30px; }
              .ops-grid, .signal-layout { grid-template-columns:1fr; }
              .tile-grid.compact { grid-template-columns:repeat(2,minmax(0,1fr)); }
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
