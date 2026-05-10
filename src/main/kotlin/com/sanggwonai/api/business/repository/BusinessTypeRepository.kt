package com.sanggwonai.api.business.repository

import com.sanggwonai.api.business.entity.BusinessTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BusinessTypeRepository : JpaRepository<BusinessTypeEntity, String> {
    fun findAllByOrderByBusinessKeyAsc(): List<BusinessTypeEntity>
}
