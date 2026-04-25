package com.sanggwonai.api.auth.mapper

import com.sanggwonai.api.auth.dto.UserDto
import com.sanggwonai.api.auth.entity.UserEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface UserMapper {

    @Mapping(target = "tier", expression = "java(user.getTier().name().toLowerCase())")
    fun toDto(user: UserEntity): UserDto
}
