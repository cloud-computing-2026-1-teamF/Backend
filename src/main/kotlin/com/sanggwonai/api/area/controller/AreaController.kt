package com.sanggwonai.api.area.controller

import com.sanggwonai.api.area.controller.request.AreaSearchRequest
import com.sanggwonai.api.area.controller.response.AreaCenterResponse
import com.sanggwonai.api.area.controller.response.AreaSearchResponse
import com.sanggwonai.api.area.dto.AreaCenterDto
import com.sanggwonai.api.area.dto.AreaSearchResponseDto
import com.sanggwonai.api.area.facade.AreaFacade
import com.sanggwonai.api.common.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/areas")
class AreaController(
    private val areaFacade: AreaFacade
) {

    @GetMapping("/search")
    fun search(
        @ModelAttribute request: AreaSearchRequest
    ): ResponseEntity<ApiResponse<*>> {
        val data = areaFacade.search(request.q, request.limit).map(::toResponse)
        return ResponseEntity.ok(ApiResponse(data))
    }

    private fun toResponse(dto: AreaSearchResponseDto): AreaSearchResponse {
        return AreaSearchResponse(
            id = dto.id,
            name = dto.name,
            region = dto.region,
            fullName = dto.fullName,
            center = toResponse(dto.center)
        )
    }

    private fun toResponse(dto: AreaCenterDto): AreaCenterResponse {
        return AreaCenterResponse(
            lat = dto.lat,
            lng = dto.lng
        )
    }
}
