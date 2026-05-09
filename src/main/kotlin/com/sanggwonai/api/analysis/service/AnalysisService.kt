package com.sanggwonai.api.analysis.service

import com.sanggwonai.api.analysis.controller.request.CreateAnalysisRequest
import com.sanggwonai.api.analysis.dto.AnalysisEventDto
import com.sanggwonai.api.analysis.dto.AnalysisLinksDto
import com.sanggwonai.api.analysis.dto.AnalysisPollingData
import com.sanggwonai.api.analysis.dto.AnalysisSectionTodoDto
import com.sanggwonai.api.analysis.dto.CreateAnalysisData
import com.sanggwonai.api.analysis.entity.AnalysisEntity
import com.sanggwonai.api.analysis.entity.AnalysisStatus
import com.sanggwonai.api.analysis.mapper.AnalysisMapper
import com.sanggwonai.api.analysis.repository.AnalysisRepository
import com.sanggwonai.api.area.repository.AreaRepository
import com.sanggwonai.api.auth.entity.UserTier
import com.sanggwonai.api.auth.repository.UserRepository
import com.sanggwonai.api.auth.service.AuthContext
import com.sanggwonai.api.business.repository.BusinessTypeRepository
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.common.util.IdGenerator
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executors

@Service
class AnalysisService(
    private val analysisRepository: AnalysisRepository,
    private val areaRepository: AreaRepository,
    private val businessTypeRepository: BusinessTypeRepository,
    private val userRepository: UserRepository,
    private val analysisMapper: AnalysisMapper,
    private val analysisProperties: AnalysisProperties,
    private val progressWorker: AnalysisProgressWorker,
    private val clock: Clock
) {
    private val sseExecutor = Executors.newCachedThreadPool()

    @Transactional
    fun create(authContext: AuthContext, request: CreateAnalysisRequest): CreateAnalysisData {
        val user = userRepository.findById(authContext.userId)
            .orElseThrow { ApiException.of(ErrorType.AUTH_REQUIRED) }

        validateDailyLimit(user.id, user.tier)
        if (!businessTypeRepository.existsById(request.businessType)) {
            throw ApiException.of(ErrorType.INVALID_BUSINESS_TYPE)
        }
        if (!areaRepository.existsById(request.areaId)) {
            throw ApiException.of(ErrorType.INVALID_AREA)
        }

        val now = Instant.now(clock)
        val analysis = analysisRepository.save(
            AnalysisEntity(
                id = IdGenerator.next("an"),
                userId = authContext.userId,
                businessTypeKey = request.businessType,
                areaId = request.areaId,
                budgetDepositMax = request.budget?.depositMax,
                budgetRentMax = request.budget?.rentMax,
                status = AnalysisStatus.PENDING,
                progress = 0,
                stepIndex = null,
                stepTotal = null,
                stepLabel = null,
                errorCode = null,
                errorMessage = null,
                createdAt = now,
                completedAt = null,
                updatedAt = now
            )
        )

        progressWorker.runAsync(analysis.id)

        return CreateAnalysisData(
            id = analysis.id,
            status = analysis.status.name.lowercase(),
            progress = analysis.progress,
            createdAt = analysis.createdAt,
            estimatedSeconds = analysisProperties.estimatedSeconds,
            links = AnalysisLinksDto(
                self = "/v1/analyses/${analysis.id}",
                events = "/v1/analyses/${analysis.id}/events"
            )
        )
    }

    @Transactional(readOnly = true)
    fun getPolling(authContext: AuthContext, analysisId: String): AnalysisPollingData {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return analysisMapper.toPollingData(analysis)
    }

    @Transactional(readOnly = true)
    fun list(authContext: AuthContext, limit: Int?): List<AnalysisPollingData> {
        val pageSize = limit?.coerceIn(1, 50) ?: 20
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(
            authContext.userId,
            PageRequest.of(0, pageSize)
        ).map(analysisMapper::toPollingData)
    }

    @Transactional
    fun patch(authContext: AuthContext, analysisId: String): AnalysisPollingData {
        return analysisMapper.toPollingData(loadOwnedAnalysis(authContext.userId, analysisId))
    }

    @Transactional
    fun delete(authContext: AuthContext, analysisId: String) {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        analysisRepository.delete(analysis)
    }

    @Transactional(readOnly = true)
    fun stats(authContext: AuthContext): UserStatsData {
        return UserStatsData(
            totalAnalyses = analysisRepository.countByUserId(authContext.userId),
            savedAnalyses = analysisRepository.countByUserIdAndStatus(authContext.userId, AnalysisStatus.DONE),
            avgTopScore = 0
        )
    }

    fun openEvents(authContext: AuthContext, analysisId: String): SseEmitter {
        val emitter = SseEmitter(0L)
        sseExecutor.submit {
            try {
                var previous: AnalysisEventDto? = null
                while (true) {
                    val event = findEvent(authContext.userId, analysisId)
                    if (event != previous) {
                        emitter.send(SseEmitter.event().data(event))
                        previous = event
                    }
                    if (event.status == "done" || event.status == "failed") {
                        emitter.complete()
                        break
                    }
                    Thread.sleep(500)
                }
            } catch (_: Exception) {
                emitter.complete()
            }
        }
        return emitter
    }

    @Transactional(readOnly = true)
    fun findEvent(userId: String, analysisId: String): AnalysisEventDto {
        val analysis = loadOwnedAnalysis(userId, analysisId)
        return analysisMapper.toEventData(analysis)
    }

    @Transactional(readOnly = true)
    fun loadOwnedAnalysis(userId: String, analysisId: String): AnalysisEntity {
        val analysis = analysisRepository.findById(analysisId)
            .orElseThrow { ApiException.of(ErrorType.ANALYSIS_NOT_FOUND) }
        if (analysis.userId != userId) {
            throw ApiException.of(ErrorType.ANALYSIS_FORBIDDEN)
        }
        return analysis
    }

    @Transactional(readOnly = true)
    fun getRecommendedProperties(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "recommended_properties", "추천 매물")
    }

    @Transactional(readOnly = true)
    fun getKeyMetrics(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "key_metrics", "주요 지표")
    }

    @Transactional(readOnly = true)
    fun getFootTraffic(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "foot_traffic", "유동인구")
    }

    @Transactional(readOnly = true)
    fun getCompetition(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "competition", "경쟁 점포")
    }

    @Transactional(readOnly = true)
    fun getEstimatedRevenue(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "estimated_revenue", "추정 매출")
    }

    @Transactional(readOnly = true)
    fun getIndustryGrowth(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "industry_growth", "업종 성장률")
    }

    @Transactional(readOnly = true)
    fun getAccessibility(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "accessibility", "입지 접근성")
    }

    private fun todoSection(analysis: AnalysisEntity, sectionKey: String, sectionLabel: String): AnalysisSectionTodoDto {
        return AnalysisSectionTodoDto(
            analysisId = analysis.id,
            sectionKey = sectionKey,
            sectionLabel = sectionLabel,
            todo = "TODO: 공식 API/크롤링 데이터 스키마 확정 후 상세 필드 정의 예정",
            updatedAt = analysis.updatedAt
        )
    }

    private fun validateDailyLimit(userId: String, tier: UserTier) {
        if (tier != UserTier.FREE) {
            return
        }
        val today = LocalDate.now(clock)
        val from = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val count = analysisRepository.countCreatedByUserInRange(userId, from, to)
        if (count >= 20) {
            throw ApiException.of(ErrorType.ANALYSIS_RATE_LIMIT_EXCEEDED)
        }
    }
}

data class UserStatsData(
    val totalAnalyses: Long,
    val savedAnalyses: Long,
    val avgTopScore: Int
)
