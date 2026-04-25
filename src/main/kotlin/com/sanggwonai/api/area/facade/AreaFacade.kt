package com.sanggwonai.api.area.facade

import com.sanggwonai.api.area.dto.AreaSearchResponseDto
import com.sanggwonai.api.area.service.AreaService
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class AreaFacade(
    private val areaService: AreaService
) {
    fun search(query: String?, limit: Int?): List<AreaSearchResponseDto> {
        val normalizedQuery = query?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw ApiException(
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                code = ErrorCode.VALIDATION_FAILED,
                message = "q 파라미터가 필요해요",
                details = mapOf("q" to "required")
            )

        val safeLimit = (limit ?: 10).coerceIn(1, 50)
        return areaService.search(normalizedQuery, safeLimit)
    }
}
