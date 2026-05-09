package com.sanggwonai.api.analysis.controller.response

import java.time.Instant

data class CreateAnalysisResponse(
    val id: String,
    val vacancyId: String,
    val status: String,
    val progress: Int,
    val createdAt: Instant,
    val estimatedSeconds: Int,
    val links: AnalysisLinksResponse
)
