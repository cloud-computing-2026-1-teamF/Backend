package com.sanggwonai.api.report.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 보고서 생성 설정. application.yml 의 `app.report.*` 에 바인딩.
 * (@ConfigurationPropertiesScan 이 메인 클래스에 있어 별도 등록 불필요)
 *
 * - openai     : 보고서 LLM 호출(공실검색 인프라와 동일 OpenAI Responses API)
 * - assumptions: ch9 투자회수 계산에 쓰는 표준 가정값
 * - categoryMargins: 9-카테고리 key("1".."9") -> 영업이익률.
 *   가영 add_sales_payback.py SERVICE_CODES(10분류)를 9분류에 근사 매핑한 값.
 *   ⚠ 투자회수 실데이터가 RDS에 적재되면 이 추정 대신 그 값을 사용할 것.
 */
@ConfigurationProperties(prefix = "app.report")
data class ReportProperties(
    val openai: OpenAi = OpenAi(),
    val assumptions: Assumptions = Assumptions(),
    val categoryMargins: Map<String, Double> = mapOf(
        "1" to 0.12, // 한식
        "2" to 0.12, // 중식
        "3" to 0.13, // 일식
        "4" to 0.13, // 서양식
        "5" to 0.12, // 기타
        "6" to 0.12, // 구내식당 및 뷔페
        "7" to 0.13, // 패스트푸드
        "8" to 0.18, // 주점업
        "9" to 0.17  // 카페·디저트(커피음료 기준)
    )
) {
    data class OpenAi(
        val enabled: Boolean = false,
        val apiKey: String = "",
        val endpoint: String = "https://api.openai.com/v1/responses",
        val model: String = "gpt-5.4-nano",
        val timeoutSeconds: Long = 30,
        val maxOutputTokens: Int = 9000
    )

    data class Assumptions(
        val avgTicketPriceKrw: Int = 6500,
        val operatingDaysPerMonth: Int = 26,
        val peakShareOfDailySales: Double = 0.45,
        val peakHoursPerDay: Int = 3,
        // 환산보증금(보증금 + 월세×100) × 0.9% — 상가 중개수수료 상한 근사
        val brokerageRate: Double = 0.009,
        // 초기투자비에 더하는 (월세+관리비)×N 개월 (운전자금 버퍼). 가영 식과 동일(N=3)
        val workingCapitalMonths: Int = 3
    )
}
