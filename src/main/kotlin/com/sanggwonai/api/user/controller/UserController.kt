package com.sanggwonai.api.user.controller

import com.sanggwonai.api.analysis.facade.AnalysisFacade
import com.sanggwonai.api.common.api.ApiResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users")
class UserController(
    private val analysisFacade: AnalysisFacade
) {
    @GetMapping("/me/stats", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun stats(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?
    ): ResponseEntity<ApiResponse<UserStatsResponse>> {
        val data = analysisFacade.stats(authorization)
        return ResponseEntity.ok(
            ApiResponse(
                UserStatsResponse(
                    totalAnalyses = data.totalAnalyses,
                    savedAnalyses = data.savedAnalyses,
                    avgTopScore = data.avgTopScore
                )
            )
        )
    }
}

data class UserStatsResponse(
    val totalAnalyses: Long,
    val savedAnalyses: Long,
    val avgTopScore: Int
)
