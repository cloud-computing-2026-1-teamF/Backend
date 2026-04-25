package com.sanggwonai.api.area.repository

import com.sanggwonai.api.area.entity.AreaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AreaRepository : JpaRepository<AreaEntity, String> {

    @Query(
        """
        select a from AreaEntity a
        where lower(a.name) like lower(concat('%', :keyword, '%'))
           or lower(a.region) like lower(concat('%', :keyword, '%'))
           or lower(a.fullName) like lower(concat('%', :keyword, '%'))
        order by a.name asc
        """
    )
    fun search(@Param("keyword") keyword: String): List<AreaEntity>
}
