package com.sanggwonai.api.business.service

import com.sanggwonai.api.business.dto.BusinessTypeDto
import com.sanggwonai.api.business.mapper.BusinessTypeMapper
import com.sanggwonai.api.business.repository.BusinessTypeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BusinessTypeService(
    private val businessTypeRepository: BusinessTypeRepository,
    private val businessTypeMapper: BusinessTypeMapper
) {
    @Transactional(readOnly = true)
    fun getBusinessTypes(): List<BusinessTypeDto> {
        return businessTypeRepository.findAllByOrderBySortOrderAsc()
            .map(businessTypeMapper::toDto)
    }
}
