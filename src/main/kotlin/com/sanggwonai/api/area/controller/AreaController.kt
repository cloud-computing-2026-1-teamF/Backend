package com.sanggwonai.api.area.controller

import com.sanggwonai.api.area.facade.AreaFacade
import com.sanggwonai.api.common.api.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/areas")
class AreaController(
    private val areaFacade: AreaFacade
) {

    @GetMapping("/search")
    fun search(
        @RequestParam("q", required = false) query: String?,
        @RequestParam("limit", required = false) limit: Int?
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse(areaFacade.search(query, limit)))
    }
}
