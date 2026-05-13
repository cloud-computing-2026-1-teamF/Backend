package com.sanggwonai.api.analysis.facade

import com.sanggwonai.api.analysis.controller.request.CreateAnalysisRequest
import com.sanggwonai.api.analysis.controller.request.PatchAnalysisRequest
import com.sanggwonai.api.analysis.dto.AnalysisRecommendationsDto
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

    fun list(authorizationHeader: String?, limit: Int?, saved: Boolean?) =
        analysisService.list(authContextResolver.resolveOrThrow(authorizationHeader), limit, saved)

    fun patch(authorizationHeader: String?, analysisId: String, request: PatchAnalysisRequest?) =
        analysisService.patch(authContextResolver.resolveOrThrow(authorizationHeader), analysisId, request)

    fun delete(authorizationHeader: String?, analysisId: String) =
        analysisService.delete(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun stats(authorizationHeader: String?) =
        analysisService.stats(authContextResolver.resolveOrThrow(authorizationHeader))

    fun events(authorizationHeader: String?, analysisId: String): SseEmitter =
        analysisService.openEvents(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun recommendedProperties(authorizationHeader: String?, analysisId: String): AnalysisRecommendationsDto =
        analysisService.getRecommendedProperties(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun keyMetrics(authorizationHeader: String?, analysisId: String) =
        analysisService.getKeyMetrics(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun footTraffic(authorizationHeader: String?, analysisId: String) =
        analysisService.getFootTraffic(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun competition(authorizationHeader: String?, analysisId: String) =
        analysisService.getCompetition(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun estimatedRevenue(authorizationHeader: String?, analysisId: String) =
        analysisService.getEstimatedRevenue(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun industryGrowth(authorizationHeader: String?, analysisId: String) =
        analysisService.getIndustryGrowth(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)

    fun accessibility(authorizationHeader: String?, analysisId: String) =
        analysisService.getAccessibility(authContextResolver.resolveOrThrow(authorizationHeader), analysisId)
}
