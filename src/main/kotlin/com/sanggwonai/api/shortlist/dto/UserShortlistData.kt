package com.sanggwonai.api.shortlist.dto

import java.time.Instant

data class UserShortlistData(
    val vacancyId: String,
    val createdAt: Instant
)
