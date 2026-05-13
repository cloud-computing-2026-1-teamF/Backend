package com.sanggwonai.api.area.repository

import com.sanggwonai.api.area.entity.AreaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AreaRepository : JpaRepository<AreaEntity, String> {

    @Query(
        value = """
        select *
        from areas a
        where lower(a.name) like ('%' || lower(:keyword) || '%')
           or lower(a.region) like ('%' || lower(:keyword) || '%')
           or lower(a.full_name) like ('%' || lower(:keyword) || '%')
        order by a.name asc
        limit :limit
        """,
        nativeQuery = true
    )
    fun search(@Param("keyword") keyword: String, @Param("limit") limit: Int): List<AreaEntity>
}
