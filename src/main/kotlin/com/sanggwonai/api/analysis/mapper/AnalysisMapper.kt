package com.sanggwonai.api.analysis.mapper

import com.sanggwonai.api.analysis.dto.AnalysisErrorDto
import com.sanggwonai.api.analysis.dto.AnalysisEventDto
import com.sanggwonai.api.analysis.dto.AnalysisPollingData
import com.sanggwonai.api.analysis.dto.AnalysisStepDto
import com.sanggwonai.api.analysis.entity.AnalysisEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface AnalysisMapper {

    @Mapping(target = "status", expression = "java(entity.getStatus().name().toLowerCase())")
    @Mapping(target = "step", expression = "java(toStep(entity))")
    @Mapping(target = "error", expression = "java(toError(entity))")
    fun toPollingData(entity: AnalysisEntity): AnalysisPollingData

    @Mapping(target = "status", expression = "java(entity.getStatus().name().toLowerCase())")
    @Mapping(target = "step", expression = "java(toStep(entity))")
    @Mapping(target = "error", expression = "java(toError(entity))")
    fun toEventData(entity: AnalysisEntity): AnalysisEventDto

    fun toStep(entity: AnalysisEntity): AnalysisStepDto? {
        if (entity.stepIndex == null || entity.stepTotal == null || entity.stepLabel == null) {
            return null
        }
        return AnalysisStepDto(
            index = entity.stepIndex!!,
            total = entity.stepTotal!!,
            label = entity.stepLabel!!
        )
    }

    fun toError(entity: AnalysisEntity): AnalysisErrorDto? {
        if (entity.errorCode.isNullOrBlank() || entity.errorMessage.isNullOrBlank()) {
            return null
        }
        return AnalysisErrorDto(
            code = entity.errorCode!!,
            message = entity.errorMessage!!
        )
    }
}
