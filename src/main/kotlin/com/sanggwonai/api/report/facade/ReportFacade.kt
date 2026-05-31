package com.sanggwonai.api.report.facade

import com.sanggwonai.api.auth.service.AuthContextResolver
import com.sanggwonai.api.report.service.GeneratedReport
import com.sanggwonai.api.report.service.ReportService
import org.springframework.stereotype.Component

@Component
class ReportFacade(
    private val reportService: ReportService,
    private val authContextResolver: AuthContextResolver
) {
    fun generate(authorizationHeader: String?, analysisId: String): GeneratedReport? =
        reportService.generate(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)
}
