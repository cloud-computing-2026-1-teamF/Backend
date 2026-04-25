package com.sanggwonai.api.area.service

import com.sanggwonai.api.area.dto.AreaSearchResponseDto
import com.sanggwonai.api.area.mapper.AreaMapper
import com.sanggwonai.api.area.repository.AreaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AreaService(
    private val areaRepository: AreaRepository,
    private val areaMapper: AreaMapper
) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int): List<AreaSearchResponseDto> {
        return areaRepository.search(query)
            .take(limit)
            .map(areaMapper::toDto)
    }
}
