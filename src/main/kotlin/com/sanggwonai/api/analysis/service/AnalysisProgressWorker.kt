package com.sanggwonai.api.analysis.service

import com.sanggwonai.api.analysis.entity.AnalysisStatus
import com.sanggwonai.api.analysis.repository.AnalysisRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Component
class AnalysisProgressWorker(
    private val analysisRepository: AnalysisRepository,
    private val clock: Clock
) {

    @Async
    fun runAsync(analysisId: String) {
        val steps = listOf(
            Triple(1, 25, "주변 상권 살펴보는 중"),
            Triple(2, 50, "유동인구와 경쟁 매장 확인"),
            Triple(3, 75, "업종별 생존율 계산"),
            Triple(4, 100, "가장 잘 맞는 매물 3곳 선별")
        )
        try {
            Thread.sleep(400)
            updatePendingToRunning(analysisId)
            for ((index, progress, label) in steps) {
                Thread.sleep(900)
                updateStep(analysisId, index, progress, label)
            }
            complete(analysisId)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            fail(analysisId, "analysis_failed", "분석 작업이 중단되었어요")
        } catch (_: Exception) {
            fail(analysisId, "upstream_unavailable", "분석 엔진 연결에 실패했어요")
        }
    }

    @Transactional
    fun updatePendingToRunning(analysisId: String) {
        val now = Instant.now(clock)
        analysisRepository.findById(analysisId).ifPresent {
            it.status = AnalysisStatus.RUNNING
            it.progress = 0
            it.stepIndex = null
            it.stepTotal = 4
            it.stepLabel = null
            it.updatedAt = now
        }
    }

    @Transactional
    fun updateStep(analysisId: String, index: Int, progress: Int, label: String) {
        val now = Instant.now(clock)
        analysisRepository.findById(analysisId).ifPresent {
            it.status = AnalysisStatus.RUNNING
            it.progress = progress
            it.stepIndex = index
            it.stepTotal = 4
            it.stepLabel = label
            it.updatedAt = now
        }
    }

    @Transactional
    fun complete(analysisId: String) {
        val now = Instant.now(clock)
        analysisRepository.findById(analysisId).ifPresent {
            it.status = AnalysisStatus.DONE
            it.progress = 100
            it.stepIndex = null
            it.stepTotal = null
            it.stepLabel = null
            it.completedAt = now
            it.updatedAt = now
        }
    }

    @Transactional
    fun fail(analysisId: String, errorCode: String, message: String) {
        val now = Instant.now(clock)
        analysisRepository.findById(analysisId).ifPresent {
            it.status = AnalysisStatus.FAILED
            it.errorCode = errorCode
            it.errorMessage = message
            it.updatedAt = now
        }
    }
}
