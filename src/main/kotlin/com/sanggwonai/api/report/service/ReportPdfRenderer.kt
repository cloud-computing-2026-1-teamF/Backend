package com.sanggwonai.api.report.service

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 보고서 output JSON(Map) -> HTML -> PDF(openhtmltopdf).
 *
 * 챕터 구조가 이질적이라 알려진 챕터 순서대로 제목 + 필드를 재귀 렌더(범용).
 * 한글은 resources/fonts/NotoSansKR-Regular.ttf 를 임베드해야 안 깨짐(없으면 □).
 *
 * ⚠ 로컬 확인:
 *   - build.gradle 에 openhtmltopdf-pdfbox 의존성(버전은 Spring Boot 4/PDFBox와 호환 확인).
 *   - resources/fonts/ 에 한글 TTF 추가(없으면 한글 깨짐).
 */
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

    private fun buildHtml(report: Map<String, Any?>): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><style>").append(CSS).append("</style></head><body>")
        renderMetadata(this, report["report_metadata"])
        for (key in CHAPTER_ORDER) {
            val ch = report[key] as? Map<*, *> ?: continue
            if (ch["활성_여부"] == false) continue
            renderChapter(this, ch)
        }
        append("</body></html>")
    }

    private fun renderMetadata(sb: StringBuilder, meta: Any?) {
        val m = meta as? Map<*, *> ?: return
        sb.append("<div class=\"cover\"><div class=\"brand\">상권을 부탁해 · AI 입지 분석 보고서</div>")
        sb.append("<h1>").append(esc(strOf(m["보고서_제목"]))).append("</h1>")
        strOf(m["보고서_부제"]).ifBlank { null }?.let { sb.append("<div class=\"sub\">").append(esc(it)).append("</div>") }
        sb.append("<div class=\"badges\">")
        strOf(m["보고서_등급"]).ifBlank { null }?.let { sb.append("<span class=\"badge\">등급 ").append(esc(it)).append("</span>") }
        strOf(m["추천_도장"]).ifBlank { null }?.let { sb.append("<span class=\"badge stamp\">").append(esc(it)).append("</span>") }
        strOf(m["발급일시"]).ifBlank { null }?.let { sb.append("<span class=\"date\">").append(esc(it)).append("</span>") }
        sb.append("</div></div>")
    }

    private fun renderChapter(sb: StringBuilder, ch: Map<*, *>) {
        sb.append("<section><h2>")
        strOf(ch["chapter_number"]).ifBlank { null }?.let { sb.append("<span class=\"num\">").append(esc(it)).append("</span> ") }
        sb.append(esc(strOf(ch["chapter_title"]))).append("</h2>")
        for ((k, v) in ch) {
            val key = k.toString()
            if (key in CH_SKIP || key.startsWith("_")) continue
            sb.append("<div class=\"field\"><div class=\"label\">").append(esc(label(key))).append("</div>")
            sb.append("<div class=\"value\">").append(renderValue(v)).append("</div></div>")
        }
        sb.append("</section>")
    }

    private fun renderValue(v: Any?): String = when (v) {
        null -> ""
        is Map<*, *> -> if (v.containsKey("값")) esc(strOf(v["값"])) else buildString {
            append("<div class=\"obj\">")
            for ((k, vv) in v) {
                val key = k.toString()
                if (key in CH_SKIP || key.startsWith("_")) continue
                append("<div class=\"row\"><span class=\"k\">").append(esc(label(key))).append("</span> ")
                append(renderValue(vv)).append("</div>")
            }
            append("</div>")
        }
        is List<*> -> when {
            v.isEmpty() -> ""
            v.none { it is Map<*, *> || it is List<*> } -> esc(v.joinToString(", ") { it.toString() })
            else -> buildString {
                append("<ul>")
                for (item in v) append("<li>").append(renderValue(item)).append("</li>")
                append("</ul>")
            }
        }
        else -> esc(v.toString())
    }

    private fun strOf(v: Any?): String = when (v) {
        null -> ""
        is Map<*, *> -> strOf(v["값"])
        else -> v.toString()
    }

    private fun label(key: String): String = key.replace('_', ' ')

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("\n", "<br/>")

    companion object {
        // 9->7 챕터 재구성: chapter_2·chapter_6 제거(렌더 순서에서도 제외).
        private val CHAPTER_ORDER = listOf(
            "chapter_1_executive_summary",
            "chapter_3_top3_property_analysis", "chapter_4_location_characteristics",
            "chapter_5_business_fit_analysis",
            "chapter_7_appendix", "chapter_8_review_insight", "chapter_9_investment_payback"
        )
        private val CH_SKIP = setOf("chapter_number", "chapter_title", "활성_여부")
        private val CSS = """
            @page { size: A4; margin: 22mm 18mm; }
            body { font-family: 'Noto Sans KR', sans-serif; color: #1B2330; font-size: 10pt; line-height: 1.5; }
            .cover { border-bottom: 2px solid #E85D1F; padding-bottom: 12px; margin-bottom: 18px; }
            .brand { color: #E85D1F; font-size: 8.5pt; }
            h1 { font-size: 19pt; margin: 6px 0; }
            .sub { color: #54607A; }
            .badges { margin-top: 8px; }
            .badge { background: #FBF4F0; border: 1px solid #E5E8EF; border-radius: 4px; padding: 2px 8px; font-size: 9pt; margin-right: 6px; }
            .badge.stamp { background: #E85D1F; color: #ffffff; border-color: #E85D1F; }
            .date { color: #54607A; font-size: 8.5pt; }
            section { margin: 0 0 14px; }
            h2 { font-size: 13pt; border-left: 3px solid #E85D1F; padding-left: 8px; margin: 16px 0 8px; }
            h2 .num { color: #E85D1F; }
            .field { margin: 5px 0; }
            .field .label { font-weight: bold; font-size: 9.5pt; }
            .field .value { color: #54607A; }
            .obj .row { margin: 2px 0; }
            .obj .k { font-weight: bold; color: #1B2330; }
            ul { margin: 4px 0; padding-left: 16px; }
            li { margin: 2px 0; color: #54607A; }
        """.trimIndent()
    }
}
