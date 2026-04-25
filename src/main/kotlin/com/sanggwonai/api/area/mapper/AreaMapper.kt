package com.sanggwonai.api.area.mapper

import com.sanggwonai.api.area.dto.AreaSearchResponseDto
import com.sanggwonai.api.area.dto.CenterDto
import com.sanggwonai.api.area.entity.AreaEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface AreaMapper {

    @Mapping(target = "center", expression = "java(new CenterDto(area.getCenterLat().doubleValue(), area.getCenterLng().doubleValue()))")
    fun toDto(area: AreaEntity): AreaSearchResponseDto
}
