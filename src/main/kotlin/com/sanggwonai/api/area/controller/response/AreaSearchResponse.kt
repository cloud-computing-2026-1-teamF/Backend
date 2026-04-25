package com.sanggwonai.api.area.controller.response

data class AreaSearchResponse(
    val id: String,
    val name: String,
    val region: String,
    val fullName: String,
    val center: AreaCenterResponse
)
