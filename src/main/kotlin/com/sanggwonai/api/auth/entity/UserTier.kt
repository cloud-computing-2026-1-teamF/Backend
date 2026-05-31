package com.sanggwonai.api.auth.entity

enum class UserTier {
    FREE,
    PRO,
    BUSINESS;

    fun canUseLlmPrompt(): Boolean = this == PRO || this == BUSINESS
}
