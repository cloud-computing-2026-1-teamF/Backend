package com.sanggwonai.api.common.api

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "상권 AI Backend API",
        version = "v1",
        description = "프론트엔드 연동용 API 문서임. 엔드포인트별 의도와 사용 맥락을 빠르게 파악하도록 정리함."
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class SwaggerConfig
