package com.sanggwonai.api.analysis.dto

import java.time.Instant

data class CreateAnalysisData(
    val id: String,
    val vacancyId: String,
    val status: String,
    val progress: Int,
    val createdAt: Instant,
    val estimatedSeconds: Int,
    val links: AnalysisLinksDto,
    val recommendations: List<AnalysisRecommendationDto>
)
