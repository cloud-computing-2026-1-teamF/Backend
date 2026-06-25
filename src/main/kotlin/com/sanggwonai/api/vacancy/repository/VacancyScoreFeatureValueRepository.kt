package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureValueEntity
import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureValueKey
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyScoreFeatureValueRepository :
    JpaRepository<VacancyScoreFeatureValueEntity, VacancyScoreFeatureValueKey>
