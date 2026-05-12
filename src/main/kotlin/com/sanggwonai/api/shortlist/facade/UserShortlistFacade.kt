package com.sanggwonai.api.shortlist.facade

import com.sanggwonai.api.auth.service.AuthContextResolver
import com.sanggwonai.api.shortlist.dto.UserShortlistData
import com.sanggwonai.api.shortlist.service.UserShortlistService
import org.springframework.stereotype.Component

@Component
class UserShortlistFacade(
    private val shortlistService: UserShortlistService,
    private val authContextResolver: AuthContextResolver
) {
    fun list(authorizationHeader: String?): List<UserShortlistData> =
        shortlistService.list(authContextResolver.resolveOrThrow(authorizationHeader))

    fun add(authorizationHeader: String?, vacancyId: String): UserShortlistData =
        shortlistService.add(authContextResolver.resolveOrThrow(authorizationHeader), vacancyId)

    fun remove(authorizationHeader: String?, vacancyId: String) =
        shortlistService.remove(authContextResolver.resolveOrThrow(authorizationHeader), vacancyId)
}
