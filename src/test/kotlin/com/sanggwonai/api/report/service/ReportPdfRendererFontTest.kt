package com.sanggwonai.api.report.service

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * PDF 한글 폰트 임베드 검증 (Spring/DB 불필요한 순수 단위 테스트).
 *
 * 폰트 임베드가 실패하면 openhtmltopdf 는 예외 없이 기본 폰트로 폴백 → 한글이 □ 로 나온다.
 * 따라서 "render 가 성공했다"만으로는 부족하고, 출력 PDF 에 NotoSansKR 가 실제로
 * 임베드(서브셋)됐는지 바이트에서 확인한다. 임베드되면 BaseFont 이름에 'NotoSans' 가 박힌다.
 */
class ReportPdfRendererFontTest {

    @Test
    fun `한글 보고서가 NotoSansKR 폰트를 임베드한다`() {
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

        val bytes = ReportPdfRenderer().render(report)
        File("build/font-check.pdf").apply { parentFile.mkdirs() }.writeBytes(bytes)

        assertTrue(bytes.size > 5_000, "PDF 가 비정상적으로 작음(${bytes.size}B) — 렌더 실패 의심")

        // 임베드된 폰트의 BaseFont 이름은 ASCII 라 ISO-8859-1 로 바이트를 문자열화해 탐색.
        val asText = String(bytes, Charsets.ISO_8859_1)
        assertTrue(
            asText.contains("NotoSans", ignoreCase = true),
            "출력 PDF 에 NotoSans 폰트가 임베드되지 않음 → 한글이 □ 로 렌더됨(폰트 파일 호환 문제)."
        )
    }
}
