package com.sanggwonai.api.business.controller

import com.sanggwonai.api.business.controller.response.BusinessTypeResponse
import com.sanggwonai.api.business.dto.BusinessTypeDto
import com.sanggwonai.api.business.facade.BusinessTypeFacade
import com.sanggwonai.api.common.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/business-types")
@Tag(name = "업종", description = "요식업 업종 조회 API 모음임")
class BusinessTypeController(
    private val businessTypeFacade: BusinessTypeFacade
) {
    @GetMapping
    @Operation(
        summary = "업종 목록 조회함",
        description = "분석 가능한 요식업 업종 목록을 정렬 순서대로 조회함."
    )
    fun getBusinessTypes(): ResponseEntity<ApiResponse<*>> {
        val data = businessTypeFacade.getBusinessTypes().map(::toResponse)
        return ResponseEntity.ok(ApiResponse(data))
    }

    private fun toResponse(dto: BusinessTypeDto): BusinessTypeResponse {
        return BusinessTypeResponse(
            key = dto.key,
            label = dto.label,
            emoji = dto.emoji,
            sortOrder = dto.sortOrder
        )
    }
}
