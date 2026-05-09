package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyRepository : JpaRepository<VacancyEntity, String> {
    fun findFirstByAreaIdOrderByIdAsc(areaId: String): VacancyEntity?

    fun findAllByAreaIdOrderByIdAsc(areaId: String): List<VacancyEntity>

    fun findAllByOrderByIdAsc(): List<VacancyEntity>
}
