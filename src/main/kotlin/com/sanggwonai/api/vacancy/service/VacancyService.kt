package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.dto.VacancyExplorerCriteria
import com.sanggwonai.api.vacancy.dto.VacancyExplorerResult
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSort
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSummary
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyAccessibilityFoottrafficEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class VacancyService(
    private val vacancyDataset: VacancyDataset
) {
    @Transactional(readOnly = true)
    fun list(areaId: String?): List<VacancyDto> {
        val snapshot = vacancyDataset.snapshot()
        return snapshot.vacancies
            .asSequence()
            .map { toSearchRow(it, snapshot, categoryId = null) }
            .filter { row -> areaId.isNullOrBlank() || row.dto.areaId == areaId }
            .sortedBy { it.dto.id }
            .map { it.dto }
            .toList()
    }

    @Transactional(readOnly = true)
    fun search(criteria: VacancyExplorerCriteria): VacancyExplorerResult {
        val snapshot = vacancyDataset.snapshot()
        val categoryId = criteria.categoryId?.trim()?.takeIf { it.isNotEmpty() }
        val matchedRows = snapshot.vacancies
            .asSequence()
            .map { toSearchRow(it, snapshot, categoryId) }
            .filter { row -> matches(row, criteria, categoryId) }
            .toList()
        val sortedRows = sortRows(matchedRows, criteria.sort)
        val pageSize = criteria.size.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val pageNumber = criteria.page.coerceAtLeast(0)
        val offset = pageNumber * pageSize
        val pageRows = if (offset >= sortedRows.size) emptyList() else sortedRows.drop(offset).take(pageSize)

        return VacancyExplorerResult(
            items = pageRows.map { it.dto },
            total = sortedRows.size.toLong(),
            page = pageNumber,
            size = pageSize,
            totalPages = totalPages(sortedRows.size, pageSize),
            summary = summarize(matchedRows)
        )
    }

    @Transactional(readOnly = true)
    fun get(id: String): VacancyDto {
        val snapshot = vacancyDataset.snapshot()
        val vacancy = snapshot.vacancyById[id]
            ?: throw ApiException.of(ErrorType.VACANCY_NOT_FOUND)
        return toSearchRow(vacancy, snapshot, categoryId = null).dto
    }

    internal fun toDto(
        entity: VacancyEntity,
        common: VacancyCommonFeatureEntity?,
        score: VacancyCategoryScoreEntity?,
        spatial: VacancyCategorySpatialEntity?,
        categoryName: String?,
        accessibility: VacancyAccessibilityFoottrafficEntity?
    ): VacancyDto {
        return VacancyDto(
            id = entity.id,
            areaId = common?.areaCode ?: entity.dong ?: entity.district ?: entity.id,
            areaName = common?.areaName,
            categoryId = score?.id?.categoryId ?: spatial?.id?.categoryId,
            category = categoryName,
            recommended = score?.recommended,
            monthlyRent = entity.monthlyRent,
            deposit = entity.deposit,
            maintenanceFee = entity.maintenanceFee,
            premium = entity.premium,
            salePrice = entity.salePrice,
            latitude = entity.latitude,
            longitude = entity.longitude,
            survivalScore = score?.scorePercent(),
            listingId = entity.listingId,
            listingNumber = entity.listingNumber,
            roadAddress = entity.roadAddress,
            lotAddress = entity.lotAddress,
            postalCode = entity.postalCode,
            buildingName = entity.buildingName,
            province = entity.province,
            district = entity.district,
            dong = entity.dong,
            detailAddress = entity.detailAddress,
            transactionType = entity.transactionType,
            dedicatedArea = entity.dedicatedArea,
            supplyArea = entity.supplyArea,
            floor = entity.floor,
            totalFloors = entity.totalFloors,
            basementFloors = entity.basementFloors,
            buildingType = entity.buildingType,
            buildingUse = entity.buildingUse,
            buildingGrade = entity.buildingGrade,
            approvalDate = entity.approvalDate,
            direction = entity.direction,
            elevatorAvailable = entity.elevatorAvailable,
            elevatorCount = entity.elevatorCount,
            heatingType = entity.heatingType,
            restroomType = entity.restroomType,
            restroomCount = entity.restroomCount,
            parkingAvailable = entity.parkingAvailable,
            parkingCount = entity.parkingCount,
            terrace = entity.terrace,
            rooftop = entity.rooftop,
            interior = entity.interior,
            storage = entity.storage,
            airConditioner = entity.airConditioner,
            heater = entity.heater,
            lateNightOperationAvailable = entity.lateNightOperationAvailable,
            priceNegotiable = entity.priceNegotiable,
            rentAdjustable = entity.rentAdjustable,
            rentFreePeriodAvailable = entity.rentFreePeriodAvailable,
            subway = entity.subway,
            busStopInfo = accessibility?.busStopInfo,
            subwayStationInfo = accessibility?.subwayStationInfo,
            parkingInfo = accessibility?.parkingInfo,
            hourlyFloatingPopulation = accessibility?.hourlyFoottraffic(),
            brokerageFee = entity.brokerageFee,
            brokerageRate = entity.brokerageRate,
            viewCount = entity.viewCount,
            favoriteCount = entity.favoriteCount,
            majorBusinessCategory = entity.majorBusinessCategory,
            middleBusinessCategory = entity.middleBusinessCategory,
            floatingPopulationAnnualTotal = common?.floatingPopulationAnnualDensity?.toLong(),
            residentPopulationAnnualTotal = common?.residentPopulationAnnualDensity?.toLong(),
            workerPopulationAnnualTotal = common?.workerPopulationAnnualDensity?.toLong(),
            floatingPopulationQuarterlyAverage = common?.floatingPopulationQuarterlyDensity,
            residentPopulationQuarterlyAverage = common?.residentPopulationQuarterlyDensity,
            workerPopulationQuarterlyAverage = common?.workerPopulationQuarterlyDensity,
            restaurantCount250m = common?.restaurantCount250m,
            cafeCount250m = common?.cafeCount250m,
            industryGrowthRate250m = spatial?.industryGrowthRate250m,
            restaurantCount500m = common?.restaurantCount500m,
            cafeCount500m = common?.cafeCount500m,
            industryGrowthRate500m = spatial?.industryGrowthRate500m,
            restaurantCount1000m = common?.restaurantCount1000m,
            cafeCount1000m = common?.cafeCount1000m,
            industryGrowthRate1000m = spatial?.industryGrowthRate1000m,
            sameCategoryRestaurantCount250m = spatial?.sameCategoryRestaurantCount250m,
            sameCategoryRestaurantCount500m = spatial?.sameCategoryRestaurantCount500m,
            sameCategoryRestaurantCount1000m = spatial?.sameCategoryRestaurantCount1000m,
            businessMiddleCategoryName = entity.majorBusinessCategory,
            businessSubCategoryName = entity.middleBusinessCategory,
            multiUseFacility = common?.multiUseFacility,
            facilityTotalSize = common?.facilityTotalSize,
            locationArea = entity.dedicatedArea ?: common?.locationArea,
            eveningPopulationRatio = common?.eveningPopulationRatio,
            lateNightPopulationRatio = common?.lateNightPopulationRatio,
            morningPopulationRatio = common?.morningPopulationRatio,
            weekendPopulationRatio = common?.weekendPopulationRatio,
            age2030PopulationRatio = common?.age2030PopulationRatio,
            age40PlusPopulationRatio = common?.age40PlusPopulationRatio,
            femalePopulationRatio = common?.femalePopulationRatio,
            residentToFloatingRatio = common?.residentToFloatingRatio,
            workerToFloatingRatio = common?.workerToFloatingRatio,
            officialLandPrice = common?.officialLandPrice,
            closureRate = common?.closureRate,
            openingRate = common?.openingRate,
            averageSalesPerStore = common?.averageSalesPerStore?.divide(java.math.BigDecimal(3), 2, java.math.RoundingMode.HALF_UP),
            timeBasedSalesRatio = common?.eveningSalesRatio,
            lateNightSalesRatio = common?.lateNightSalesRatio,
            weekendSalesRatio = common?.weekendSalesRatio,
            age2030SalesRatio = common?.age2030SalesRatio,
            femaleSalesRatio = common?.femaleSalesRatio,
            totalSpending = common?.totalSpending,
            foodSpending = common?.foodSpending,
            spendingPerStore = common?.spendingPerStore,
            commercialTurnoverType = common?.commercialTurnoverType,
            commercialGrowthType = common?.commercialGrowthType,
            createdAt = entity.registeredAt.orEmpty(),
            updatedAt = entity.modifiedAt ?: entity.registeredAt.orEmpty()
        )
    }

    private fun toSearchRow(
        vacancy: VacancyEntity,
        snapshot: VacancyDatasetSnapshot,
        categoryId: String?
    ): VacancySearchRow {
        val common = snapshot.commonByProperty[vacancy.id]
        val score = snapshot.scoreFor(vacancy.id, categoryId)
        val spatial = snapshot.spatialFor(vacancy.id, score)
        val categoryName = snapshot.categoryName(score?.id?.categoryId)
        val accessibility = snapshot.accessibilityByProperty[vacancy.id]
        val dto = toDto(vacancy, common, score, spatial, categoryName, accessibility)
        return VacancySearchRow(dto = dto, searchText = searchText(dto))
    }

    private fun matches(row: VacancySearchRow, criteria: VacancyExplorerCriteria, categoryId: String?): Boolean {
        val dto = row.dto
        if (!criteria.areaId.isNullOrBlank() && dto.areaId != criteria.areaId) return false
        if (categoryId != null && dto.categoryId != categoryId) return false
        criteria.transactionType?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (!it.equals(dto.transactionType, ignoreCase = true)) return false
        }
        if (criteria.latitude != null && criteria.longitude != null && criteria.radiusM != null) {
            val vacancyLat = dto.latitude?.toDouble() ?: return false
            val vacancyLng = dto.longitude?.toDouble() ?: return false
            val radiusM = criteria.radiusM.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
            if (distanceMeters(criteria.latitude, criteria.longitude, vacancyLat, vacancyLng) > radiusM) return false
        }
        criteria.q?.trim()?.lowercase(Locale.KOREA)?.takeIf { it.isNotEmpty() }?.let {
            if (!row.searchText.contains(it)) return false
        }
        criteria.rentMax?.let { if (dto.monthlyRent == null || dto.monthlyRent > it) return false }
        criteria.depositMax?.let { if (dto.deposit == null || dto.deposit > it) return false }
        criteria.maintenanceFeeMax?.let {
            if (dto.maintenanceFee == null || dto.maintenanceFee > it) return false
        }
        criteria.premiumMax?.let { if (dto.premium == null || dto.premium > it) return false }
        criteria.salePriceMax?.let { if (dto.salePrice == null || dto.salePrice > it) return false }
        criteria.scoreMin?.let { if (dto.survivalScore == null || dto.survivalScore < it) return false }
        criteria.areaMin?.let { if (dto.locationArea == null || dto.locationArea < it) return false }
        criteria.areaMax?.let { if (dto.locationArea == null || dto.locationArea > it) return false }
        return true
    }

    private fun sortRows(rows: List<VacancySearchRow>, sort: VacancyExplorerSort): List<VacancySearchRow> {
        return when (sort) {
            VacancyExplorerSort.ScoreDesc -> rows.sortedWith(
                compareByDescending<VacancySearchRow> { it.dto.survivalScore ?: BigDecimal.ZERO }
                    .thenBy { it.dto.id }
            )
            VacancyExplorerSort.RentAsc -> rows.sortedWith(
                compareBy<VacancySearchRow> { it.dto.monthlyRent ?: Long.MAX_VALUE }
                    .thenByDescending { it.dto.survivalScore ?: BigDecimal.ZERO }
                    .thenBy { it.dto.id }
            )
            VacancyExplorerSort.RentDesc -> rows.sortedWith(
                compareByDescending<VacancySearchRow> { it.dto.monthlyRent ?: Long.MIN_VALUE }
                    .thenByDescending { it.dto.survivalScore ?: BigDecimal.ZERO }
                    .thenBy { it.dto.id }
            )
            VacancyExplorerSort.DepositAsc -> rows.sortedWith(
                compareBy<VacancySearchRow> { it.dto.deposit ?: Long.MAX_VALUE }
                    .thenByDescending { it.dto.survivalScore ?: BigDecimal.ZERO }
                    .thenBy { it.dto.id }
            )
            VacancyExplorerSort.AreaDesc -> rows.sortedWith(
                compareByDescending<VacancySearchRow> { it.dto.locationArea ?: BigDecimal.ZERO }
                    .thenByDescending { it.dto.survivalScore ?: BigDecimal.ZERO }
                    .thenBy { it.dto.id }
            )
            VacancyExplorerSort.UpdatedDesc -> rows.sortedWith(
                compareByDescending<VacancySearchRow> { it.dto.updatedAt }
                    .thenBy { it.dto.id }
            )
        }
    }

    private fun summarize(rows: List<VacancySearchRow>): VacancyExplorerSummary {
        val vacancies = rows.map { it.dto }
        return VacancyExplorerSummary(
            total = vacancies.size.toLong(),
            averageScore = averageDecimal(vacancies.mapNotNull { it.survivalScore }),
            averageRent = averageLong(vacancies.mapNotNull { it.monthlyRent }),
            averageDeposit = averageLong(vacancies.mapNotNull { it.deposit }),
            averageMaintenanceFee = averageLong(vacancies.mapNotNull { it.maintenanceFee }),
            minRent = vacancies.mapNotNull { it.monthlyRent }.minOrNull(),
            maxRent = vacancies.mapNotNull { it.monthlyRent }.maxOrNull(),
            areaCount = vacancies.map { it.areaId }.distinct().size
        )
    }

    private fun averageLong(values: List<Long>): BigDecimal? {
        if (values.isEmpty()) return null
        val sum = values.fold(BigDecimal.ZERO) { acc, value -> acc + BigDecimal.valueOf(value) }
        return sum.divide(BigDecimal.valueOf(values.size.toLong()), SUMMARY_SCALE, RoundingMode.HALF_UP)
    }

    private fun averageDecimal(values: List<BigDecimal>): BigDecimal? {
        if (values.isEmpty()) return null
        val sum = values.fold(BigDecimal.ZERO) { acc, value -> acc + value }
        return sum.divide(BigDecimal.valueOf(values.size.toLong()), SUMMARY_SCALE, RoundingMode.HALF_UP)
    }

    private fun totalPages(total: Int, size: Int): Int {
        if (total == 0) return 0
        return ((total - 1) / size) + 1
    }

    private fun searchText(dto: VacancyDto): String {
        return listOfNotNull(
            dto.id,
            dto.areaId,
            dto.areaName,
            dto.category,
            dto.roadAddress,
            dto.lotAddress,
            dto.buildingName,
            dto.province,
            dto.district,
            dto.dong,
            dto.detailAddress,
            dto.transactionType,
            dto.buildingType,
            dto.buildingUse,
            dto.majorBusinessCategory,
            dto.middleBusinessCategory,
            dto.businessMiddleCategoryName,
            dto.businessSubCategoryName,
            dto.subway
        ).joinToString(" ").lowercase(Locale.KOREA)
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val earthRadiusM = 6_371_000.0
        val latRad1 = Math.toRadians(lat1)
        val latRad2 = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLng = Math.toRadians(lng2 - lng1)
        val a = sin(deltaLat / 2).pow(2) +
            cos(latRad1) * cos(latRad2) * sin(deltaLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusM * c).toInt()
    }

    companion object {
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 600
        private const val MIN_RADIUS_M = 1
        private const val MAX_RADIUS_M = 5000
        private const val SUMMARY_SCALE = 2
    }
}

private data class VacancySearchRow(
    val dto: VacancyDto,
    val searchText: String
)
