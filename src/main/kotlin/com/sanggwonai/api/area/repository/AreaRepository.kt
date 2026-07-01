package com.sanggwonai.api.area.repository

import com.sanggwonai.api.area.entity.AreaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AreaRepository : JpaRepository<AreaEntity, String> {

    @Query(
        value = """
        with search_input as (
            select
                lower(:keyword) as keyword,
                regexp_replace(lower(:keyword), '[[:space:].·ㆍ-]+', '', 'g') as normalized_keyword
        )
        select a.*
        from areas a
        cross join search_input s
        where lower(a.name) like ('%' || lower(:keyword) || '%')
           or lower(a.region) like ('%' || lower(:keyword) || '%')
           or lower(a.full_name) like ('%' || lower(:keyword) || '%')
           or regexp_replace(lower(a.name), '[[:space:].·ㆍ-]+', '', 'g') like ('%' || s.normalized_keyword || '%')
           or regexp_replace(lower(a.region), '[[:space:].·ㆍ-]+', '', 'g') like ('%' || s.normalized_keyword || '%')
           or regexp_replace(lower(a.full_name), '[[:space:].·ㆍ-]+', '', 'g') like ('%' || s.normalized_keyword || '%')
           or exists (
                select 1
                from vacancy_common_features cf
                join vacancies v on v.property_id = cf.property_id
                where cf."행정동_행정동_코드" = a.id
                  and (
                    lower(coalesce(v."동", '')) like ('%' || s.keyword || '%')
                    or lower(coalesce(v."지번주소", '')) like ('%' || s.keyword || '%')
                    or lower(coalesce(v."도로명주소", '')) like ('%' || s.keyword || '%')
                    or regexp_replace(lower(coalesce(v."동", '')), '[[:space:].·ㆍ-]+', '', 'g') like ('%' || s.normalized_keyword || '%')
                    or regexp_replace(lower(coalesce(v."지번주소", '')), '[[:space:].·ㆍ-]+', '', 'g') like ('%' || s.normalized_keyword || '%')
                    or regexp_replace(lower(coalesce(v."도로명주소", '')), '[[:space:].·ㆍ-]+', '', 'g') like ('%' || s.normalized_keyword || '%')
                  )
           )
        order by a.name asc
        limit :limit
        """,
        nativeQuery = true
    )
    fun search(@Param("keyword") keyword: String, @Param("limit") limit: Int): List<AreaEntity>
}
