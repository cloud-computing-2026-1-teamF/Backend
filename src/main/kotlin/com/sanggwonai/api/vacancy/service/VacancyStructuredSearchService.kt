package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyCategoryFilter
import com.sanggwonai.api.vacancy.dto.VacancyExplorerResult
import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VacancyStructuredSearchService(
    private val candidateQuery: VacancyStructuredCandidateQuery,
    private val vacancyService: VacancyService,
    private val promptService: VacancyPromptService
) {
    @Transactional(readOnly = true)
    fun search(filters: VacancyStructuredFilter): VacancyExplorerResult {
        val normalized = resolveCategory(filters.normalized())
        val candidateIds = candidateQuery.findCandidateIds(normalized)
        return vacancyService.search(
            criteria = normalized.toExplorerCriteria(),
            candidateIds = candidateIds
        )
    }

    @Transactional(readOnly = true)
    fun parsePrompt(prompt: String, allowOpenAi: Boolean): VacancyPromptParseResult {
        val parsed = promptService.parse(prompt, allowOpenAi)
        return parsed.copy(filters = resolveCategory(parsed.filters.normalized()))
    }

    @Transactional(readOnly = true)
    fun searchPrompt(prompt: String, page: Int?, size: Int?, allowOpenAi: Boolean): Pair<VacancyPromptParseResult, VacancyExplorerResult> {
        val parsed = parsePrompt(prompt, allowOpenAi)
        val filters = parsed.filters.copy(
            page = page ?: parsed.filters.page,
            size = size ?: parsed.filters.size
        ).normalized()
        return parsed.copy(filters = filters) to search(filters)
    }

    private fun resolveCategory(filters: VacancyStructuredFilter): VacancyStructuredFilter {
        val category = filters.category ?: return filters
        if (!category.categoryId.isNullOrBlank()) return filters
        val categoryId = category.categoryLabel?.let { label ->
            VacancyPromptSchema.categories.entries.firstOrNull { (_, categoryLabel) -> categoryLabel == label }?.key
        } ?: return filters
        return filters.copy(
            category = category.copy(
                categoryId = categoryId,
                scoreMode = category.scoreMode ?: "category"
            )
        )
    }
}
