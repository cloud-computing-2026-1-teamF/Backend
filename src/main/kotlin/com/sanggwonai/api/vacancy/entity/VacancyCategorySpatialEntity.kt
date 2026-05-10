package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "vacancy_category_spatial")
class VacancyCategorySpatialEntity(
    @EmbeddedId
    val id: VacancyCategoryKey,

    @Column(name = "동종_식당수_250m")
    val sameCategoryRestaurantCount250m: Int?,

    @Column(name = "동종_식당수_500m")
    val sameCategoryRestaurantCount500m: Int?,

    @Column(name = "동종_식당수_1000m")
    val sameCategoryRestaurantCount1000m: Int?,

    @Column(name = "업종성장률_250m", precision = 12, scale = 8)
    val industryGrowthRate250m: BigDecimal?,

    @Column(name = "업종성장률_500m", precision = 12, scale = 8)
    val industryGrowthRate500m: BigDecimal?,

    @Column(name = "업종성장률_1000m", precision = 12, scale = 8)
    val industryGrowthRate1000m: BigDecimal?
)
