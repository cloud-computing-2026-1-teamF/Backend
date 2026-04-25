package com.sanggwonai.api.analysis.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.analysis")
data class AnalysisProperties(
    val estimatedSeconds: Int
)
