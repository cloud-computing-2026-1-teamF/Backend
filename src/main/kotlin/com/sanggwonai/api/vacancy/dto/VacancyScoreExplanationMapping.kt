package com.sanggwonai.api.vacancy.dto

import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreExplanationEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureBenchmarkEntity
import com.sanggwonai.api.vacancy.entity.VacancyScoreFeatureValueEntity
import java.math.BigDecimal
import java.math.RoundingMode

fun toScoreExplanationDto(
    entities: List<VacancyCategoryScoreExplanationEntity>,
    benchmarksByKey: Map<String, VacancyScoreFeatureBenchmarkEntity>,
    featureValuesByKey: Map<String, VacancyScoreFeatureValueEntity> = emptyMap(),
    vacancy: VacancyEntity,
    common: VacancyCommonFeatureEntity?,
    spatial: VacancyCategorySpatialEntity?,
    scorePercent: BigDecimal? = null
): VacancyScoreExplanationDto? {
    if (entities.isEmpty()) return null

    val positiveFeatures = entities
        .filter { it.id.explanationTone == POSITIVE_TONE }
        .sortedBy { it.id.featureRank }
        .take(3)
        .map { entity ->
            entity.toFeatureDto(
                rank = entity.id.featureRank.toInt(),
                benchmarksByKey = benchmarksByKey,
                featureValuesByKey = featureValuesByKey,
                vacancy = vacancy,
                common = common,
                spatial = spatial
            )
        }

    val negativeFeatures = entities
        .filter { it.id.explanationTone == NEGATIVE_TONE }
        .sortedBy { it.id.featureRank }
        .take(3)
        .map { entity ->
            entity.toFeatureDto(
                rank = entity.id.featureRank.toInt(),
                benchmarksByKey = benchmarksByKey,
                featureValuesByKey = featureValuesByKey,
                vacancy = vacancy,
                common = common,
                spatial = spatial
            )
        }

    val features = if (positiveFeatures.isNotEmpty() || negativeFeatures.isNotEmpty()) {
        selectScoreBandFeatures(scorePercent, positiveFeatures, negativeFeatures)
    } else {
        entities
            .sortedBy { it.id.featureRank }
            .take(5)
            .map { entity ->
                entity.toFeatureDto(
                    rank = entity.id.featureRank.toInt(),
                    benchmarksByKey = benchmarksByKey,
                    featureValuesByKey = featureValuesByKey,
                    vacancy = vacancy,
                    common = common,
                    spatial = spatial
                )
            }
    }
    if (features.isEmpty()) return null

    val source = entities
        .map { it.source }
        .distinct()
        .let { sources -> if (sources.size == 1) sources.first() else "mixed" }

    return VacancyScoreExplanationDto(
        features = features,
        positiveFeatures = positiveFeatures,
        negativeFeatures = negativeFeatures,
        source = source
    )
}

private fun VacancyCategoryScoreExplanationEntity.toFeatureDto(
    rank: Int,
    benchmarksByKey: Map<String, VacancyScoreFeatureBenchmarkEntity>,
    featureValuesByKey: Map<String, VacancyScoreFeatureValueEntity>,
    vacancy: VacancyEntity,
    common: VacancyCommonFeatureEntity?,
    spatial: VacancyCategorySpatialEntity?
): VacancyScoreFeatureDto {
    val benchmark = benchmarksByKey[featureKey]
    val featureValue = featureValuesByKey[featureKey]
    val currentValue = featureValue?.currentValue ?: currentFeatureValue(featureKey, vacancy, common, spatial)
    val averageValue = featureValue?.averageValue ?: benchmark?.averageValue
    return VacancyScoreFeatureDto(
        rank = rank,
        sourceRank = id.featureRank.toInt(),
        sourceTone = id.explanationTone,
        featureKey = featureKey,
        featureLabel = benchmark?.featureLabel ?: featureKey,
        effect = explicitEffect(id.explanationTone) ?: inferEffect(currentValue, benchmark),
        currentValue = currentValue,
        averageValue = averageValue,
        displayUnit = benchmark?.displayUnit,
        higherIsPositive = benchmark?.higherIsPositive,
        contributionLogOdds = contributionLogOdds,
        contributionPp = contributionPp,
        percentileLabel = percentileLabel,
        normalizedImpact = normalizedImpact,
        impactPercentile = impactPercentile,
        valuePercentile = featureValue?.valuePercentile,
        valuePercentileLabel = featureValue?.valuePercentileLabel
    )
}

private fun selectScoreBandFeatures(
    scorePercent: BigDecimal?,
    positiveFeatures: List<VacancyScoreFeatureDto>,
    negativeFeatures: List<VacancyScoreFeatureDto>
): List<VacancyScoreFeatureDto> {
    val score = scorePercent ?: BigDecimal.ZERO
    val positiveCount = when {
        score > NINETY -> 3
        score > EIGHTY -> 2
        score > SEVENTY -> 1
        else -> 0
    }
    val negativeCount = 3 - positiveCount
    return (positiveFeatures.take(positiveCount) + negativeFeatures.take(negativeCount))
        .mapIndexed { index, feature -> feature.copy(rank = index + 1) }
}

private fun explicitEffect(tone: String): String? {
    return when (tone) {
        POSITIVE_TONE -> "positive"
        NEGATIVE_TONE -> "negative"
        else -> null
    }
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
private val SEVENTY = BigDecimal("70")
private val EIGHTY = BigDecimal("80")
private val NINETY = BigDecimal("90")
private const val POSITIVE_TONE = "positive"
private const val NEGATIVE_TONE = "negative"
