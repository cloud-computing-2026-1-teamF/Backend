package com.sanggwonai.api.vacancy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.vacancy.menu-price.openai")
data class MenuPriceOpenAiProperties(
    val enabled: Boolean = true,
    val apiKey: String = "",
    val endpoint: String = "https://api.openai.com/v1/responses",
    val model: String = "gpt-5.4-nano",
    val timeoutSeconds: Long = 5,
    val maxOutputTokens: Int = 16
)
