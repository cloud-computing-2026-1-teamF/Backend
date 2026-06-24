package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.vacancy.dto.MenuPriceEstimateDto
import com.sanggwonai.api.vacancy.dto.VacancyDto
import com.sanggwonai.api.vacancy.dto.VacancyExplorerCriteria
import com.sanggwonai.api.vacancy.dto.VacancyExplorerResult
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSort
import com.sanggwonai.api.vacancy.dto.VacancyExplorerSummary
import com.sanggwonai.api.vacancy.dto.VacancyHorizonScoreDto
import com.sanggwonai.api.vacancy.dto.VacancyMetricDistribution
import com.sanggwonai.api.vacancy.dto.VacancyMetricReference
import com.sanggwonai.api.vacancy.dto.VacancyScoreMode
import com.sanggwonai.api.vacancy.dto.VacancyScoreExplanationDto
import com.sanggwonai.api.vacancy.dto.toScoreExplanationDto
import com.sanggwonai.api.vacancy.entity.VacancyCategoryHorizonScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.entity.VacancyAccessibilityFoottrafficEntity
import com.sanggwonai.api.vacancy.repository.VacancyMetricReferenceRepository
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
    private val vacancyDataset: VacancyDataset,
    private val metricReferenceRepository: VacancyMetricReferenceRepository
) {
    @Transactional(readOnly = true)
    fun list(areaId: String?): List<VacancyDto> {
        val snapshot = vacancyDataset.snapshot()
        return snapshot.vacancies
            .asSequence()
            .mapNotNull { toSearchRow(it, snapshot, categoryId = null, scoreMode = VacancyScoreMode.Best) }
            .filter { row -> areaId.isNullOrBlank() || row.dto.areaId == areaId }
            .let { dedupeRows(it.toList()).asSequence() }
            .sortedBy { it.dto.id }
            .map { it.dto }
            .toList()
    }

    @Transactional(readOnly = true)
    fun search(criteria: VacancyExplorerCriteria): VacancyExplorerResult = search(criteria, candidateIds = null)

    @Transactional(readOnly = true)
    fun search(criteria: VacancyExplorerCriteria, candidateIds: Set<String>?): VacancyExplorerResult {
        val snapshot = vacancyDataset.snapshot()
        val categoryId = criteria.categoryId?.trim()?.takeIf { it.isNotEmpty() }
        val matchedRows = dedupeRows(snapshot.vacancies
            .asSequence()
            .filter { vacancy -> candidateIds == null || vacancy.id in candidateIds }
            .mapNotNull { toSearchRow(it, snapshot, categoryId, criteria.scoreMode) }
            .filter { row -> matches(row, criteria, categoryId) }
            .toList())
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
        return toSearchRow(vacancy, snapshot, categoryId = null, scoreMode = VacancyScoreMode.Best)!!.dto
    }

    @Transactional(readOnly = true)
    fun metricReference(categoryId: String?, vacancyId: String?): VacancyMetricReference {
        val normalizedCategoryId = categoryId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedVacancyId = vacancyId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedCategoryId != null) {
            val persisted = if (normalizedVacancyId != null) {
                metricReferenceRepository.find(normalizedCategoryId, normalizedVacancyId)
            } else {
                metricReferenceRepository.findCategorySummary(normalizedCategoryId)
            }
            if (persisted != null) return persisted
        }

        return VacancyMetricReference(
            categoryId = normalizedCategoryId,
            vacancyId = normalizedVacancyId,
            peerCount = 0,
            footTrafficDaily = emptyMetricDistribution(),
            competition500m = emptyMetricDistribution(),
            averageSalesMonthly = emptyMetricDistribution()
        )
    }

    fun estimateMenuPrice(vacancyId: String, menuName: String): MenuPriceEstimateDto {
        val normalizedMenu = menuName.trim()
        if (normalizedMenu.length < MIN_MENU_NAME_LENGTH) {
            throw ApiException.of(ErrorType.VALIDATION_FAILED, mapOf("menu_name" to "메뉴 이름을 입력해 주세요"))
        }

        val vacancy = get(vacancyId)
        val hash = stableHash("${vacancy.id}|${vacancy.categoryId}|$normalizedMenu")
        val latencyMs = SIMULATED_MODEL_DELAY_MIN_MS + Math.floorMod(hash, SIMULATED_MODEL_DELAY_SPREAD_MS).toLong()
        Thread.sleep(latencyMs)

        val basePrice = baseMenuPrice(normalizedMenu)
        val multiplier = priceMultiplier(vacancy, hash)
        val recommended = roundPrice(basePrice * multiplier)
        val minPrice = roundPrice(recommended * PRICE_RANGE_LOW)
        val maxPrice = roundPrice(recommended * PRICE_RANGE_HIGH)
        val confidence = confidenceLabel(vacancy)
        val positioning = positioningLabel(multiplier)

        return MenuPriceEstimateDto(
            vacancyId = vacancy.id,
            menuName = normalizedMenu,
            recommendedPrice = recommended,
            minPrice = minPrice.coerceAtMost(recommended),
            maxPrice = maxPrice.coerceAtLeast(recommended),
            currency = "KRW",
            confidence = confidence,
            positioning = positioning,
            signals = priceSignals(vacancy, multiplier),
            estimatedLatencyMs = latencyMs,
            source = "mock_menu_price_model"
        )
    }

    internal fun toDto(
        entity: VacancyEntity,
        common: VacancyCommonFeatureEntity?,
        score: VacancyCategoryScoreEntity?,
        spatial: VacancyCategorySpatialEntity?,
        categoryName: String?,
        accessibility: VacancyAccessibilityFoottrafficEntity?,
        horizonScores: List<VacancyCategoryHorizonScoreEntity>,
        scoreExplanation: VacancyScoreExplanationDto?
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
            horizonScores = horizonScores.map(::toHorizonScoreDto),
            scoreExplanation = scoreExplanation,
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
        categoryId: String?,
        scoreMode: VacancyScoreMode
    ): VacancySearchRow? {
        val common = snapshot.commonByProperty[vacancy.id]
        val score = when (scoreMode) {
            VacancyScoreMode.Best -> snapshot.bestScoreFor(vacancy.id)
            VacancyScoreMode.Category -> categoryId?.let { snapshot.categoryScoreFor(vacancy.id, it) }
                ?: snapshot.bestScoreFor(vacancy.id)
        } ?: return null
        val spatial = snapshot.spatialFor(vacancy.id, score)
        val categoryName = snapshot.categoryName(score.id.categoryId)
        val accessibility = snapshot.accessibilityByProperty[vacancy.id]
        val horizonScores = snapshot.horizonScoresFor(vacancy.id, score.id.categoryId)
        val scoreExplanation = toScoreExplanationDto(
            entities = snapshot.scoreExplanationsFor(vacancy.id, score.id.categoryId),
            benchmarksByKey = snapshot.scoreFeatureBenchmarksByKey,
            vacancy = vacancy,
            common = common,
            spatial = spatial
        )
        val dto = toDto(vacancy, common, score, spatial, categoryName, accessibility, horizonScores, scoreExplanation)
        return VacancySearchRow(dto = dto, searchText = searchText(dto))
    }

    private fun toHorizonScoreDto(entity: VacancyCategoryHorizonScoreEntity): VacancyHorizonScoreDto {
        return VacancyHorizonScoreDto(
            horizonYears = entity.id.horizonYears,
            survivalScore = entity.scorePercent(),
            recommended = entity.recommended
        )
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

    private fun dedupeRows(rows: List<VacancySearchRow>): List<VacancySearchRow> {
        val byKey = linkedMapOf<String, VacancySearchRow>()
        rows.forEach { row ->
            val key = row.dto.deduplicationKey()
            val existing = byKey[key]
            if (existing == null || isBetterDuplicate(row, existing)) {
                byKey[key] = row
            }
        }
        return byKey.values.toList()
    }

    private fun isBetterDuplicate(candidate: VacancySearchRow, existing: VacancySearchRow): Boolean {
        val candidateScore = candidate.dto.survivalScore ?: BigDecimal.ZERO
        val existingScore = existing.dto.survivalScore ?: BigDecimal.ZERO
        val scoreCompare = candidateScore.compareTo(existingScore)
        if (scoreCompare != 0) return scoreCompare > 0

        val updatedCompare = candidate.dto.updatedAt.compareTo(existing.dto.updatedAt)
        if (updatedCompare != 0) return updatedCompare > 0

        return candidate.dto.id < existing.dto.id
    }

    private fun summarize(rows: List<VacancySearchRow>): VacancyExplorerSummary {
        val vacancies = rows.map { it.dto }
        return VacancyExplorerSummary(
            total = vacancies.size.toLong(),
            averageScore = averageDecimal(vacancies.mapNotNull { it.survivalScore }),
            averageRent = averageLong(vacancies.mapNotNull { it.monthlyRent }),
            averageDeposit = averageLong(vacancies.mapNotNull { it.deposit }),
            averageSalePrice = averageLong(vacancies.mapNotNull { it.salePrice }),
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

    private fun emptyMetricDistribution(): VacancyMetricDistribution {
        return VacancyMetricDistribution(
            selected = null,
            average = null,
            median = null,
            min = null,
            max = null,
            p10 = null,
            p25 = null,
            p75 = null,
            p90 = null,
            percentile = null
        )
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

    private fun baseMenuPrice(menuName: String): Double {
        val normalized = menuName.lowercase(Locale.KOREA)
        return when {
            normalized.contains("아메리카노") || normalized.contains("coffee") || normalized.contains("커피") -> 4_800.0
            normalized.contains("라떼") || normalized.contains("latte") -> 5_800.0
            normalized.contains("디저트") || normalized.contains("케이크") || normalized.contains("cake") -> 7_200.0
            normalized.contains("샐러드") || normalized.contains("salad") -> 11_500.0
            normalized.contains("파스타") || normalized.contains("pasta") -> 17_000.0
            normalized.contains("피자") || normalized.contains("pizza") -> 19_500.0
            normalized.contains("버거") || normalized.contains("burger") -> 10_500.0
            normalized.contains("치킨") || normalized.contains("chicken") -> 22_000.0
            normalized.contains("삼겹") || normalized.contains("고기") || normalized.contains("스테이크") -> 18_000.0
            normalized.contains("떡볶") -> 7_500.0
            normalized.contains("김밥") -> 5_000.0
            normalized.contains("국밥") || normalized.contains("찌개") || normalized.contains("덮밥") -> 9_500.0
            normalized.contains("라멘") || normalized.contains("쌀국수") || normalized.contains("면") -> 11_000.0
            normalized.contains("맥주") || normalized.contains("beer") -> 6_500.0
            normalized.contains("칵테일") || normalized.contains("와인") -> 12_000.0
            else -> 12_000.0
        }
    }

    private fun priceMultiplier(vacancy: VacancyDto, hash: Int): Double {
        val score = vacancy.survivalScore?.toDouble() ?: 70.0
        val scoreFactor = ((score - 70.0) / 100.0).coerceIn(-0.12, 0.18)

        val sales = vacancy.averageSalesPerStore?.toDouble()
        val salesFactor = sales?.let { ((it / 3_500.0) - 1.0) * 0.14 }?.coerceIn(-0.10, 0.16) ?: 0.0

        val dailyFootTraffic = vacancy.floatingPopulationAnnualTotal?.let { it / 365.0 }
        val footFactor = dailyFootTraffic?.let { ((it / 70_000.0) - 1.0) * 0.08 }?.coerceIn(-0.08, 0.12) ?: 0.0

        val rentFactor = vacancy.monthlyRent?.let { ((it / 280.0) - 1.0) * 0.06 }?.coerceIn(-0.06, 0.10) ?: 0.0
        val areaFactor = vacancy.locationArea?.toDouble()?.let { ((it / 80.0) - 1.0) * 0.03 }?.coerceIn(-0.04, 0.05) ?: 0.0
        val jitter = ((Math.floorMod(hash, 15) - 7) / 100.0)

        return (1.0 + scoreFactor + salesFactor + footFactor + rentFactor + areaFactor + jitter)
            .coerceIn(0.74, 1.48)
    }

    private fun priceSignals(vacancy: VacancyDto, multiplier: Double): List<String> {
        val signals = mutableListOf<String>()
        val score = vacancy.survivalScore?.toDouble()
        if (score != null) {
            signals += if (score >= 78.0) "상권 점수가 높아 가격 수용력이 좋아 보여요" else "상권 점수를 감안해 보수적으로 잡았어요"
        }
        val sales = vacancy.averageSalesPerStore?.toDouble()
        if (sales != null) {
            signals += if (sales >= 3_500.0) "주변 매출 기준이 높게 형성돼 있어요" else "주변 매출 기준은 무리한 가격을 피하는 쪽이에요"
        }
        val dailyFootTraffic = vacancy.floatingPopulationAnnualTotal?.let { it / 365.0 }
        if (dailyFootTraffic != null) {
            signals += if (dailyFootTraffic >= 70_000.0) "유동인구가 가격 테스트에 유리해요" else "유동인구 기준으로는 접근성 있는 가격이 좋아 보여요"
        }
        if (signals.size < 3 && vacancy.monthlyRent != null) {
            signals += if (vacancy.monthlyRent >= 280L) "임대료 부담을 반영해 객단가를 조금 높게 봤어요" else "임대료 여유가 있어 진입 가격을 낮출 수 있어요"
        }
        if (signals.size < 3) {
            signals += if (multiplier >= 1.04) "이 입지는 평균보다 높은 가격 실험이 가능해 보여요" else "이 입지는 빠른 회전을 우선한 가격이 어울려요"
        }
        return signals.take(3)
    }

    private fun confidenceLabel(vacancy: VacancyDto): String {
        val availableSignals = listOfNotNull(
            vacancy.survivalScore,
            vacancy.averageSalesPerStore,
            vacancy.floatingPopulationAnnualTotal,
            vacancy.monthlyRent,
            vacancy.locationArea
        ).size
        return when {
            availableSignals >= 4 -> "높음"
            availableSignals >= 2 -> "보통"
            else -> "낮음"
        }
    }

    private fun positioningLabel(multiplier: Double): String {
        return when {
            multiplier >= 1.12 -> "프리미엄 가격 가능"
            multiplier >= 0.96 -> "상권 평균권"
            else -> "접근성 가격 권장"
        }
    }

    private fun roundPrice(value: Double): Long {
        return (kotlin.math.round(value / PRICE_ROUNDING_UNIT) * PRICE_ROUNDING_UNIT).toLong()
            .coerceAtLeast(MIN_RECOMMENDED_PRICE)
    }

    private fun stableHash(value: String): Int {
        return Math.floorMod(value.fold(0) { acc, char -> (acc * 31) + char.code }, Int.MAX_VALUE)
    }

    companion object {
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 600
        private const val MIN_RADIUS_M = 1
        private const val MAX_RADIUS_M = 5000
        private const val SUMMARY_SCALE = 2
        private const val MIN_MENU_NAME_LENGTH = 1
        private const val SIMULATED_MODEL_DELAY_MIN_MS = 2200L
        private const val SIMULATED_MODEL_DELAY_SPREAD_MS = 1200
        private const val PRICE_RANGE_LOW = 0.9
        private const val PRICE_RANGE_HIGH = 1.12
        private const val PRICE_ROUNDING_UNIT = 500.0
        private const val MIN_RECOMMENDED_PRICE = 1_000L
    }
}

private data class VacancySearchRow(
    val dto: VacancyDto,
    val searchText: String
)
