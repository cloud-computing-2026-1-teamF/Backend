package com.sanggwonai.api.business.facade

import com.sanggwonai.api.business.dto.BusinessTypeDto
import com.sanggwonai.api.business.service.BusinessTypeService
import org.springframework.stereotype.Component

@Component
class BusinessTypeFacade(
    private val businessTypeService: BusinessTypeService
) {
    fun getBusinessTypes(): List<BusinessTypeDto> = businessTypeService.getBusinessTypes()
}
