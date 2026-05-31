package com.sanggwonai.api.vacancy.facade

import com.sanggwonai.api.auth.repository.UserRepository
import com.sanggwonai.api.auth.service.AuthContextResolver
import com.sanggwonai.api.vacancy.dto.VacancyExplorerCriteria
import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter
import com.sanggwonai.api.vacancy.service.VacancyService
import com.sanggwonai.api.vacancy.service.VacancyStructuredSearchService
import org.springframework.stereotype.Component

@Component
class VacancyFacade(
    private val vacancyService: VacancyService,
    private val structuredSearchService: VacancyStructuredSearchService,
    private val authContextResolver: AuthContextResolver,
    private val userRepository: UserRepository
) {
    fun list(areaId: String?) = vacancyService.list(areaId)

    fun search(criteria: VacancyExplorerCriteria) = vacancyService.search(criteria)

    fun structuredSearch(filters: VacancyStructuredFilter) = structuredSearchService.search(filters)

    fun parsePrompt(authorizationHeader: String?, prompt: String) =
        structuredSearchService.parsePrompt(prompt, canUseLlmPrompt(authorizationHeader))

    fun promptSearch(authorizationHeader: String?, prompt: String, page: Int?, size: Int?) =
        structuredSearchService.searchPrompt(prompt, page, size, canUseLlmPrompt(authorizationHeader))

    fun get(id: String) = vacancyService.get(id)

    fun metricReference(categoryId: String?, vacancyId: String?) =
        vacancyService.metricReference(categoryId, vacancyId)

    private fun canUseLlmPrompt(authorizationHeader: String?): Boolean {
        val authContext = authContextResolver.resolveOrNull(authorizationHeader) ?: return false
        val user = userRepository.findById(authContext.userId).orElse(null) ?: return false
        return user.tier.canUseLlmPrompt()
    }
}
