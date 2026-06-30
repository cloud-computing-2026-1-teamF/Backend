package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.config.MenuPriceOpenAiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.measureTimeMillis

@Component
class MenuPriceOpenAiClient(
    private val properties: MenuPriceOpenAiProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MenuPriceOpenAiClient::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds.coerceAtLeast(1)))
        .build()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}
    private val pricePattern = Regex("""\d[\d,]*""")

    fun estimate(menuName: String): MenuPriceOpenAiResult? {
        if (!isAvailable()) return null

        val body = mapOf(
            "model" to properties.model,
            "input" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to "You estimate common menu prices for South Korea. Return only one integer number in Korean won. No currency symbol, no commas, no explanation."
                ),
                mapOf(
                    "role" to "user",
                    "content" to "What is the suitable price of $menuName in South Korea? Just give a number in Korean won without any explanations."
                )
            ),
            "reasoning" to mapOf("effort" to "none"),
            "text" to mapOf("verbosity" to "low"),
            "temperature" to 0,
            "max_output_tokens" to properties.maxOutputTokens.coerceIn(4, 64)
        )

        var elapsedMs = 0L
        return runCatching {
            var responseBody = ""
            elapsedMs = measureTimeMillis {
                val request = HttpRequest.newBuilder(URI.create(properties.endpoint))
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds.coerceAtLeast(1)))
                    .header("Authorization", "Bearer ${properties.apiKey}")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() !in 200..299) {
                    logger.warn("OpenAI menu price estimate failed with status {}", response.statusCode())
                    return null
                }
                responseBody = response.body()
            }
            val text = extractOutputText(objectMapper.readValue(responseBody, mapType)) ?: return null
            val price = extractPrice(text) ?: return null
            MenuPriceOpenAiResult(price = price, latencyMs = elapsedMs)
        }.onFailure {
            logger.warn("OpenAI menu price estimate failed: {}", it.message)
        }.getOrNull()
    }

    private fun isAvailable(): Boolean = properties.enabled && properties.apiKey.isNotBlank()

    private fun extractOutputText(responseBody: Map<String, Any?>): String? {
        (responseBody["output_text"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val output = responseBody["output"] as? List<*> ?: return null
        output.forEach { outputItem ->
            val item = outputItem as? Map<*, *> ?: return@forEach
            val content = item["content"] as? List<*> ?: return@forEach
            content.forEach { contentItem ->
                val contentMap = contentItem as? Map<*, *> ?: return@forEach
                if (!(contentMap["refusal"] as? String).isNullOrBlank()) return null
                (contentMap["text"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private fun extractPrice(text: String): Long? {
        val raw = pricePattern.find(text)?.value?.replace(",", "") ?: return null
        return raw.toLongOrNull()?.takeIf { it > 0 }
    }
}

data class MenuPriceOpenAiResult(
    val price: Long,
    val latencyMs: Long
)
