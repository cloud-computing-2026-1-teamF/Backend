package com.sanggwonai.api.analysis.service

import com.sanggwonai.api.analysis.dto.AnalysisEventDto
import com.sanggwonai.api.analysis.dto.AnalysisLinksDto
import com.sanggwonai.api.analysis.dto.AnalysisPollingData
import com.sanggwonai.api.analysis.dto.CreateAnalysisData
import com.sanggwonai.api.analysis.dto.CreateAnalysisRequest
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
import com.sanggwonai.api.common.error.ErrorCode
import com.sanggwonai.api.common.util.IdGenerator
import org.springframework.http.HttpStatus
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
            .orElseThrow { ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED, "인증이 필요해요") }

        validateDailyLimit(user.id, user.tier)
        if (!businessTypeRepository.existsById(request.businessType)) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED, "유효하지 않은 업종이에요")
        }
        if (!areaRepository.existsById(request.areaId)) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED, "유효하지 않은 지역이에요")
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
            .orElseThrow { ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "분석을 찾을 수 없어요") }
        if (analysis.userId != userId) {
            throw ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "접근 권한이 없어요")
        }
        return analysis
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
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, "일일 분석 한도를 초과했어요")
        }
    }
}
