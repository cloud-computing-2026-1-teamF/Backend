package com.sanggwonai.api.business.controller

import com.sanggwonai.api.business.controller.response.BusinessTypeResponse
import com.sanggwonai.api.business.dto.BusinessTypeDto
import com.sanggwonai.api.business.facade.BusinessTypeFacade
import com.sanggwonai.api.common.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/business-types")
class BusinessTypeController(
    private val businessTypeFacade: BusinessTypeFacade
) {
    @GetMapping
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
