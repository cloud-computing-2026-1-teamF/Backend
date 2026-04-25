package com.sanggwonai.api.business.mapper

import com.sanggwonai.api.business.dto.BusinessTypeDto
import com.sanggwonai.api.business.entity.BusinessTypeEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface BusinessTypeMapper {
    @Mapping(target = "key", source = "businessKey")
    fun toDto(entity: BusinessTypeEntity): BusinessTypeDto
}
