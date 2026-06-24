package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureBenchmarkEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyScoreFeatureBenchmarkRepository :
    JpaRepository<VacancyScoreFeatureBenchmarkEntity, String>
