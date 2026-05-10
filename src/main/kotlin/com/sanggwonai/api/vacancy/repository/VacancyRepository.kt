package com.sanggwonai.api.vacancy.repository

import com.sanggwonai.api.vacancy.entity.VacancyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface VacancyRepository : JpaRepository<VacancyEntity, String>, JpaSpecificationExecutor<VacancyEntity> {
    fun findFirstByAreaIdOrderByIdAsc(areaId: String): VacancyEntity?

    fun findAllByAreaIdOrderByIdAsc(areaId: String): List<VacancyEntity>

    fun findAllByOrderByIdAsc(): List<VacancyEntity>

    @Query(
        """
        select v from VacancyEntity v
        where v.areaId = :areaId
          and v.latitude is not null
          and v.longitude is not null
          and (:rentMax is null or (v.monthlyRent is not null and v.monthlyRent <= :rentMax))
          and (:depositMax is null or (v.deposit is not null and v.deposit <= :depositMax))
          and (:maintenanceFeeMax is null or (v.maintenanceFee is not null and v.maintenanceFee <= :maintenanceFeeMax))
        """
    )
    fun findBudgetAndLocationCandidates(
        @Param("areaId") areaId: String,
        @Param("rentMax") rentMax: Long?,
        @Param("depositMax") depositMax: Long?,
        @Param("maintenanceFeeMax") maintenanceFeeMax: Long?
    ): List<VacancyEntity>
}
