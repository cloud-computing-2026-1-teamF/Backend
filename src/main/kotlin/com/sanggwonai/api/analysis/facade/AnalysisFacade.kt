package com.sanggwonai.api.analysis.facade

import com.sanggwonai.api.analysis.controller.request.CreateAnalysisRequest
import com.sanggwonai.api.analysis.service.AnalysisService
import com.sanggwonai.api.auth.service.AuthContextResolver
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
class AnalysisFacade(
    private val analysisService: AnalysisService,
    private val authContextResolver: AuthContextResolver
) {

    fun create(authorizationHeader: String?, request: CreateAnalysisRequest) =
        analysisService.create(authContextResolver.resolveOrThrow(authorizationHeader), request)

    fun polling(authorizationHeader: String?, analysisId: String) =
        analysisService.getPolling(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun events(authorizationHeader: String?, analysisId: String): SseEmitter =
        analysisService.openEvents(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)
}
