package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "vacancies")
class VacancyEntity(
    @Id
    @Column(nullable = false, length = 40)
    val id: String,

    @Column(name = "area_id", nullable = false, length = 20)
    val areaId: String,

    @Column(name = "floating_population_annual_total")
    val floatingPopulationAnnualTotal: Long?,

    @Column(name = "resident_population_annual_total")
    val residentPopulationAnnualTotal: Long?,

    @Column(name = "worker_population_annual_total")
    val workerPopulationAnnualTotal: Long?,

    @Column(name = "floating_population_quarterly_average", precision = 18, scale = 2)
    val floatingPopulationQuarterlyAverage: BigDecimal?,

    @Column(name = "resident_population_quarterly_average", precision = 18, scale = 2)
    val residentPopulationQuarterlyAverage: BigDecimal?,

    @Column(name = "worker_population_quarterly_average", precision = 18, scale = 2)
    val workerPopulationQuarterlyAverage: BigDecimal?,

    @Column(name = "restaurant_count_250m")
    val restaurantCount250m: Int?,

    @Column(name = "cafe_count_250m")
    val cafeCount250m: Int?,

    @Column(name = "industry_growth_rate_250m", precision = 10, scale = 4)
    val industryGrowthRate250m: BigDecimal?,

    @Column(name = "restaurant_count_500m")
    val restaurantCount500m: Int?,

    @Column(name = "cafe_count_500m")
    val cafeCount500m: Int?,

    @Column(name = "industry_growth_rate_500m", precision = 10, scale = 4)
    val industryGrowthRate500m: BigDecimal?,

    @Column(name = "restaurant_count_1000m")
    val restaurantCount1000m: Int?,

    @Column(name = "cafe_count_1000m")
    val cafeCount1000m: Int?,

    @Column(name = "industry_growth_rate_1000m", precision = 10, scale = 4)
    val industryGrowthRate1000m: BigDecimal?,

    @Column(length = 100)
    val category: String?,

    @Column(name = "business_middle_category_name", length = 150)
    val businessMiddleCategoryName: String?,

    @Column(name = "business_sub_category_name", length = 150)
    val businessSubCategoryName: String?,

    @Column(name = "multi_use_facility")
    val multiUseFacility: Boolean?,

    @Column(name = "facility_total_size", precision = 14, scale = 2)
    val facilityTotalSize: BigDecimal?,

    @Column(name = "location_area", precision = 14, scale = 2)
    val locationArea: BigDecimal?,

    @Column(name = "evening_population_ratio", precision = 10, scale = 4)
    val eveningPopulationRatio: BigDecimal?,

    @Column(name = "late_night_population_ratio", precision = 10, scale = 4)
    val lateNightPopulationRatio: BigDecimal?,

    @Column(name = "morning_population_ratio", precision = 10, scale = 4)
    val morningPopulationRatio: BigDecimal?,

    @Column(name = "weekend_population_ratio", precision = 10, scale = 4)
    val weekendPopulationRatio: BigDecimal?,

    @Column(name = "age_20_30_population_ratio", precision = 10, scale = 4)
    val age2030PopulationRatio: BigDecimal?,

    @Column(name = "age_40_plus_population_ratio", precision = 10, scale = 4)
    val age40PlusPopulationRatio: BigDecimal?,

    @Column(name = "female_population_ratio", precision = 10, scale = 4)
    val femalePopulationRatio: BigDecimal?,

    @Column(name = "resident_to_floating_ratio", precision = 10, scale = 4)
    val residentToFloatingRatio: BigDecimal?,

    @Column(name = "worker_to_floating_ratio", precision = 10, scale = 4)
    val workerToFloatingRatio: BigDecimal?,

    @Column(name = "official_land_price", precision = 18, scale = 2)
    val officialLandPrice: BigDecimal?,

    @Column(name = "closure_rate", precision = 10, scale = 4)
    val closureRate: BigDecimal?,

    @Column(name = "opening_rate", precision = 10, scale = 4)
    val openingRate: BigDecimal?,

    @Column(name = "average_sales_per_store", precision = 18, scale = 2)
    val averageSalesPerStore: BigDecimal?,

    @Column(name = "time_based_sales_ratio", precision = 10, scale = 4)
    val timeBasedSalesRatio: BigDecimal?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)
