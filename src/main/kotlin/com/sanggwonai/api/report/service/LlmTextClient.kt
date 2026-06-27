package com.sanggwonai.api.report.service

/**
 * 보고서 LLM 호출 추상화. 운영은 ReportLlmClient(OpenAI), 테스트는 가짜 구현을 끼워
 * 키·비용 없이 조립 배관(병렬·머지·검증)을 검증한다.
 */
interface LlmTextClient {
    fun isAvailable(): Boolean
    fun generate(systemPrompt: String, userPrompt: String, temperature: Double): String?
}
