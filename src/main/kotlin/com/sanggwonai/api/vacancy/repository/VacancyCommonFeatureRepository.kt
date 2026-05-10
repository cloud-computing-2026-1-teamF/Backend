package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VacancyCommonFeatureRepository : JpaRepository<VacancyCommonFeatureEntity, String>
