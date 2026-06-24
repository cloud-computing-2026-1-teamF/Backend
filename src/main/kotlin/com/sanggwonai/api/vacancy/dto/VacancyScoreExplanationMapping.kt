package com.sanggwonai.api.vacancy.dto

import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreExplanationEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureBenchmarkEntity
import java.math.BigDecimal
import java.math.RoundingMode

fun toScoreExplanationDto(
    entities: List<VacancyCategoryScoreExplanationEntity>,
    benchmarksByKey: Map<String, VacancyScoreFeatureBenchmarkEntity>,
    vacancy: VacancyEntity,
    common: VacancyCommonFeatureEntity?,
    spatial: VacancyCategorySpatialEntity?
): VacancyScoreExplanationDto? {
    if (entities.isEmpty()) return null

    val features = entities
        .sortedBy { it.id.featureRank }
        .take(5)
        .map { entity ->
            val benchmark = benchmarksByKey[entity.featureKey]
            val currentValue = currentFeatureValue(entity.featureKey, vacancy, common, spatial)
            VacancyScoreFeatureDto(
                rank = entity.id.featureRank.toInt(),
                featureKey = entity.featureKey,
                featureLabel = benchmark?.featureLabel ?: entity.featureKey,
                effect = inferEffect(currentValue, benchmark),
                currentValue = currentValue,
                averageValue = benchmark?.averageValue,
                displayUnit = benchmark?.displayUnit,
                higherIsPositive = benchmark?.higherIsPositive
            )
        }
    if (features.isEmpty()) return null

    val source = entities
        .map { it.source }
        .distinct()
        .let { sources -> if (sources.size == 1) sources.first() else "mixed" }

    return VacancyScoreExplanationDto(
        features = features,
        source = source
    )
}

private fun inferEffect(
    currentValue: BigDecimal?,
    benchmark: VacancyScoreFeatureBenchmarkEntity?
): String {
    if (currentValue == null || benchmark == null) return "unknown"
    val comparison = currentValue.compareTo(benchmark.averageValue)
    if (comparison == 0) return "neutral"
    val higherThanAverage = comparison > 0
    return if (higherThanAverage == benchmark.higherIsPositive) "positive" else "negative"
}

private fun currentFeatureValue(
    featureKey: String,
    vacancy: VacancyEntity,
    common: VacancyCommonFeatureEntity?,
    spatial: VacancyCategorySpatialEntity?
): BigDecimal? {
    return when (featureKey) {
        "monthly_rent" -> vacancy.monthlyRent.toDecimal()
        "deposit" -> vacancy.deposit.toDecimal()
        "maintenance_fee" -> vacancy.maintenanceFee.toDecimal()
        "premium" -> vacancy.premium.toDecimal()
        "sale_price" -> vacancy.salePrice.toDecimal()
        "exclusive_area" -> vacancy.dedicatedArea
        "supply_area" -> vacancy.supplyArea
        "facility_total_size" -> common?.facilityTotalSize
        "location_area" -> common?.locationArea ?: vacancy.dedicatedArea
        "daily_floating_population" -> common?.floatingPopulationAnnualDensity?.divide(DAYS_PER_YEAR, 6, RoundingMode.HALF_UP)
        "evening_foot_traffic" -> common?.eveningPopulationRatio?.multiply(ONE_HUNDRED)
        "weekend_population_ratio" -> common?.weekendPopulationRatio?.multiply(ONE_HUNDRED)
        "age2030_population_ratio" -> common?.age2030PopulationRatio?.multiply(ONE_HUNDRED)
        "female_population_ratio" -> common?.femalePopulationRatio?.multiply(ONE_HUNDRED)
        "sales_per_store" -> common?.averageSalesPerStore?.divide(TEN_THOUSAND, 6, RoundingMode.HALF_UP)
        "closure_rate" -> common?.closureRate
        "opening_rate" -> common?.openingRate
        "restaurant_count_500m" -> common?.restaurantCount500m.toDecimal()
        "cafe_count_500m" -> common?.cafeCount500m.toDecimal()
        "same_category_competition_500m" -> spatial?.sameCategoryRestaurantCount500m.toDecimal()
        "industry_growth_500m" -> spatial?.industryGrowthRate500m?.multiply(ONE_HUNDRED)
        else -> null
    }
}

private fun Long?.toDecimal(): BigDecimal? = this?.let(BigDecimal::valueOf)

private fun Int?.toDecimal(): BigDecimal? = this?.let { BigDecimal.valueOf(it.toLong()) }

private val ONE_HUNDRED = BigDecimal("100")
private val TEN_THOUSAND = BigDecimal("10000")
private val DAYS_PER_YEAR = BigDecimal("365")
