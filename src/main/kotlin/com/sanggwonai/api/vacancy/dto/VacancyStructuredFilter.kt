package com.sanggwonai.api.vacancy.dto

import java.math.BigDecimal

data class VacancyStructuredFilter(
    val q: String? = null,
    val location: VacancyLocationFilter? = null,
    val category: VacancyCategoryFilter? = null,
    val transactionType: String? = null,
    val price: VacancyPriceFilter? = null,
    val space: VacancySpaceFilter? = null,
    val building: VacancyBuildingFilter? = null,
    val amenities: VacancyAmenityFilter? = null,
    val commercial: VacancyCommercialFilter? = null,
    val spatial: VacancySpatialFilter? = null,
    val sort: String? = null,
    val page: Int? = null,
    val size: Int? = null
) {
    fun normalized(): VacancyStructuredFilter {
        val normalizedCategory = category?.normalized()
        return copy(
            q = cleanText(q),
            location = location?.normalized()?.takeUnless { it.empty() },
            category = normalizedCategory?.takeUnless { it.empty() },
            transactionType = normalizeTransactionType(transactionType),
            price = price?.takeUnless { it.empty() },
            space = space?.normalized()?.takeUnless { it.empty() },
            building = building?.normalized()?.takeUnless { it.empty() },
            amenities = amenities?.normalized()?.takeUnless { it.empty() },
            commercial = commercial?.takeUnless { it.empty() },
            spatial = spatial?.takeUnless { it.empty() },
            sort = VacancyExplorerSort.from(sort).wireValue,
            page = page?.coerceAtLeast(0),
            size = size?.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        )
    }

    fun toExplorerCriteria(defaultPage: Int = 0, defaultSize: Int = 20): VacancyExplorerCriteria {
        val normalized = normalized()
        val categoryId = normalized.category?.categoryId
        val scoreMode = normalized.category?.scoreMode
            ?: if (categoryId != null) VacancyScoreMode.Category.wireValue else VacancyScoreMode.Best.wireValue
        val areaMin = normalized.space?.dedicatedAreaMin
            ?: normalized.space?.supplyAreaMin
            ?: normalized.commercial?.locationAreaMin
        val areaMax = normalized.space?.dedicatedAreaMax
            ?: normalized.space?.supplyAreaMax
            ?: normalized.commercial?.locationAreaMax

        return VacancyExplorerCriteria(
            areaId = normalized.location?.areaId,
            categoryId = categoryId,
            scoreMode = VacancyScoreMode.from(scoreMode),
            transactionType = normalized.transactionType,
            q = normalized.q,
            latitude = normalized.location?.latitude,
            longitude = normalized.location?.longitude,
            radiusM = normalized.location?.radiusM,
            rentMax = normalized.price?.monthlyRentMax,
            depositMax = normalized.price?.depositMax,
            maintenanceFeeMax = normalized.price?.maintenanceFeeMax,
            premiumMax = normalized.price?.premiumMax,
            salePriceMax = normalized.price?.salePriceMax,
            scoreMin = normalized.category?.scoreMin,
            areaMin = areaMin,
            areaMax = areaMax,
            page = normalized.page ?: defaultPage,
            size = normalized.size ?: defaultSize,
            sort = VacancyExplorerSort.from(normalized.sort)
        )
    }

    companion object {
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 600
    }
}

data class VacancyLocationFilter(
    val areaId: String? = null,
    val province: String? = null,
    val district: String? = null,
    val dong: String? = null,
    val address: String? = null,
    val subway: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusM: Int? = null
) {
    fun normalized(): VacancyLocationFilter = copy(
        areaId = cleanText(areaId),
        province = cleanText(province),
        district = cleanText(district),
        dong = cleanText(dong),
        address = cleanText(address),
        subway = cleanText(subway),
        radiusM = radiusM?.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
    )

    fun empty(): Boolean {
        return listOf(areaId, province, district, dong, address, subway).all { it.isNullOrBlank() } &&
            latitude == null &&
            longitude == null &&
            radiusM == null
    }

    companion object {
        private const val MIN_RADIUS_M = 1
        private const val MAX_RADIUS_M = 5000
    }
}

data class VacancyCategoryFilter(
    val categoryId: String? = null,
    val categoryLabel: String? = null,
    val scoreMode: String? = null,
    val scoreMin: BigDecimal? = null,
    val recommendedOnly: Boolean? = null
) {
    fun normalized(): VacancyCategoryFilter = copy(
        categoryId = cleanText(categoryId),
        categoryLabel = cleanText(categoryLabel),
        scoreMode = cleanText(scoreMode)?.let { VacancyScoreMode.from(it).wireValue },
        scoreMin = scoreMin
    )

    fun empty(): Boolean {
        return categoryId.isNullOrBlank() &&
            categoryLabel.isNullOrBlank() &&
            scoreMin == null &&
            recommendedOnly == null
    }
}

data class VacancyPriceFilter(
    val monthlyRentMin: Long? = null,
    val monthlyRentMax: Long? = null,
    val depositMin: Long? = null,
    val depositMax: Long? = null,
    val maintenanceFeeMin: Long? = null,
    val maintenanceFeeMax: Long? = null,
    val premiumMin: Long? = null,
    val premiumMax: Long? = null,
    val salePriceMin: Long? = null,
    val salePriceMax: Long? = null,
    val priceNegotiable: Boolean? = null,
    val rentAdjustable: Boolean? = null,
    val rentFreePeriodAvailable: Boolean? = null
) {
    fun empty(): Boolean {
        return monthlyRentMin == null &&
            monthlyRentMax == null &&
            depositMin == null &&
            depositMax == null &&
            maintenanceFeeMin == null &&
            maintenanceFeeMax == null &&
            premiumMin == null &&
            premiumMax == null &&
            salePriceMin == null &&
            salePriceMax == null &&
            priceNegotiable == null &&
            rentAdjustable == null &&
            rentFreePeriodAvailable == null
    }
}

data class VacancySpaceFilter(
    val dedicatedAreaMin: BigDecimal? = null,
    val dedicatedAreaMax: BigDecimal? = null,
    val supplyAreaMin: BigDecimal? = null,
    val supplyAreaMax: BigDecimal? = null,
    val floorText: String? = null,
    val groundFloor: Boolean? = null,
    val basement: Boolean? = null
) {
    fun normalized(): VacancySpaceFilter = copy(floorText = cleanText(floorText))

    fun empty(): Boolean {
        return dedicatedAreaMin == null &&
            dedicatedAreaMax == null &&
            supplyAreaMin == null &&
            supplyAreaMax == null &&
            floorText.isNullOrBlank() &&
            groundFloor == null &&
            basement == null
    }
}

data class VacancyBuildingFilter(
    val buildingName: String? = null,
    val buildingType: String? = null,
    val buildingUse: String? = null,
    val buildingGrade: String? = null,
    val direction: String? = null,
    val approvalDateFrom: String? = null,
    val approvalDateTo: String? = null
) {
    fun normalized(): VacancyBuildingFilter = copy(
        buildingName = cleanText(buildingName),
        buildingType = cleanText(buildingType),
        buildingUse = cleanText(buildingUse),
        buildingGrade = cleanText(buildingGrade),
        direction = cleanText(direction),
        approvalDateFrom = cleanText(approvalDateFrom),
        approvalDateTo = cleanText(approvalDateTo)
    )

    fun empty(): Boolean {
        return listOf(
            buildingName,
            buildingType,
            buildingUse,
            buildingGrade,
            direction,
            approvalDateFrom,
            approvalDateTo
        ).all { it.isNullOrBlank() }
    }
}

data class VacancyAmenityFilter(
    val elevatorAvailable: Boolean? = null,
    val parkingAvailable: Boolean? = null,
    val parkingCountMin: BigDecimal? = null,
    val terrace: Boolean? = null,
    val rooftop: Boolean? = null,
    val interior: Boolean? = null,
    val storage: Boolean? = null,
    val airConditioner: Boolean? = null,
    val heater: Boolean? = null,
    val lateNightOperationAvailable: Boolean? = null,
    val restroomType: String? = null,
    val restroomCountMin: BigDecimal? = null
) {
    fun normalized(): VacancyAmenityFilter = copy(restroomType = cleanText(restroomType))

    fun empty(): Boolean {
        return elevatorAvailable == null &&
            parkingAvailable == null &&
            parkingCountMin == null &&
            terrace == null &&
            rooftop == null &&
            interior == null &&
            storage == null &&
            airConditioner == null &&
            heater == null &&
            lateNightOperationAvailable == null &&
            restroomType.isNullOrBlank() &&
            restroomCountMin == null
    }
}

data class VacancyCommercialFilter(
    val facilityTotalSizeMin: BigDecimal? = null,
    val facilityTotalSizeMax: BigDecimal? = null,
    val locationAreaMin: BigDecimal? = null,
    val locationAreaMax: BigDecimal? = null,
    val multiUseFacility: Boolean? = null,
    val floatingPopulationQuarterlyMin: BigDecimal? = null,
    val residentPopulationQuarterlyMin: BigDecimal? = null,
    val workerPopulationQuarterlyMin: BigDecimal? = null,
    val eveningPopulationRatioMin: BigDecimal? = null,
    val lateNightPopulationRatioMin: BigDecimal? = null,
    val morningPopulationRatioMin: BigDecimal? = null,
    val weekendPopulationRatioMin: BigDecimal? = null,
    val age2030PopulationRatioMin: BigDecimal? = null,
    val age40PlusPopulationRatioMin: BigDecimal? = null,
    val femalePopulationRatioMin: BigDecimal? = null,
    val restaurantCount500mMin: Int? = null,
    val restaurantCount500mMax: Int? = null,
    val cafeCount500mMin: Int? = null,
    val cafeCount500mMax: Int? = null,
    val closureRateMax: BigDecimal? = null,
    val openingRateMin: BigDecimal? = null,
    val averageSalesPerStoreMin: BigDecimal? = null,
    val eveningSalesRatioMin: BigDecimal? = null,
    val lateNightSalesRatioMin: BigDecimal? = null,
    val weekendSalesRatioMin: BigDecimal? = null,
    val age2030SalesRatioMin: BigDecimal? = null,
    val femaleSalesRatioMin: BigDecimal? = null,
    val officialLandPriceMax: BigDecimal? = null,
    val totalSpendingMin: BigDecimal? = null,
    val foodSpendingMin: BigDecimal? = null,
    val spendingPerStoreMin: BigDecimal? = null,
    val commercialTurnoverTypeMin: BigDecimal? = null,
    val commercialGrowthTypeMin: BigDecimal? = null
) {
    fun empty(): Boolean {
        return facilityTotalSizeMin == null &&
            facilityTotalSizeMax == null &&
            locationAreaMin == null &&
            locationAreaMax == null &&
            multiUseFacility == null &&
            floatingPopulationQuarterlyMin == null &&
            residentPopulationQuarterlyMin == null &&
            workerPopulationQuarterlyMin == null &&
            eveningPopulationRatioMin == null &&
            lateNightPopulationRatioMin == null &&
            morningPopulationRatioMin == null &&
            weekendPopulationRatioMin == null &&
            age2030PopulationRatioMin == null &&
            age40PlusPopulationRatioMin == null &&
            femalePopulationRatioMin == null &&
            restaurantCount500mMin == null &&
            restaurantCount500mMax == null &&
            cafeCount500mMin == null &&
            cafeCount500mMax == null &&
            closureRateMax == null &&
            openingRateMin == null &&
            averageSalesPerStoreMin == null &&
            eveningSalesRatioMin == null &&
            lateNightSalesRatioMin == null &&
            weekendSalesRatioMin == null &&
            age2030SalesRatioMin == null &&
            femaleSalesRatioMin == null &&
            officialLandPriceMax == null &&
            totalSpendingMin == null &&
            foodSpendingMin == null &&
            spendingPerStoreMin == null &&
            commercialTurnoverTypeMin == null &&
            commercialGrowthTypeMin == null
    }
}

data class VacancySpatialFilter(
    val sameCategoryRestaurantCount500mMin: Int? = null,
    val sameCategoryRestaurantCount500mMax: Int? = null,
    val industryGrowthRate500mMin: BigDecimal? = null
) {
    fun empty(): Boolean {
        return sameCategoryRestaurantCount500mMin == null &&
            sameCategoryRestaurantCount500mMax == null &&
            industryGrowthRate500mMin == null
    }
}

private fun cleanText(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun normalizeTransactionType(value: String?): String? {
    val trimmed = cleanText(value) ?: return null
    return when {
        trimmed.contains("월세") || trimmed.contains("임대") -> "임대"
        trimmed.contains("전세") -> "전세"
        trimmed.contains("매매") -> "매매"
        else -> trimmed
    }
}
