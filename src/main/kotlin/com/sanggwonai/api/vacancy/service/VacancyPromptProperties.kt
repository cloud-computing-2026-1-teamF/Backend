package com.sanggwonai.api.vacancy.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.vacancy.prompt")
data class VacancyPromptProperties(
    val openai: OpenAi = OpenAi(),
    val cacheSize: Int = 200
) {
    data class OpenAi(
        val enabled: Boolean = false,
        val apiKey: String = "",
        val endpoint: String = "https://api.openai.com/v1/responses",
        val model: String = "gpt-4.1-mini",
        val timeoutSeconds: Long = 6,
        val maxOutputTokens: Int = 1200
    )
}
