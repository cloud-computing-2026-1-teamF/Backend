package com.sanggwonai.api.area.facade

import com.sanggwonai.api.area.dto.AreaSearchResponseDto
import com.sanggwonai.api.area.service.AreaService
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import org.springframework.stereotype.Component

@Component
class AreaFacade(
    private val areaService: AreaService
) {
    fun search(query: String?, limit: Int?): List<AreaSearchResponseDto> {
        val normalizedQuery = query?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw ApiException.of(
                errorType = ErrorType.QUERY_REQUIRED,
                details = mapOf("q" to "required")
            )

        val safeLimit = (limit ?: 10).coerceIn(1, 50)
        return areaService.search(normalizedQuery, safeLimit)
    }
}
