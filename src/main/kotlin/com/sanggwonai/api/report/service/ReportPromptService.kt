package com.sanggwonai.api.report.service

import com.sanggwonai.api.auth.service.AuthContext
import org.springframework.stereotype.Service

data class ReportGenerationResult(
    val source: String,                 // "openai" | "failed"
    val report: Map<String, Any?>?,     // 검증 통과한 보고서 JSON (v6.5 병합 형태)
    val rawText: String?,
    val input: Map<String, Any?>        // 조립된 input_data (감사/디버그용)
)

/**
 * 보고서 생성 오케스트레이션 (Step 4).
 *  1) ReportContextAssembler 로 input_data 조립 (메인 스레드 — 보안/트랜잭션 컨텍스트 유지)
 *  2) ReportLlmComposer 가 매물별/공통 콜을 병렬 호출·병합·검증 (v6.5)
 */
@Service
class ReportPromptService(
    private val assembler: ReportContextAssembler,
    private val composer: ReportLlmComposer
) {
    fun llmAvailable(): Boolean = composer.llmAvailable()

    fun generate(authContext: AuthContext, analysisId: String): ReportGenerationResult =
        composer.compose(assembler.assemble(authContext, analysisId))
}
