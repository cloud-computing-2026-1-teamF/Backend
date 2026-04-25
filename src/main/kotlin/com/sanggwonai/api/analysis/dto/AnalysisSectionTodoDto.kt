package com.sanggwonai.api.analysis.dto

import java.time.Instant

data class AnalysisSectionTodoDto(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val updatedAt: Instant
)
