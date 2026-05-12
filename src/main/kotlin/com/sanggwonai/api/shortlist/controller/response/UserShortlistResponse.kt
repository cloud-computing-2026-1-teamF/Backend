package com.sanggwonai.api.shortlist.controller.response

import java.time.Instant

data class UserShortlistResponse(
    val vacancyId: String,
    val createdAt: Instant
)

data class UserShortlistListResponse(
    val items: List<UserShortlistResponse>
)
