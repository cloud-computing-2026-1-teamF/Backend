package com.sanggwonai.api.report.service

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 보고서 렌더러 — 확장판 디자인(report-developed.html) 기반.
 *
 * 설계: 숫자·차트는 input_data(DB 사실)에서 직접, 문장(해석)은 AI report(output_schema)에서.
 *  - 게이지·시간대 라인차트: inline SVG (openhtmltopdf-svg-support / Batik)
 *  - 막대·KPI·표·콜아웃·리스크·액션: CSS (flexbox 회피 — table/inline-block 레이아웃)
 *  - 한글: resources/fonts/NotoSansKR-Regular.ttf 임베드(위계는 크기·색으로).
 *
 * 7섹션: 임원요약 / 입지특성 / 업종적합도 / 추천Top3 / 투자회수(opt) / 리뷰(opt) / 부록.
 */
@Component
class ReportPdfRenderer {

    fun render(report: Map<String, Any?>, input: Map<String, Any?>): ByteArray {
        val os = ByteArrayOutputStream()
        val builder = PdfRendererBuilder()
            .useSVGDrawer(BatikSVGDrawer())
            .withHtmlContent(buildHtml(report, input), null)
            .toStream(os)
        javaClass.getResourceAsStream("/fonts/NotoSansKR-Regular.ttf")?.readBytes()?.let { bytes ->
            builder.useFont({ ByteArrayInputStream(bytes) }, "Noto Sans KR")
        }
        builder.run()
        return os.toByteArray()
    }

    // ───────────────────────── HTML 조립 ─────────────────────────
    private fun buildHtml(report: Map<String, Any?>, input: Map<String, Any?>): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><style>").append(CSS).append("</style></head><body>")
        sectionSummary(this, report, input)
        sectionLocation(this, report, input)
        sectionFit(this, report, input)
        sectionTop3(this, report, input)
        if (path(input, "section_06_investment_payback") != null && active(report["chapter_9_investment_payback"]))
            sectionPayback(this, report, input)
        if (path(input, "section_05_review_insight") != null && active(report["chapter_8_review_insight"]))
            sectionReview(this, report, input)
        sectionAppendix(this, report)
        append("</body></html>")
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

    // ── §2 입지 특성 ────────────────────────────────────────────
    private fun sectionLocation(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val sa = asMap(path(input, "saved_analysis")) ?: emptyMap<Any?, Any?>()
        val top1 = asList(sa["top3"]).firstOrNull().let { asMap(it) } ?: emptyMap<Any?, Any?>()
        val ch4 = asMap(report["chapter_4_location_characteristics"]) ?: emptyMap<Any?, Any?>()
        val mref = asMap(path(input, "vacancy_metric_reference"))

        @Suppress("UNCHECKED_CAST")
        val hourly = (top1["footHourly"] as? List<*>)?.mapNotNull { intOf(it) } ?: emptyList()

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "2", "입지 특성", null)

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

    // ── §3 업종 적합도 (9개) ─────────────────────────────────────
    private fun sectionFit(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val scores = asMap(path(input, "selected_vacancy_extra", "nine_category_scores")) ?: emptyMap<Any?, Any?>()
        val selected = str(path(input, "saved_analysis", "category"))
        val ch5 = asMap(report["chapter_5_business_fit_analysis"]) ?: emptyMap<Any?, Any?>()

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "3", "업종 적합도 — 9개 업종 비교", null)
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

    // ── §4 추천 매물 Top 3 ──────────────────────────────────────
    private fun sectionTop3(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val top3 = asList(path(input, "saved_analysis", "top3"))
        val ch3 = asMap(report["chapter_3_top3_property_analysis"]) ?: emptyMap<Any?, Any?>()
        val payProps = asList(path(input, "section_06_investment_payback", "properties"))
        val recRank = intOf(path(ch3, "최종_권장_매물", "rank"))
        val details = asList(ch3["매물_상세_분석"])

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "4", "추천 매물 Top 3", null)
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

    // ── §5 투자 회수 ────────────────────────────────────────────
    private fun sectionPayback(sb: StringBuilder, report: Map<String, Any?>, input: Map<String, Any?>) {
        val pay = asMap(path(input, "section_06_investment_payback")) ?: return
        val ch9 = asMap(report["chapter_9_investment_payback"]) ?: emptyMap<Any?, Any?>()
        val props = asList(pay["properties"])

        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "5", "투자 회수 분석", null)

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
        sb.append("<p class=\"body sm faint\">* 초기투자비에 인테리어·설비 capex 미포함</p></div>")
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

    // ── §6 리뷰 인사이트 (옵션) ─────────────────────────────────
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
        secHead(sb, "6", "주변 리뷰 인사이트", str(path(ch8, "데이터_범위")))
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

    // ── §7 부록 ────────────────────────────────────────────────
    private fun sectionAppendix(sb: StringBuilder, report: Map<String, Any?>) {
        val ch7 = asMap(report["chapter_7_appendix"]) ?: emptyMap<Any?, Any?>()
        sb.append("<div class=\"sheet\"><div class=\"pad\">")
        secHead(sb, "7", "부록 — 본 보고서의 한계", null)
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
    private fun wonShort(man: Double?): String {
        val v = man ?: return "-"
        return if (abs(v) >= 10000) ("₩" + "%.1f".format(v / 10000.0).removeSuffix(".0") + "억")
        else "₩" + "%,d".format(v.toLong()) + "만"
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
    private fun sevLabel(s: String): String = when { s.contains("높") -> "심각 높음"; s.contains("중") -> "중간"; else -> "낮음" }
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
            @page { size: A4; margin: 14mm 13mm 16mm;
              @bottom-left { content: "상권을 부탁해 · AI 입지 분석"; font-family:'Noto Sans KR'; font-size:8pt; color:#9AA3B2; }
              @bottom-right { content: counter(page) " / " counter(pages); font-family:'Noto Sans KR'; font-size:8pt; color:#9AA3B2; } }
            * { box-sizing: border-box; }
            body { font-family:'Noto Sans KR', sans-serif; color:#1B2330; font-size:10pt; line-height:1.5; margin:0; }
            .sheet { page-break-inside: auto; }
            .sec { page-break-after: avoid; margin-top: 22px; }
            .cover, .panel, .callout, .risk, .hero, .twocol, .disc, .mkpi,
              table.act, table.bar, table.kpis, table.cmp tr, .swslist tr { page-break-inside: avoid; }
            .pad { padding: 4px 2px; }
            .body { font-size:10pt; line-height:1.6; color:#374050; margin:8px 0; }
            .body.sm { font-size:9pt; line-height:1.5; }
            .faint { color:#8A94A6; } .red { color:#D33A3A; } .green { color:#1A8F4C; }
            .cover { background:#52260F; color:#fff; padding:22px 24px; border-radius:10px; border-bottom:3px solid #E85D1F; }
            .brand { font-size:9pt; letter-spacing:.5px; color:#F4D9C8; }
            .cover h1 { font-size:21pt; margin:8px 0 4px; }
            .csub { color:#F0D8C8; font-size:11pt; }
            .badges { margin-top:12px; }
            .badge { display:inline-block; font-size:8.5pt; padding:3px 9px; border-radius:14px;
              background:rgba(255,255,255,.18); color:#fff; margin:0 5px 5px 0; }
            .badge.grade { background:#fff; color:#B9791A; font-weight:bold; }
            .badge.stamp { background:#D33A3A; color:#fff; }
            .sec { font-size:12pt; color:#E85D1F; margin:26px 0 12px; padding-bottom:6px; border-bottom:1px solid #F0D6C8; }
            .sec .n { display:inline-block; width:20px; height:20px; line-height:20px; text-align:center; border-radius:5px;
              background:#E85D1F; color:#fff; font-size:9pt; margin-right:6px; }
            .sec .hint { color:#8A94A6; font-size:8.5pt; font-weight:normal; margin-left:6px; }
            .hint { color:#8A94A6; font-size:8.5pt; font-weight:normal; }
            .hero { width:100%; border-collapse:collapse; }
            .gaugecell { width:140px; vertical-align:middle; text-align:center; }
            .gauge { position:relative; width:130px; height:130px; margin:0 auto; }
            .gctr { position:absolute; left:0; top:0; width:130px; height:130px; text-align:center; }
            .gctr b { display:block; font-size:30pt; line-height:118px; }
            .gctr span { display:block; margin-top:-30px; font-size:8.5pt; color:#5A6678; }
            .herobody { vertical-align:middle; padding-left:12px; }
            .kpis { width:100%; border-collapse:separate; border-spacing:7px 0; }
            .kpi { border:1px solid #E7EAF0; border-radius:9px; padding:9px 11px; width:33%; vertical-align:top; }
            .klab { font-size:8.5pt; color:#5A6678; }
            .kval { font-size:16pt; font-weight:bold; margin-top:2px; }
            .kval small, .mval small { font-size:9pt; color:#8A94A6; font-weight:normal; }
            .callout { border-left:3px solid #E85D1F; background:#FCF5F1; border-radius:0 8px 8px 0;
              padding:10px 13px; margin:12px 0; font-size:9.5pt; line-height:1.55; }
            .callout.blue { border-color:#2D6FE0; background:#EAF1FD; }
            .callout.green { border-color:#1A8F4C; background:#E9F7EF; }
            .callout.amber { border-color:#B9791A; background:#FBF1DD; }
            .twocol { width:100%; border-collapse:collapse; }
            .twocol > tbody > tr > td { width:50%; vertical-align:top; padding:0 8px; }
            .colh { font-size:9pt; font-weight:bold; margin:4px 0 8px; }
            .colh.warn { color:#8A94A6; } .colh.ok { color:#8A94A6; }
            .risk { border:1px solid #F2D6D6; border-left:3px solid #D33A3A; border-radius:0 8px 8px 0;
              padding:8px 11px; margin:7px 0; }
            .risktop .rt { font-weight:bold; font-size:9.5pt; }
            .risktop .pill { float:right; font-size:8pt; padding:2px 7px; border-radius:10px; }
            .pill.hi { background:#FCEAEA; color:#D33A3A; } .pill.mid { background:#FBF1DD; color:#B9791A; }
            .pill.low { background:#EAF1FD; color:#2D6FE0; }
            .rd { font-size:8.5pt; color:#5A6678; margin-top:6px; line-height:1.5; clear:both; }
            .act { width:100%; border-collapse:collapse; border-bottom:1px solid #EEF0F4; }
            .act .ano { width:22px; vertical-align:top; }
            .act .ano { color:#fff; }
            .act td.ano { background:#E85D1F; border-radius:11px; text-align:center; font-size:9pt; font-weight:bold;
              width:20px; height:20px; }
            .atx { padding:6px 8px; } .atx b { font-size:9.5pt; } .atx p { margin:3px 0 0; font-size:8.5pt; color:#5A6678; }
            .adur { vertical-align:top; text-align:right; font-size:8pt; color:#8A94A6; white-space:nowrap; width:54px; padding-top:6px; }
            .panel { border:1px solid #E7EAF0; border-radius:10px; padding:12px; margin-bottom:12px; }
            .panel h3 { margin:0 0 10px; font-size:10pt; }
            .axis { width:100%; border-collapse:collapse; margin-top:2px; }
            .axis td { font-size:8pt; color:#8A94A6; }
            .range { position:relative; height:34px; margin:16px 4px 8px; }
            .rtrack { position:absolute; top:13px; left:0; right:0; height:7px; border-radius:4px; background:#EAECF1; }
            .rme { position:absolute; top:6px; width:3px; height:21px; background:#E85D1F; border-radius:2px; }
            .rlab { position:absolute; top:22px; font-size:8pt; color:#8A94A6; }
            .bar { width:100%; border-collapse:collapse; margin:5px 0; }
            .bar .bnm { width:34%; font-size:9pt; color:#5A6678; padding-right:8px; }
            .bar.hl .bnm { color:#1B2330; font-weight:bold; }
            .bbg { height:16px; background:#EEF0F4; border-radius:5px; overflow:hidden; }
            .bfill { height:16px; border-radius:5px; }
            .bar .bvv { width:46px; text-align:right; font-size:9pt; font-weight:bold; }
            .kvs { width:100%; border-collapse:collapse; }
            .kvs .kvk { width:60px; font-size:8.5pt; color:#8A94A6; padding:3px 0; }
            .kvs .kvv { font-size:9pt; color:#374050; }
            table.cmp { width:100%; border-collapse:collapse; font-size:9pt; margin-top:6px; }
            table.cmp th { background:#F7F8FB; color:#5A6678; text-align:left; padding:7px 8px; border-bottom:1px solid #E7EAF0; font-size:8.5pt; }
            table.cmp td { padding:7px 8px; border-bottom:1px solid #EEF0F4; }
            table.cmp td.r, table.cmp th.r { text-align:right; }
            table.cmp tr.best td { background:#FCF5F1; }
            .recmark { font-size:7.5pt; color:#fff; background:#E85D1F; padding:1px 6px; border-radius:9px; }
            .tag { font-size:8pt; padding:2px 7px; border-radius:10px; white-space:nowrap; }
            .tag.g { background:#E9F7EF; color:#1A8F4C; } .tag.r { background:#FCEAEA; color:#D33A3A; }
            .tag.a { background:#FBF1DD; color:#B9791A; } .tag.b { background:#EAF1FD; color:#2D6FE0; }
            .swslist { width:100%; border-collapse:collapse; margin-top:10px; }
            .swslist .swr { width:46px; vertical-align:top; font-size:8.5pt; color:#8A94A6; font-weight:bold; padding-top:6px; }
            .swslist td { padding:6px 0; border-bottom:1px solid #EEF0F4; }
            .swt { font-weight:bold; font-size:9pt; margin-bottom:3px; }
            .sw { font-size:8.5pt; line-height:1.5; margin:2px 0; }
            .sw.g { color:#1A8F4C; } .sw.w { color:#D33A3A; }
            .mkpi { border:1px solid #E7EAF0; border-radius:8px; padding:8px 10px; margin-bottom:7px; }
            .mval { font-size:13pt; font-weight:bold; margin-top:2px; }
            .disc { border:1px dashed #D6DAE3; border-radius:10px; background:#FAFBFC; padding:12px 14px; }
            .disc ul { margin:0; padding-left:16px; } .disc li { font-size:9pt; color:#5A6678; line-height:1.7; margin:2px 0; }
        """.trimIndent()
    }
}
