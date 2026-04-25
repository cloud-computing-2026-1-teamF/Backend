package com.sanggwonai.api.analysis.controller.response

import java.time.Instant

data class AnalysisSectionTodoResponse(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val updatedAt: Instant
)
