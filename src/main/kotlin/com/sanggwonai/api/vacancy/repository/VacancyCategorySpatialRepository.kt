package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyCategoryKey
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyCategorySpatialRepository : JpaRepository<VacancyCategorySpatialEntity, VacancyCategoryKey> {
    fun findAllByIdCategoryId(categoryId: String): List<VacancyCategorySpatialEntity>
}
