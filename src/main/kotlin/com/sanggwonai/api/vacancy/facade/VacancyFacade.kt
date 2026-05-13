package com.sanggwonai.api.vacancy.facade

import com.sanggwonai.api.vacancy.dto.VacancyExplorerCriteria
import com.sanggwonai.api.vacancy.service.VacancyService
import org.springframework.stereotype.Component

@Component
class VacancyFacade(
    private val vacancyService: VacancyService
) {
    fun list(areaId: String?) = vacancyService.list(areaId)

    fun search(criteria: VacancyExplorerCriteria) = vacancyService.search(criteria)

    fun get(id: String) = vacancyService.get(id)

    fun metricReference(categoryId: String?, vacancyId: String?) =
        vacancyService.metricReference(categoryId, vacancyId)
}
