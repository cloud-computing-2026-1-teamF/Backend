package com.sanggwonai.api.business.service

import com.sanggwonai.api.business.dto.BusinessTypeDto
import com.sanggwonai.api.business.repository.BusinessTypeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BusinessTypeService(
    private val businessTypeRepository: BusinessTypeRepository
) {
    @Transactional(readOnly = true)
    fun getBusinessTypes(): List<BusinessTypeDto> {
        return businessTypeRepository.findAllByOrderByBusinessKeyAsc()
            .sortedBy { it.businessKey.toIntOrNull() ?: Int.MAX_VALUE }
            .mapIndexed { index, entity ->
                BusinessTypeDto(
                    key = entity.businessKey,
                    label = entity.label,
                    emoji = CATEGORY_EMOJI[entity.businessKey] ?: "🍽",
                    sortOrder = entity.businessKey.toIntOrNull() ?: index + 1
                )
            }
    }

    companion object {
        private val CATEGORY_EMOJI = mapOf(
            "1" to "🍚",
            "2" to "🥟",
            "3" to "🍣",
            "4" to "🍝",
            "5" to "🍽",
            "6" to "🥘",
            "7" to "🍔",
            "8" to "🍻",
            "9" to "☕"
        )
    }
}
