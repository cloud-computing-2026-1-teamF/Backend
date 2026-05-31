package com.sanggwonai.api.report.service

import com.sanggwonai.api.report.config.ReportProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 보고서 LLM 호출 — OpenAI Responses API.
 * 공실검색 OpenAiVacancyPromptClient 패턴을 그대로 복제(java.net.http.HttpClient).
 *
 * 보안: API 키는 app.report.openai.api-key(=환경변수 OPENAI_API_KEY)에서만 읽는다.
 *       코드/리소스/깃에 절대 하드코딩하지 않는다.
 */
@Component
class ReportLlmClient(
    private val properties: ReportProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ReportLlmClient::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(properties.openai.timeoutSeconds.coerceAtLeast(1)))
        .build()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    fun isAvailable(): Boolean {
        val o = properties.openai
        return o.enabled && o.apiKey.isNotBlank()
    }

    /** system+user 프롬프트로 OpenAI 호출. 성공 시 출력 텍스트(보고서 JSON 문자열) 반환, 실패 null. */
    fun generate(systemPrompt: String, userPrompt: String, temperature: Double): String? {
        val o = properties.openai
        if (!isAvailable()) return null
        val body = mapOf(
            "model" to o.model,
            "input" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "temperature" to temperature,
            "max_output_tokens" to o.maxOutputTokens.coerceIn(512, 16000)
        )
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create(o.endpoint))
                .timeout(Duration.ofSeconds(o.timeoutSeconds.coerceAtLeast(1)))
                .header("Authorization", "Bearer ${o.apiKey}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                logger.warn("OpenAI report generation failed: status {}", response.statusCode())
                return null
            }
            extractOutputText(objectMapper.readValue(response.body(), mapType))
        }.onFailure { logger.warn("OpenAI report generation error: {}", it.message) }.getOrNull()
    }

    /** Responses API 출력에서 텍스트 추출 (output_text 우선, 없으면 output[].content[].text). */
    private fun extractOutputText(body: Map<String, Any?>): String? {
        (body["output_text"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val output = body["output"] as? List<*> ?: return null
        output.forEach { o ->
            val item = o as? Map<*, *> ?: return@forEach
            val content = item["content"] as? List<*> ?: return@forEach
            content.forEach { c ->
                val cm = c as? Map<*, *> ?: return@forEach
                (cm["refusal"] as? String)?.takeIf { it.isNotBlank() }?.let { return null }
                (cm["text"] as? String)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
            }
        }
        return null
    }
}
