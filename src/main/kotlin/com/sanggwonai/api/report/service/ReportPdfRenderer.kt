package com.sanggwonai.api.report.service

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs

@Component
class ReportPdfRenderer {

    fun render(report: Map<String, Any?>): ByteArray {
        val os = ByteArrayOutputStream()
        val builder = PdfRendererBuilder().withHtmlContent(buildHtml(report), null).toStream(os)
        javaClass.getResourceAsStream("/fonts/NotoSansKR-Regular.ttf")?.readBytes()?.let { bytes ->
            builder.useFont({ ByteArrayInputStream(bytes) }, "Noto Sans KR")
        }
        builder.run()
        return os.toByteArray()
    }

    // ── HTML 최상위 조립 ─────────────────────────────────────────────────────────

    internal fun buildHtml(report: Map<String, Any?>): String = buildString {
        append("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"utf-8\"/><style>")
        append(CSS)
        append("</style></head><body>")

        val meta      = m(report["report_metadata"])
        val ch1       = m(report["chapter_1_executive_summary"])
        val ch2       = m(report["chapter_2_commercial_type"])
        val ch3       = m(report["chapter_3_top3_property_analysis"])
        val ch4       = m(report["chapter_4_location_characteristics"])
        val ch5       = m(report["chapter_5_business_fit_analysis"])
        val chClose   = m(report["chapter_closure_risk"])
        val ch7       = m(report["chapter_7_appendix"])
        val ch8       = m(report["chapter_8_review_insight"])?.takeIf { it["활성_여부"] != false }
        val ch9       = m(report["chapter_9_investment_payback"])?.takeIf { it["활성_여부"] != false }
        val chSwot    = m(report["chapter_swot"])
        val chComp    = m(report["chapter_competition_niche"])
        val chConcept = m(report["chapter_concept_menu"])
        val chOp      = m(report["chapter_operation"])

        val score = (m(ch5?.get("선택_카테고리_평가"))?.get("선택_카테고리_점수") as? Number)?.toInt() ?: 0

        var pg = 1; var sec = 0
        fun ns() = (++sec).toString()

        append(sheet1(meta, ch1, score, pg++, ns()))
        if (ch2 != null)      append(sheetCommercialType(ch2, pg++, ns()))
        if (ch4 != null)      append(sheet4(ch4, pg++, ns()))
        if (ch5 != null)      append(sheet5(ch5, pg++, ns()))
        if (chClose != null)  append(sheetClosureRisk(chClose, pg++, ns()))
        if (ch3 != null)      append(sheet3(ch3, pg++, ns()))
        if (ch9 != null)      append(sheet9(ch9, pg++, ns()))
        if (chSwot != null)   append(sheetSwot(chSwot, pg++, ns()))
        if (ch8 != null)      append(sheet8(ch8, pg++, ns()))
        if (chComp != null)   append(sheetCompetitionNiche(chComp, pg++, ns()))
        if (chConcept != null) append(sheetConceptMenu(chConcept, pg++, ns()))
        if (chOp != null)     append(sheetOperation(chOp, pg++, ns()))
        if (ch7 != null)      append(sheet7(ch7, pg, ns()))

        append("</body></html>")
    }

    // ── Sheet 1: 표지 + 임원 요약 ────────────────────────────────────────────────

    private fun sheet1(meta: Map<*, *>?, ch1: Map<*, *>?, score: Int, pg: Int, sec: String): String = buildString {
        val title    = s(meta?.get("보고서_제목"))
        val subtitle = s(meta?.get("보고서_부제"))
        val grade    = s(meta?.get("보고서_등급"))
        val stamp    = s(meta?.get("추천_도장"))
        val date     = s(meta?.get("발급일시"))
        val category = s(meta?.get("업종"))
        val area     = s(meta?.get("분석_반경"))

        val headline = s(ch1?.get("한_줄_결론"))
        val body     = s(ch1?.get("요약_본문"))
        val decision = s(ch1?.get("의사결정_권장"))
        val kpis     = l(ch1?.get("핵심_수치_3선"))
        val risks    = l(ch1?.get("리스크_요인"))
        val actions  = l(ch1?.get("액션_아이템"))

        append("""<div class="sheet">""")
        append("""<div class="cover">""")
        append("""<div class="brand">상권을 부탁해 · AI 입지 분석 보고서</div>""")
        if (title.isNotBlank())    append("""<h1>${e(title)}</h1>""")
        if (subtitle.isNotBlank()) append("""<div class="sub">${e(subtitle)}</div>""")
        append("""<div class="badges">""")
        if (grade.isNotBlank())    append("""<span class="badge grade">등급 ${e(grade)}</span>""")
        if (stamp.isNotBlank())    append("""<span class="badge warn">${e(stamp)}</span>""")
        if (category.isNotBlank()) append("""<span class="badge">${e(category)}</span>""")
        if (area.isNotBlank())     append("""<span class="badge">${e(area)}</span>""")
        if (date.isNotBlank())     append("""<span class="badge">${e(date)}</span>""")
        append("""</div></div>""")

        append("""<div class="pad">""")
        append(secHead(sec, "임원 요약"))

        // 게이지 + KPI 행
        append("""<div style="display:flex;align-items:center;margin-bottom:14px">""")
        append(gauge(score))
        append("""<div style="flex:1;margin-left:14px">""")
        if (kpis.isNotEmpty()) {
            append("""<div style="display:flex;gap:10px;margin-bottom:10px">""")
            for (kpi in kpis.take(3)) {
                val km = m(kpi) ?: continue
                val ctx = s(km["맥락"])
                append("""<div class="kpi">""")
                append("""<div class="kpi-lab">${e(s(km["지표명"]))}</div>""")
                append("""<div class="kpi-val">${e(s(km["값"]))}</div>""")
                if (ctx.isNotBlank()) append("""<div class="kpi-ctx">${e(ctx)}</div>""")
                append("""</div>""")
            }
            append("""</div>""")
        }
        if (headline.isNotBlank()) append(callout(headline, "orange"))
        append("""</div></div>""")

        if (body.isNotBlank())     append("""<p class="body">${e(body)}</p>""")
        if (decision.isNotBlank()) append(callout(decision, "blue"))

        if (risks.isNotEmpty() || actions.isNotEmpty()) {
            append("""<div style="display:flex;gap:14px;margin-top:10px">""")
            if (risks.isNotEmpty()) {
                append("""<div style="flex:1">""")
                append("""<div class="col-label">⚠ 핵심 리스크</div>""")
                for (r in risks) {
                    val rm  = m(r) ?: continue
                    val sev = s(rm["심각도"])
                    val sc  = if (sev == "높음") "pill-hi" else if (sev == "중간") "pill-mid" else "pill-lo"
                    append("""<div class="risk-card">""")
                    append("""<div style="display:flex;justify-content:space-between;align-items:flex-start">""")
                    append("""<div class="risk-title">${e(s(rm["리스크"]))}</div>""")
                    if (sev.isNotBlank()) append("""<span class="pill $sc">${e(sev)}</span>""")
                    append("""</div>""")
                    val detail = s(rm["대응_방안"])
                    if (detail.isNotBlank()) append("""<div class="risk-detail">${e(detail)}</div>""")
                    append("""</div>""")
                }
                append("""</div>""")
            }
            if (actions.isNotEmpty()) {
                append("""<div style="flex:1">""")
                append("""<div class="col-label">✓ 액션 아이템</div>""")
                for ((idx, a) in actions.withIndex()) {
                    val am  = m(a) ?: continue
                    val no  = s(am["우선순위"]).ifBlank { (idx + 1).toString() }
                    val dur = s(am["예상_소요시간"])
                    append("""<div class="act">""")
                    append("""<span class="act-no">${e(no)}</span>""")
                    append("""<div class="act-txt"><b>${e(s(am["할_일"]))}</b>""")
                    val reason = s(am["이유"])
                    if (reason.isNotBlank()) append("""<p>${e(reason)}</p>""")
                    append("""</div>""")
                    if (dur.isNotBlank()) append("""<span class="act-dur">${e(dur)}</span>""")
                    append("""</div>""")
                }
                append("""</div>""")
            }
            append("""</div>""")
        }

        append("""</div>""")
        append(foot("상권을 부탁해 · AI 입지 분석", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet 2: 상권 유형 진단 (신규) ──────────────────────────────────────────

    private fun sheetCommercialType(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro      = s(ch["chapter_intro"])
        val summary    = s(ch["상권_유형_요약"])
        val rows       = l(ch["상권_유형_표"])
        val conclusion = s(ch["진단_결론"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "상권 유형 진단"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")
        if (summary.isNotBlank()) append(callout(summary, "orange"))

        if (rows.isNotEmpty()) {
            append("""<table class="tbl">""")
            append("""<thead><tr><th>상권 유형</th><th>특징</th><th>적합한 모델</th><th>본 상권 부합도</th></tr></thead><tbody>""")
            for (r in rows) {
                val rm     = m(r) ?: continue
                val isMatch = rm["해당"] == true
                val fit    = s(rm["부합도"])
                val fitCls = when {
                    fit.contains("매우") -> "tag-g"
                    fit.contains("높음") -> "tag-g"
                    fit.contains("낮음") -> "tag-r"
                    else -> "tag-a"
                }
                val rowCls = if (isMatch) """ class="best-row"""" else ""
                append("""<tr$rowCls>""")
                append("""<td>${e(s(rm["유형"]))}${if (isMatch) """<span class="recmark">해당</span>""" else ""}</td>""")
                append("""<td>${e(s(rm["특징"]))}</td>""")
                append("""<td>${e(s(rm["적합_모델"]))}</td>""")
                append("""<td><span class="tag $fitCls">${e(fit)}</span></td>""")
                append("""</tr>""")
            }
            append("""</tbody></table>""")
        }

        if (conclusion.isNotBlank()) append(callout(conclusion, "blue"))
        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet 3(표시): 입지 특성 ────────────────────────────────────────────────

    private fun sheet4(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro = s(ch["chapter_intro"])
        val s41   = m(ch["section_4_1_floating_population"])
        val s42   = m(ch["section_4_2_competition"])
        val s43   = m(ch["section_4_3_estimated_revenue"])
        val s44   = m(ch["section_4_4_accessibility"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "입지 특성"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")

        // 시간대별 유동인구 패널
        if (s41 != null) {
            val kn        = m(s41["핵심_수치"])
            val hourlyRaw = l(s41["시간대별_유동인구"])
            val peakTime  = s(kn?.get("Peak_시간대"))
            val interp    = s(s41["시간대_패턴_해석"])

            append("""<div class="panel" style="margin-bottom:14px">""")
            append("""<h3 class="panel-h3">시간대별 유동인구""")
            if (peakTime.isNotBlank()) append(""" <span class="panel-sub">— 피크 ${e(peakTime)}</span>""")
            append("""</h3>""")

            if (hourlyRaw.isNotEmpty()) {
                val hourly = hourlyRaw.mapNotNull { n(it)?.toInt() }
                append(lineChart(hourly))
                append("""<div style="display:flex;justify-content:space-between;font-size:8.5pt;color:#8A94A6;margin-top:2px">""")
                append("""<span>0시</span><span>6시</span><span>12시</span><span>18시</span><span>24시</span>""")
                append("""</div>""")
            }

            if (kn != null) {
                append("""<div style="display:flex;gap:10px;margin-top:10px">""")
                val daily = s(kn["하루_평균_유동인구"])
                val avg   = s(kn["동일_업종_평균_대비"])
                if (daily.isNotBlank()) append(kpiSm("하루 평균 유동인구", daily))
                if (avg.isNotBlank())   append(kpiSm("동일 업종 평균 대비", avg))
                if (peakTime.isNotBlank()) append(kpiSm("피크 시간대", peakTime))
                append("""</div>""")
            }
            if (interp.isNotBlank()) append("""<p class="body" style="font-size:10pt;margin-top:8px;color:#5A6678">${e(interp)}</p>""")
            append("""</div>""")
        }

        // 경쟁 + 매출 2열
        append("""<div style="display:flex;gap:14px;margin-bottom:14px">""")

        if (s42 != null) {
            val kn      = m(s42["핵심_수치"])
            val compStr = s(kn?.get("반경_500m_동일_업종"))
            val avgStr  = s(kn?.get("동일_업종_평균_대비"))
            val compVal = compStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            val avgVal  = avgStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            val maxVal  = maxOf(compVal, avgVal, 1)
            val interp  = s(s42["경쟁_상황_해석"])

            append("""<div class="panel" style="flex:1">""")
            append("""<h3 class="panel-h3">경쟁 강도 (500m 동일 업종)</h3>""")
            if (compVal > 0) append(barRow("내 상권",   compVal, maxVal, true))
            if (avgVal  > 0) append(barRow("업종 평균", avgVal,  maxVal, false))
            if (interp.isNotBlank()) append("""<p class="body" style="font-size:10pt;margin-top:8px">${e(interp)}</p>""")
            append("""</div>""")
        }

        if (s43 != null) {
            val kn    = m(s43["핵심_수치"])
            val avgKw = n(kn?.get("동네_평균_추정_매출_만원"))?.toInt()
            val pct   = s(kn?.get("매출_백분위"))
            val dist  = m(s43["매출_분포_시각화"])

            append("""<div class="panel" style="flex:1">""")
            append("""<h3 class="panel-h3">동네 매출 분포 내 위치</h3>""")

            if (dist != null) {
                val minV  = n(dist["최솟값_만원"])?.toInt() ?: 0
                val midV  = n(dist["중앙값_만원"])?.toInt() ?: 0
                val maxV  = n(dist["최댓값_만원"])?.toInt() ?: 1
                val myV   = n(dist["내_매물_만원"])?.toInt() ?: (avgKw ?: 0)
                val myPct = if (maxV > minV) ((myV - minV).toDouble() / (maxV - minV) * 100).toInt().coerceIn(2, 96) else 70
                append(rangeBar(minV, midV, maxV, myPct))
            }

            if (avgKw != null) {
                append("""<p class="body" style="font-size:10pt;margin-top:10px">내 매물 추정 <b>${avgKw}만원</b>""")
                if (pct.isNotBlank()) append(""" · <b>${e(pct)}</b>""")
                append("""</p>""")
            }

            val sc = m(s43["예상_매출_시나리오"])
            if (sc != null) {
                val bear = n(sc["보수적_월매출_만원"])?.toInt()
                val base = n(sc["기본_월매출_만원"])?.toInt()
                val bull = n(sc["낙관적_월매출_만원"])?.toInt()
                val parts = listOfNotNull(
                    bear?.let { "보수 ${it}만" },
                    base?.let { "기본 ${it}만" },
                    bull?.let { "낙관 ${it}만" }
                )
                if (parts.isNotEmpty()) append("""<p class="body" style="font-size:9.5pt;color:#5A6678">${parts.joinToString(" · ")}</p>""")
            }
            val interp = s(s43["매출_환경_해석"])
            if (interp.isNotBlank()) append("""<p class="body" style="font-size:10pt">${e(interp)}</p>""")
            append("""</div>""")
        }

        append("""</div>""")

        // 접근성
        if (s44 != null) {
            val subway = m(s44["지하철_접근성"])
            val bus    = m(s44["버스_접근성"])
            val park   = m(s44["주차_접근성"])
            val total  = s(s44["접근성_종합_평가"])
            append("""<div class="panel">""")
            append("""<h3 class="panel-h3">교통 및 접근성</h3>""")
            fun accRow(label: String, info: String, grade: String) {
                if (info.isBlank()) return
                val gc = when (grade) { "매우 좋음", "좋음" -> "tag-g"; "나쁨" -> "tag-r"; "보통" -> "tag-a"; else -> "" }
                append("""<p class="body" style="font-size:10pt;margin:4px 0"><b>${e(label)}:</b> ${e(info)}""")
                if (gc.isNotBlank()) append(""" <span class="tag $gc">${e(grade)}</span>""")
                append("""</p>""")
            }
            accRow("지하철", s(subway?.get("정보")), s(subway?.get("등급")))
            accRow("버스",   s(bus?.get("정보")),    s(bus?.get("등급")))
            accRow("주차",   s(park?.get("정보")),   "")
            if (total.isNotBlank()) append(callout(total, "blue"))
            append("""</div>""")
        }

        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 업종 적합도 ───────────────────────────────────────────────────────

    private fun sheet5(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro   = s(ch["chapter_intro"])
        val evalMap = m(ch["선택_카테고리_평가"])
        val rawData = l(m(ch["9개_카테고리_점수표"])?.get("데이터"))
        val best3   = s(ch["best_3_카테고리"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "업종 적합도 — 9개 업종 비교"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")

        if (evalMap != null) {
            val chosen = s(evalMap["선택_카테고리"])
            val cscore = n(evalMap["선택_카테고리_점수"])?.toInt()
            val rank   = n(evalMap["9개_카테고리_중_순위"])?.toInt()
            val interp = s(evalMap["해석"])
            if (chosen.isNotBlank()) {
                append("""<p class="body"><b>${e(chosen)}</b>""")
                if (cscore != null) append(" — 점수 <b>$cscore</b>")
                if (rank   != null) append(", 9개 중 <b>${rank}위</b>")
                append("""</p>""")
            }
            if (interp.isNotBlank()) append("""<p class="body">${e(interp)}</p>""")
        }

        if (rawData.isNotEmpty()) {
            val maxScore = rawData.mapNotNull { m(it) }.mapNotNull { n(it["점수"]) }.maxOfOrNull { it.toDouble() }?.toInt() ?: 100
            val sorted   = rawData.sortedByDescending { m(it)?.let { cm -> n(cm["점수"])?.toDouble() } ?: 0.0 }
            append("""<div style="margin-top:8px">""")
            for (cat in sorted) {
                val cm    = m(cat) ?: continue
                val name  = s(cm["카테고리"])
                val score = n(cm["점수"])?.toInt() ?: 0
                val isSel = cm["선택_여부"] == true
                append(barRow(name, score, maxScore.coerceAtLeast(1), isSel))
            }
            append("""</div>""")
        }

        if (best3.isNotBlank()) append(callout("Best 3: $best3", "amber"))

        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 폐업 데이터 리스크 진단 (신규) ───────────────────────────────────

    private fun sheetClosureRisk(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro      = s(ch["chapter_intro"])
        val metrics    = l(ch["지표_설명_표"])
        val matrix     = l(ch["해석_매트릭스"])
        val conclusion = s(ch["진단_결론"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "점포·폐업 데이터 리스크 진단"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")

        if (metrics.isNotEmpty()) {
            append("""<table class="tbl">""")
            append("""<thead><tr><th>지표</th><th>해석</th></tr></thead><tbody>""")
            for (r in metrics) {
                val rm = m(r) ?: continue
                append("""<tr><td>${e(s(rm["지표"]))}</td><td>${e(s(rm["해석"]))}</td></tr>""")
            }
            append("""</tbody></table>""")
        }

        if (matrix.isNotEmpty()) {
            append("""<h3 class="sub-h3" style="margin-top:20px">점포 수 × 폐업 수 해석 매트릭스</h3>""")
            append("""<table class="tbl">""")
            append("""<thead><tr><th>데이터 결과</th><th>해석</th></tr></thead><tbody>""")
            for (r in matrix) {
                val rm = m(r) ?: continue
                val cl = when (s(rm["등급"])) { "g" -> "tag-g"; "r" -> "tag-r"; "a" -> "tag-a"; else -> "tag-b" }
                append("""<tr><td>${e(s(rm["결과"]))}</td><td><span class="tag $cl">${e(s(rm["해석"]))}</span></td></tr>""")
            }
            append("""</tbody></table>""")
        }

        if (conclusion.isNotBlank()) append(callout(conclusion, "amber"))
        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 추천 매물 Top 3 ───────────────────────────────────────────────────

    private fun sheet3(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro     = s(ch["chapter_intro"])
        val props     = l(ch["매물_상세_분석"])
        val colProps  = l(m(ch["Top3_비교_표"])?.get("열_매물"))
        val best      = m(ch["최종_권장_매물"])
        val bestRank  = n(best?.get("rank"))?.toInt() ?: 0
        val warnNote  = s(ch["주의_사항"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "추천 매물 Top 3"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")

        if (colProps.isNotEmpty()) {
            append("""<table class="tbl">""")
            append("""<thead><tr><th>매물</th><th>층</th><th class="r">월세(만)</th><th class="r">보증금(만)</th><th class="r">면적(㎡)</th><th class="r">생존점수</th><th class="r">월순이익</th><th>회수</th></tr></thead><tbody>""")
            for ((idx, cp) in colProps.withIndex()) {
                val cpm    = m(cp) ?: continue
                val rank   = n(cpm["rank"])?.toInt() ?: (idx + 1)
                val isBest = rank == bestRank
                val detail = props.mapNotNull { m(it) }.find { n(it["rank"])?.toInt() == rank }
                val addr   = s(cpm["주소_간략"])
                val floor  = s(m(detail?.get("기본_정보"))?.get("층"))
                val rent   = n(cpm["월세_만원"])?.toInt()
                val dep    = n(cpm["보증금_만원"])?.toInt()
                val area   = n(cpm["면적_제곱미터"])?.toInt()
                val score  = n(cpm["점수"])?.toInt()
                val profit = n(detail?.let { m(it)?.get("월순이익_만원") })?.toInt()
                val payback = s(detail?.let { m(it)?.get("투자회수평가") })
                val profitColor = if ((profit ?: 0) >= 0) "#1A8F4C" else "#D33A3A"
                val profitLabel = if (profit != null) { if (profit >= 0) "+${profit}만" else "${profit}만" } else "-"
                val payCls = if ((profit ?: 0) < 0) "tag-r" else "tag-a"
                val rowCls = if (isBest) """ class="best-row"""" else ""
                append("""<tr$rowCls>""")
                append("""<td>${e(addr)}${if (isBest) """<span class="recmark">추천</span>""" else ""}</td>""")
                append("""<td>${e(floor)}</td>""")
                append("""<td class="r">${if (rent != null) "₩${rent}만" else "-"}</td>""")
                append("""<td class="r">${if (dep  != null) "₩${dep}만"  else "-"}</td>""")
                append("""<td class="r">${if (area != null) "${area}㎡"   else "-"}</td>""")
                append("""<td class="r"><b>${score ?: "-"}</b></td>""")
                append("""<td class="r" style="font-weight:700;color:${profitColor}">${e(profitLabel)}</td>""")
                if (payback.isNotBlank()) append("""<td><span class="tag $payCls">${e(payback)}</span></td>""")
                else append("""<td></td>""")
                append("""</tr>""")
            }
            append("""</tbody></table>""")
        }

        if (props.isNotEmpty()) {
            append("""<h3 class="sub-h3" style="margin-top:18px">매물 유형 분류</h3>""")
            append("""<table class="tbl">""")
            append("""<thead><tr><th>매물</th><th>특징</th><th>위험요인</th><th>분류</th></tr></thead><tbody>""")
            for (p in props) {
                val pm     = m(p) ?: continue
                val rank   = n(pm["rank"])?.toInt() ?: 0
                val isBest = rank == bestRank
                val typeTag = s(pm["유형_태그"])
                val typeCls = when (s(pm["유형_태그_색"])) { "g" -> "tag-g"; "r" -> "tag-r"; "a" -> "tag-a"; else -> "tag-b" }
                val rowCls  = if (isBest) """ class="best-row"""" else ""
                append("""<tr$rowCls>""")
                append("""<td>${e(s(pm["주소"]))}${if (isBest) """<span class="recmark">추천</span>""" else ""}</td>""")
                append("""<td>${e(s(pm["강점"]))}</td>""")
                append("""<td>${e(s(pm["약점"]))}</td>""")
                append("""<td>${if (typeTag.isNotBlank()) """<span class="tag $typeCls">${e(typeTag)}</span>""" else ""}</td>""")
                append("""</tr>""")
            }
            append("""</tbody></table>""")
        }

        if (warnNote.isNotBlank())                    append(callout(warnNote, "amber"))
        val bestReason = s(best?.get("권장_근거"))
        if (bestReason.isNotBlank())                  append(callout("최종 권장: $bestReason", "orange"))

        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 투자 회수 분석 ────────────────────────────────────────────────────

    private fun sheet9(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro   = s(ch["chapter_intro"])
        val kn      = m(ch["핵심_수치"])
        val interp  = s(ch["회수_해석"])
        val top3    = l(m(ch["Top3_투자회수_비교"])?.get("데이터"))
        val bepNote = s(ch["bep_action"])
        val caution = s(ch["주의"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "투자 회수 분석"))
        if (intro.isNotBlank()) append("""<p class="body">${e(intro)}</p>""")

        append("""<div style="display:flex;gap:14px;margin-bottom:14px">""")

        if (top3.isNotEmpty()) {
            val maxAbs = top3.mapNotNull { m(it) }
                .mapNotNull { n(it["월순이익_만원"]) }
                .maxOfOrNull { abs(it.toDouble()) }?.toInt() ?: 100

            append("""<div class="panel" style="flex:1">""")
            append("""<h3 class="panel-h3">매물별 월 순이익</h3>""")
            for (t in top3) {
                val tm      = m(t) ?: continue
                val addr    = s(tm["주소_간략"])
                val profit  = n(tm["월순이익_만원"])?.toInt() ?: 0
                val pct     = (abs(profit) * 100.0 / maxAbs.coerceAtLeast(1)).toInt().coerceIn(4, 100)
                val color   = if (profit >= 0) "#1A8F4C" else "#D33A3A"
                val trackBg = if (profit >= 0) "#E9F7EF" else "#FCEAEA"
                val label   = if (profit >= 0) "+${profit}만" else "${profit}만"
                val eval    = s(tm["투자회수평가"])
                append("""<div style="display:flex;align-items:center;gap:8px;margin:7px 0">""")
                append("""<div style="width:88px;font-size:10pt;color:#5A6678">${e(addr)}</div>""")
                append("""<div style="flex:1;height:18px;background:${trackBg};border-radius:5px;overflow:hidden">""")
                append("""<div style="width:${pct}%;height:100%;background:${color};border-radius:5px"></div></div>""")
                append("""<div style="width:56px;text-align:right;font-size:10pt;font-weight:700;color:${color}">${e(label)}</div>""")
                if (eval.isNotBlank()) {
                    val cl = if (profit < 0) "tag-r" else "tag-a"
                    append("""<span class="tag $cl">${e(eval)}</span>""")
                }
                append("""</div>""")
            }
            append("""<p class="body" style="font-size:9.5pt;color:#8A94A6;margin-top:8px">* 초기투자비에 인테리어·설비 capex 미포함</p>""")
            append("""</div>""")
        }

        if (kn != null) {
            val investment = n(kn["초기투자비_만원"])?.toInt()
            val netProfit  = n(kn["월순이익_만원"])?.toInt()
            val roiMonths  = n(kn["투자회수기간_개월"])?.toInt()
            val evalStr    = s(kn["투자회수평가"])
            val bep        = s(kn["손익분기_피크_판매량"])

            append("""<div class="panel" style="flex:1">""")
            append("""<h3 class="panel-h3">1순위 투자 회수 요약</h3>""")
            if (investment != null) append(kpiSm("초기투자비", fmtManwon(investment)))
            if (netProfit != null) {
                val c     = if (netProfit >= 0) "#1A8F4C" else "#D33A3A"
                val lbl   = if (netProfit >= 0) "+${netProfit}만원" else "${netProfit}만원"
                val ec    = if (netProfit < 0) "tag-r" else "tag-a"
                append("""<div class="kpi-sm" style="margin-bottom:8px">""")
                append("""<div class="kpi-lab">투자 회수 평가</div>""")
                append("""<div class="kpi-val" style="font-size:13pt;color:${c}">${e(lbl)}""")
                if (evalStr.isNotBlank()) append(""" <span class="tag $ec">${e(evalStr)}</span>""")
                append("""</div></div>""")
            } else if (evalStr.isNotBlank()) {
                append(kpiSm("투자 회수 평가", evalStr))
            }
            if (bep.isNotBlank())       append(kpiSm("손익분기 — 피크 필요 판매량", bep))
            if (roiMonths != null)      append(kpiSm("투자회수기간", "${roiMonths}개월"))
            append("""</div>""")
        }

        append("""</div>""")
        if (interp.isNotBlank())  append("""<p class="body">${e(interp)}</p>""")
        if (bepNote.isNotBlank()) append(callout(bepNote, "blue"))
        if (caution.isNotBlank()) append("""<p class="body" style="color:#5A6678;font-size:10pt"><b>주의:</b> ${e(caution)}</p>""")

        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: SWOT 분석 (신규) ──────────────────────────────────────────────────

    private fun sheetSwot(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val strength    = s(ch["강점"])
        val weakness    = s(ch["약점"])
        val opportunity = s(ch["기회"])
        val threat      = s(ch["위협"])
        val conclusion  = s(ch["종합_결론"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "SWOT 분석"))

        // CSS grid 미지원 → table 2x2
        append("""<table style="width:100%;border-collapse:separate;border-spacing:8px;margin-top:8px">""")
        append("""<tr>""")
        append(swotCell("S", "강점 Strength",    strength,    "#E9F7EF", "#1A8F4C"))
        append(swotCell("W", "약점 Weakness",    weakness,    "#FCEAEA", "#D33A3A"))
        append("""</tr><tr>""")
        append(swotCell("O", "기회 Opportunity", opportunity, "#EAF1FD", "#2D6FE0"))
        append(swotCell("T", "위협 Threat",      threat,      "#FBF1DD", "#B9791A"))
        append("""</tr></table>""")

        if (conclusion.isNotBlank()) append(callout(conclusion, "orange"))

        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 리뷰 인사이트 ─────────────────────────────────────────────────────

    private fun sheet8(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val range    = s(ch["데이터_범위"])
        val tags     = l(ch["수요_태그_TOP"])
        val popTags  = l(ch["인기집_공통_태그"])
        val diff     = s(ch["차별화_기회"])
        val totalTxt = s(ch["리뷰_종합_해석"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "주변 리뷰 인사이트"))
        if (range.isNotBlank()) append("""<p class="body" style="color:#5A6678;font-size:10pt;margin-top:-4px">${e(range)}</p>""")

        append("""<div style="display:flex;gap:14px;margin-top:10px">""")

        if (tags.isNotEmpty()) {
            append("""<div class="panel" style="flex:1">""")
            append("""<h3 class="panel-h3">동네 수요 태그 TOP 5</h3>""")
            for (t in tags) {
                val tm       = m(t) ?: continue
                val tag      = s(tm["태그"])
                val str      = s(tm["강도"])
                val ratioStr = s(tm["비율"])
                val pct      = ratioStr.replace(Regex("[^0-9]"), "").toIntOrNull()
                    ?: when (str) { "상" -> 85; "중" -> 60; else -> 35 }
                append(barRow(tag, pct, 100, str == "상"))
            }
            append("""</div>""")
        }

        append("""<div class="panel" style="flex:1">""")
        if (popTags.isNotEmpty()) {
            append("""<h3 class="panel-h3">차별화 기회 <span class="panel-sub">— 잘되는 집엔 강한데 동네 평균엔 약함</span></h3>""")
            append("""<div style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px">""")
            for (t in popTags) {
                val tm    = m(t) ?: continue
                val tag   = s(tm["태그"])
                val delta = s(tm["차이"])
                if (tag.isNotBlank()) {
                    append("""<span class="dtag">${e(tag)}""")
                    if (delta.isNotBlank()) append(""" <span style="font-size:9pt;color:#1A8F4C;font-weight:800">${e(delta)}</span>""")
                    append("""</span>""")
                }
            }
            append("""</div>""")
        }
        if (diff.isNotBlank()) append(callout(diff, "blue"))
        append("""</div>""")

        append("""</div>""")
        if (totalTxt.isNotBlank()) append("""<p class="body" style="margin-top:12px">${e(totalTxt)}</p>""")

        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 경쟁 매장 분석 & 틈새시장 (신규) ─────────────────────────────────

    private fun sheetCompetitionNiche(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro      = s(ch["chapter_intro"])
        val compRows   = l(ch["경쟁_매장_표"])
        val nicheRows  = l(ch["공백_분석_표"])
        val conclusion = s(ch["종합_결론"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "주변 경쟁 매장 분석 &amp; 틈새시장"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")

        if (compRows.isNotEmpty()) {
            append("""<table class="tbl">""")
            append("""<thead><tr><th>경쟁 매장</th><th>강점</th><th>고객 평가 포인트</th><th>약점·한계</th></tr></thead><tbody>""")
            for (r in compRows) {
                val rm = m(r) ?: continue
                append("""<tr><td>${e(s(rm["매장명"]))}</td><td>${e(s(rm["강점"]))}</td><td>${e(s(rm["고객평가"]))}</td><td>${e(s(rm["약점"]))}</td></tr>""")
            }
            append("""</tbody></table>""")
        }

        if (nicheRows.isNotEmpty()) {
            append("""<h3 class="sub-h3" style="margin-top:20px">발견된 공백 → 제안 콘셉트</h3>""")
            append("""<table class="tbl">""")
            append("""<thead><tr><th>발견된 공백</th><th>제안 콘셉트</th></tr></thead><tbody>""")
            for (r in nicheRows) {
                val rm = m(r) ?: continue
                append("""<tr><td>${e(s(rm["발견된_공백"]))}</td><td>${e(s(rm["제안_콘셉트"]))}</td></tr>""")
            }
            append("""</tbody></table>""")
        }

        if (conclusion.isNotBlank()) append(callout(conclusion, "orange"))
        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 추천 콘셉트 & 메뉴 전략 (신규) ───────────────────────────────────

    private fun sheetConceptMenu(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val core       = s(ch["핵심_callout"])
        val dirRows    = l(ch["창업방향_표"])
        val lunchRows  = l(ch["점심_메뉴_표"])
        val dinnerRows = l(ch["저녁_메뉴_표"])
        val tips       = l(ch["운영_팁"])
        val footnote   = s(ch["주석"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "추천 창업 콘셉트 &amp; 메뉴 전략"))
        if (core.isNotBlank()) append(callout(core, "green"))

        if (dirRows.isNotEmpty()) {
            append("""<table class="tbl" style="margin-top:8px">""")
            append("""<thead><tr><th>창업 방향</th><th>설명</th><th>적합도</th></tr></thead><tbody>""")
            for (r in dirRows) {
                val rm    = m(r) ?: continue
                val isRec = rm["추천"] == true
                val fit   = s(rm["적합도"])
                val fitCls = when { fit.contains("높음") -> "tag-g"; fit.contains("낮음") -> "tag-r"; else -> "tag-a" }
                val rowCls = if (isRec) """ class="best-row"""" else ""
                append("""<tr$rowCls>""")
                append("""<td>${e(s(rm["방향"]))}${if (isRec) """<span class="recmark">추천</span>""" else ""}</td>""")
                append("""<td>${e(s(rm["설명"]))}</td>""")
                append("""<td><span class="tag $fitCls">${e(fit)}</span></td>""")
                append("""</tr>""")
            }
            append("""</tbody></table>""")
        }

        fun menuTable(title: String, subTitle: String, rows: List<*>) {
            if (rows.isEmpty()) return
            append("""<h3 class="sub-h3" style="margin-top:18px">${e(title)} <span class="panel-sub">${e(subTitle)}</span></h3>""")
            append("""<table class="tbl">""")
            append("""<thead><tr><th>메뉴</th><th class="r">가격대</th><th class="r">조리</th><th>특징</th></tr></thead><tbody>""")
            for (r in rows) {
                val rm = m(r) ?: continue
                append("""<tr><td>${e(s(rm["메뉴"]))}</td><td class="r">${e(s(rm["가격대"]))}</td><td class="r">${e(s(rm["조리시간"]))}</td><td>${e(s(rm["특징"]))}</td></tr>""")
            }
            append("""</tbody></table>""")
        }
        menuTable("점심 타깃 메뉴", "— 빠른 조리·회전·1인 식사 중심", lunchRows)
        menuTable("저녁 타깃 메뉴", "— 식사 + 가벼운 술·모임 수요", dinnerRows)

        // 운영 팁 2x2 table (CSS grid 미지원 대체)
        if (tips.isNotEmpty()) {
            append("""<table style="width:100%;border-collapse:separate;border-spacing:8px;margin-top:14px">""")
            val chunked = tips.chunked(2)
            for (row in chunked) {
                append("""<tr>""")
                for (tip in row) {
                    val tm = m(tip) ?: continue
                    append("""<td style="border:1px solid #E7EAF0;border-radius:9px;padding:10px 12px;vertical-align:top;width:50%">""")
                    append("""<div style="font-size:9.5pt;font-weight:800;color:#E85D1F;margin-bottom:4px">${e(s(tm["키"]))}</div>""")
                    append("""<div style="font-size:10pt;color:#5A6678;line-height:1.55">${e(s(tm["내용"]))}</div>""")
                    append("""</td>""")
                }
                if (row.size < 2) append("""<td style="width:50%"></td>""")
                append("""</tr>""")
            }
            append("""</table>""")
        }

        if (footnote.isNotBlank()) append("""<p class="body" style="color:#8A94A6;font-size:9pt;margin-top:10px">* ${e(footnote)}</p>""")
        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 운영 전략 (신규) ──────────────────────────────────────────────────

    private fun sheetOperation(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val intro      = s(ch["chapter_intro"])
        val sizeCards  = l(ch["매장_규모_카드"])
        val midNote    = s(ch["중간_callout"])
        val hallRows   = l(ch["홀vs배달_표"])
        val conclusion = s(ch["결론_callout"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "운영 전략 — 매장 규모 &amp; 홀·배달"))
        if (intro.isNotBlank()) append("""<p class="body" style="margin-top:-4px">${e(intro)}</p>""")

        if (sizeCards.isNotEmpty()) {
            append("""<div style="display:flex;gap:12px;margin-top:8px">""")
            for (card in sizeCards) {
                val cm    = m(card) ?: continue
                val pros  = l(cm["장점"])
                val cons  = l(cm["단점"])
                val strat = s(cm["전략"])
                append("""<div class="opcard">""")
                append("""<h4 style="margin:0;font-size:13pt">${e(s(cm["이름"]))}</h4>""")
                append("""<div style="font-size:9.5pt;color:#8A94A6;font-weight:700;margin-top:2px">${e(s(cm["크기"]))}</div>""")
                append("""<div style="height:1px;background:#E7EAF0;margin:10px 0"></div>""")
                if (pros.isNotEmpty()) {
                    append("""<div style="font-size:9.5pt;font-weight:800;color:#1A8F4C;margin-top:4px">장점</div>""")
                    append("""<ul style="margin:3px 0 0;padding-left:15px">""")
                    for (p in pros) append("""<li style="font-size:10pt;color:#5A6678;line-height:1.5;margin:1px 0">${e(s(p))}</li>""")
                    append("""</ul>""")
                }
                if (cons.isNotEmpty()) {
                    append("""<div style="font-size:9.5pt;font-weight:800;color:#D33A3A;margin-top:6px">단점</div>""")
                    append("""<ul style="margin:3px 0 0;padding-left:15px">""")
                    for (c in cons) append("""<li style="font-size:10pt;color:#5A6678;line-height:1.5;margin:1px 0">${e(s(c))}</li>""")
                    append("""</ul>""")
                }
                if (strat.isNotBlank()) append("""<div style="font-size:10pt;color:#374050;font-weight:700;margin-top:10px;line-height:1.5">→ ${e(strat)}</div>""")
                append("""</div>""")
            }
            append("""</div>""")
        }

        if (midNote.isNotBlank()) append(callout(midNote, "orange"))

        if (hallRows.isNotEmpty()) {
            append("""<h3 class="sub-h3" style="margin-top:18px">홀 장사 vs 배달 장사</h3>""")
            append("""<table class="tbl">""")
            append("""<thead><tr><th>구분</th><th>홀 중심 매장</th><th>배달 중심 매장</th></tr></thead><tbody>""")
            for (r in hallRows) {
                val rm = m(r) ?: continue
                append("""<tr><td><b>${e(s(rm["구분"]))}</b></td><td>${e(s(rm["홀"]))}</td><td>${e(s(rm["배달"]))}</td></tr>""")
            }
            append("""</tbody></table>""")
        }

        if (conclusion.isNotBlank()) append(callout(conclusion, "blue"))
        append("""</div>""")
        append(foot("상권을 부탁해", pg.toString()))
        append("""</div>""")
    }

    // ── Sheet: 부록 ──────────────────────────────────────────────────────────────

    private fun sheet7(ch: Map<*, *>, pg: Int, sec: String): String = buildString {
        val limits    = s(ch["본_보고서의_한계"])
        val modelNote = s(ch["분석_모델_정보"])

        append("""<div class="sheet"><div class="pad">""")
        append(secHead(sec, "부록 — 본 보고서의 한계"))
        if (limits.isNotBlank()) {
            append("""<div class="disc"><ul>""")
            limits.split("\n").filter { it.isNotBlank() }.forEach { append("""<li>${e(it)}</li>""") }
            append("""</ul></div>""")
        }
        if (modelNote.isNotBlank()) append("""<p class="body" style="color:#8A94A6;font-size:9.5pt;margin-top:12px">${e(modelNote)}</p>""")
        append("""</div>""")
        append(foot("상권을 부탁해 · AI 입지 분석 보고서", pg.toString()))
        append("""</div>""")
    }

    // ── 공통 컴포넌트 ─────────────────────────────────────────────────────────────

    private fun gauge(score: Int): String {
        val r      = 62.0
        val circ   = 2 * PI * r
        val offset = circ * (100 - score.coerceIn(0, 100)) / 100.0
        return buildString {
            append("""<div style="position:relative;width:150px;height:150px;flex:0 0 auto">""")
            append("""<svg width="150" height="150" viewBox="0 0 150 150">""")
            append("""<circle cx="75" cy="75" r="$r" fill="none" stroke="#EEF0F4" stroke-width="14"/>""")
            append("""<circle cx="75" cy="75" r="$r" fill="none" stroke="#E85D1F" stroke-width="14" """)
            append("""stroke-linecap="round" """)
            append("""stroke-dasharray="${String.format("%.1f", circ)}" """)
            append("""stroke-dashoffset="${String.format("%.1f", offset)}" """)
            append("""transform="rotate(-90 75 75)"/>""")
            append("""</svg>""")
            append("""<div style="position:absolute;top:0;left:0;right:0;bottom:0;display:flex;flex-direction:column;align-items:center;justify-content:center">""")
            append("""<b style="font-size:34px;line-height:1;color:#1B2330">$score</b>""")
            append("""<span style="font-size:9.5pt;color:#5A6678">생존 점수</span>""")
            append("""</div></div>""")
        }
    }

    private fun lineChart(hourly: List<Int>, svgW: Int = 740, svgH: Int = 120): String {
        if (hourly.isEmpty()) return ""
        val maxVal = hourly.maxOrNull()?.coerceAtLeast(1) ?: 1
        val n      = hourly.size
        fun px(i: Int) = (i.toDouble() / (n - 1) * svgW).toInt()
        fun py(v: Int) = svgH - (v.toDouble() / maxVal * (svgH - 8)).toInt()
        val pts    = hourly.mapIndexed { i, v -> "${px(i)},${py(v)}" }.joinToString(" ")
        val fill   = "0,$svgH $pts ${svgW},$svgH"
        val maxIdx = hourly.indexOf(hourly.max())
        return buildString {
            append("""<svg width="100%" height="${svgH}px" viewBox="0 0 $svgW $svgH" preserveAspectRatio="none" style="display:block">""")
            append("""<polyline fill="rgba(232,93,31,.10)" stroke="none" points="$fill"/>""")
            append("""<polyline fill="none" stroke="#E85D1F" stroke-width="2.5" points="$pts"/>""")
            append("""<circle cx="${px(maxIdx)}" cy="${py(maxVal)}" r="4" fill="#E85D1F"/>""")
            append("""</svg>""")
        }
    }

    private fun rangeBar(minV: Int, midV: Int, maxV: Int, myPct: Int): String = buildString {
        append("""<div style="margin:14px 0 20px">""")
        append("""<div style="position:relative;height:7px;background:linear-gradient(90deg,#E7EAF0,#FFB37E 70%,#FFD8BE);border-radius:4px;margin-bottom:18px">""")
        append("""<div style="position:absolute;top:-6px;left:${myPct}%;width:3px;height:19px;background:#E85D1F;border-radius:2px;transform:translateX(-50%)"></div>""")
        append("""<div style="position:absolute;top:-15px;left:${myPct}%;transform:translateX(-50%);font-size:8.5pt;font-weight:800;color:#E85D1F;white-space:nowrap">내 매물</div>""")
        append("""</div>""")
        append("""<div style="display:flex;justify-content:space-between;font-size:9pt;color:#8A94A6">""")
        append("""<span>${minV}만</span><span>중앙 ${midV}만</span><span>${maxV}만</span>""")
        append("""</div></div>""")
    }

    private fun swotCell(letter: String, title: String, content: String, bg: String, accent: String): String =
        """<td style="background:${bg};border:1px solid #E7EAF0;border-radius:10px;padding:14px 16px;vertical-align:top;width:50%">""" +
        """<div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">""" +
        """<span style="display:inline-flex;width:22px;height:22px;border-radius:6px;background:${accent};color:#fff;align-items:center;justify-content:center;font-size:11pt;font-weight:800">${e(letter)}</span>""" +
        """<b style="font-size:11pt">${e(title)}</b></div>""" +
        """<p style="margin:0;font-size:10pt;line-height:1.6;color:#374050">${e(content)}</p>""" +
        """</td>"""

    private fun secHead(num: String, title: String): String =
        """<h2 class="sec"><span class="n">${e(num)}</span> ${e(title)}</h2>"""

    private fun callout(text: String, type: String): String {
        val (bc, bg) = when (type) {
            "blue"  -> "#2D6FE0" to "#EAF1FD"
            "green" -> "#1A8F4C" to "#E9F7EF"
            "amber" -> "#B9791A" to "#FBF1DD"
            "red"   -> "#D33A3A" to "#FCEAEA"
            else    -> "#E85D1F" to "#FCF5F1"
        }
        return """<div style="border-left:4px solid $bc;background:$bg;border-radius:0 8px 8px 0;padding:11px 14px;margin:10px 0;font-weight:600;font-size:11pt;line-height:1.5">${e(text)}</div>"""
    }

    private fun foot(label: String, page: String): String =
        """<div class="foot"><span>${e(label)}</span>${if (page.isNotBlank()) "<span>${e(page)}</span>" else ""}</div>"""

    private fun barRow(label: String, value: Int, max: Int, highlight: Boolean): String {
        val pct    = (value * 100.0 / max.coerceAtLeast(1)).toInt().coerceIn(0, 100)
        val nmSt   = if (highlight) "font-weight:800;color:#1B2330" else "color:#5A6678"
        val fillSt = if (highlight) "background:linear-gradient(90deg,#FF8A4C,#E85D1F)" else "background:linear-gradient(90deg,#FFB37E,#E85D1F)"
        return buildString {
            append("""<div style="display:flex;align-items:center;gap:8px;margin:6px 0">""")
            append("""<div style="width:116px;font-size:10pt;$nmSt">${e(label)}</div>""")
            append("""<div style="flex:1;height:18px;background:#EEF0F4;border-radius:5px;overflow:hidden">""")
            append("""<div style="width:${pct}%;height:100%;border-radius:5px;$fillSt"></div></div>""")
            append("""<div style="width:38px;text-align:right;font-size:10pt;font-weight:700">$value</div>""")
            append("""</div>""")
        }
    }

    private fun kpiSm(label: String, value: String): String =
        """<div class="kpi-sm" style="margin-bottom:8px"><div class="kpi-lab">${e(label)}</div><div class="kpi-val" style="font-size:13pt">${e(value)}</div></div>"""

    private fun fmtManwon(manwon: Int): String {
        val eok = manwon / 10000
        val man = manwon % 10000
        return when { eok > 0 && man > 0 -> "₩${eok}억 ${man}만"; eok > 0 -> "₩${eok}억"; else -> "₩${man}만" }
    }

    // ── 데이터 추출 헬퍼 ──────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun m(v: Any?): Map<*, *>? = v as? Map<*, *>

    @Suppress("UNCHECKED_CAST")
    private fun l(v: Any?): List<*> = v as? List<*> ?: emptyList<Any>()

    private fun s(v: Any?): String = when (v) {
        null         -> ""
        is Map<*, *> -> s(v["값"])
        else         -> v.toString()
    }

    private fun n(v: Any?): Number? = when (v) {
        is Number    -> v
        is String    -> v.toDoubleOrNull()
        is Map<*, *> -> n(v["값"])
        else         -> null
    }

    private fun e(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "<br/>")

    // ── CSS ───────────────────────────────────────────────────────────────────────

    companion object {
        private val CSS = """
            @page { size: A4; margin: 16mm 15mm; }
            * { box-sizing: border-box; }
            body { font-family: 'Noto Sans KR', 'Malgun Gothic', sans-serif; color: #1B2330; font-size: 10pt; line-height: 1.5; margin: 0; }
            .sheet { page-break-after: always; }
            .sheet:last-child { page-break-after: auto; }
            .pad { padding: 22px 26px; }

            .cover { background: #3A1A0C; color: #fff; padding: 28px 26px; }
            .brand { font-size: 9pt; opacity: 0.85; letter-spacing: 0.5px; }
            .cover h1 { font-size: 20pt; margin: 8px 0 6px; font-weight: 800; }
            .sub { opacity: 0.9; font-size: 12pt; }
            .badges { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
            .badge { font-size: 9.5pt; font-weight: 700; padding: 4px 10px; border-radius: 14px; background: rgba(255,255,255,0.18); }
            .badge.grade { background: #fff; color: #B9791A; }
            .badge.warn  { background: #D33A3A; color: #fff; }

            h2.sec { font-size: 11pt; letter-spacing: 0.3px; color: #E85D1F; margin: 0 0 12px; display: flex; align-items: center; gap: 8px; font-weight: 800; }
            h2.sec .n { display: inline-flex; width: 22px; height: 22px; border-radius: 6px; background: #E85D1F; color: #fff; align-items: center; justify-content: center; font-size: 10pt; }
            h3.sub-h3 { font-size: 11pt; margin: 16px 0 8px; }

            .kpi { flex: 1; border: 1px solid #E7EAF0; border-radius: 9px; padding: 11px 12px; }
            .kpi-lab { font-size: 9.5pt; color: #5A6678; font-weight: 600; }
            .kpi-val { font-size: 16pt; font-weight: 800; margin-top: 2px; }
            .kpi-ctx { font-size: 9pt; color: #8A94A6; }
            .kpi-sm  { border: 1px solid #E7EAF0; border-radius: 8px; padding: 9px 11px; }

            .col-label { font-size: 9.5pt; font-weight: 800; color: #8A94A6; margin-bottom: 6px; }
            .risk-card   { border: 1px solid #E7EAF0; border-left: 4px solid #D33A3A; border-radius: 0 8px 8px 0; padding: 10px 12px; margin: 6px 0; }
            .risk-title  { font-weight: 700; font-size: 11pt; }
            .risk-detail { font-size: 10pt; color: #5A6678; margin-top: 4px; line-height: 1.45; }

            .pill     { font-size: 9pt; font-weight: 700; padding: 2px 7px; border-radius: 10px; white-space: nowrap; }
            .pill-hi  { background: #FCEAEA; color: #D33A3A; }
            .pill-mid { background: #FBF1DD; color: #B9791A; }
            .pill-lo  { background: #E9F7EF; color: #1A8F4C; }

            .act { display: flex; gap: 9px; padding: 9px 0; border-bottom: 1px solid #E7EAF0; align-items: flex-start; }
            .act:last-child { border: 0; }
            .act-no  { flex: 0 0 auto; width: 22px; height: 22px; border-radius: 50%; background: #E85D1F; color: #fff; font-size: 10pt; font-weight: 800; display: flex; align-items: center; justify-content: center; }
            .act-txt b { font-size: 11pt; }
            .act-txt p { margin: 2px 0 0; font-size: 9.5pt; color: #5A6678; line-height: 1.4; }
            .act-dur   { margin-left: auto; font-size: 9pt; color: #8A94A6; white-space: nowrap; flex-shrink: 0; }

            table.tbl { width: 100%; border-collapse: collapse; font-size: 10pt; }
            table.tbl th { background: #F7F8FB; color: #5A6678; font-weight: 700; text-align: left; padding: 8px 9px; border-bottom: 1px solid #E7EAF0; }
            table.tbl td { padding: 9px; border-bottom: 1px solid #E7EAF0; vertical-align: top; }
            table.tbl tr.best-row td { background: #FCF5F1; }
            table.tbl td.r, table.tbl th.r { text-align: right; }
            .recmark { font-size: 9pt; font-weight: 800; color: #fff; background: #E85D1F; padding: 2px 6px; border-radius: 9px; margin-left: 5px; }

            .panel    { border: 1px solid #E7EAF0; border-radius: 9px; padding: 13px 14px; }
            .panel-h3 { margin: 0 0 10px; font-size: 11pt; }
            .panel-sub { font-size: 9.5pt; color: #8A94A6; font-weight: 400; }

            .tag   { font-size: 9pt; font-weight: 700; padding: 2px 7px; border-radius: 9px; white-space: nowrap; display: inline-block; margin: 2px 0; }
            .tag-g { background: #E9F7EF; color: #1A8F4C; }
            .tag-r { background: #FCEAEA; color: #D33A3A; }
            .tag-a { background: #FBF1DD; color: #B9791A; }
            .tag-b { background: #EAF1FD; color: #2D6FE0; }

            .dtag { font-size: 10pt; font-weight: 700; padding: 5px 10px; border-radius: 16px; background: #EAF1FD; color: #2D6FE0; border: 1px solid #CFE0FA; display: inline-block; margin: 3px 2px; }

            .opcard { flex: 1; border: 1px solid #E7EAF0; border-top: 3px solid #E85D1F; border-radius: 0 0 10px 10px; padding: 14px; }

            .disc ul { margin: 0; border: 1px dashed #D6DAE3; border-radius: 9px; background: #FAFBFC; padding: 14px 14px 14px 30px; }
            .disc li { font-size: 10pt; color: #5A6678; line-height: 1.65; margin: 2px 0; }

            .foot { padding: 10px 26px; border-top: 1px solid #E7EAF0; display: flex; justify-content: space-between; font-size: 9pt; color: #8A94A6; }
            p.body { font-size: 11pt; line-height: 1.6; color: #374050; margin: 6px 0; }
        """.trimIndent()
    }
}
