package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyCategoryKey
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyCategoryScoreRepository : JpaRepository<VacancyCategoryScoreEntity, VacancyCategoryKey> {
    fun findAllByIdCategoryId(categoryId: String): List<VacancyCategoryScoreEntity>
}
