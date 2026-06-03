package com.sanggwonai.api.report.service

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * HTML 보고서 렌더 기본 검증 (Spring/DB 불필요한 순수 단위 테스트).
 */
class ReportHtmlRendererBasicTest {

    @Test
    fun `한글 보고서를 standalone HTML로 렌더한다`() {
        val report: Map<String, Any?> = mapOf(
            "report_metadata" to mapOf(
                "보고서_제목" to "상권을 부탁해 입지 분석 보고서",
                "보고서_부제" to "기존 시설 활용 가능 여부 포함",
                "추천_도장" to "강력 추천",
                "발급일시" to "2026-06-01 20:00"
            ),
            "chapter_1_executive_summary" to mapOf(
                "chapter_number" to "1",
                "chapter_title" to "총평",
                "요약" to "이 매물은 1년 이내 회수가 가능하며, 기존 시설 활용으로 인테리어비 절감이 가능합니다."
            )
        )

        val bytes = ReportHtmlRenderer().render(report, emptyMap())
        File("build/report-basic.html").apply { parentFile.mkdirs() }.writeBytes(bytes)
        val html = bytes.toString(Charsets.UTF_8)

        assertTrue(bytes.size > 5_000, "HTML이 비정상적으로 작음(${bytes.size}B) — 렌더 실패 의심")
        assertTrue(html.startsWith("<!DOCTYPE html>"), "HTML doctype 없음")
        assertTrue(html.contains("상권을 부탁해 입지 분석 보고서"), "한글 제목이 HTML에 포함되지 않음")
        assertTrue(html.contains("report-shell"), "standalone report shell 마크업 없음")
    }
}
