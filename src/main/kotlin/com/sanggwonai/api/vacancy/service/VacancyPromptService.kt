package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyAmenityFilter
import com.sanggwonai.api.vacancy.dto.VacancyBuildingFilter
import com.sanggwonai.api.vacancy.dto.VacancyCategoryFilter
import com.sanggwonai.api.vacancy.dto.VacancyCommercialFilter
import com.sanggwonai.api.vacancy.dto.VacancyLocationFilter
import com.sanggwonai.api.vacancy.dto.VacancyPriceFilter
import com.sanggwonai.api.vacancy.dto.VacancySpaceFilter
import com.sanggwonai.api.vacancy.dto.VacancySpatialFilter
import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class VacancyPromptParseResult(
    val filters: VacancyStructuredFilter,
    val source: String,
    val schemaVersion: String = VacancyPromptSchema.VERSION
)

@Service
class VacancyPromptService(
    private val openAiClient: OpenAiVacancyPromptClient
) {
    fun parse(prompt: String, allowOpenAi: Boolean): VacancyPromptParseResult {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty()) {
            return VacancyPromptParseResult(
                filters = VacancyStructuredFilter(sort = "score_desc").normalized(),
                source = "empty"
            )
        }

        val canUseOpenAi = allowOpenAi && openAiClient.isAvailable()
        val fallbackFilters = VacancyPromptFallbackParser.parse(normalizedPrompt)
        val parsed = if (canUseOpenAi) openAiClient.parse(normalizedPrompt) else null
        return parsed
            ?.let { VacancyPromptParseResult(filters = mergePromptRepairs(it, fallbackFilters).normalized(), source = "openai") }
            ?: VacancyPromptParseResult(
                filters = fallbackFilters.normalized(),
                source = "fallback"
            )
    }
}

private fun mergePromptRepairs(primary: VacancyStructuredFilter, repair: VacancyStructuredFilter): VacancyStructuredFilter {
    val commercial = mergeCommercial(primary.commercial, repair.commercial)
    return primary.copy(
        location = mergeLocation(primary.location, repair.location),
        category = mergeCategory(primary.category, repair.category, repair.commercial),
        transactionType = primary.transactionType ?: repair.transactionType,
        price = mergePrice(primary.price, repair.price),
        space = mergeSpace(primary.space, repair.space),
        building = mergeBuilding(primary.building, repair.building),
        amenities = mergeAmenities(primary.amenities, repair.amenities),
        commercial = commercial,
        spatial = mergeSpatial(primary.spatial, repair.spatial),
        sort = if (primary.sort == null || primary.sort == "score_desc") repair.sort ?: primary.sort else primary.sort
    )
}

private fun mergeLocation(primary: VacancyLocationFilter?, repair: VacancyLocationFilter?): VacancyLocationFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        district = primary.district?.takeIf(::looksLikeSeoulDistrict) ?: repair.district,
        districtKeywords = mergeStringLists(
            primary.districtKeywords?.filter(::looksLikeSeoulDistrict),
            repair.districtKeywords?.filter(::looksLikeSeoulDistrict)
        ),
        dong = primary.dong?.takeUnless(::looksLikeBadLocationKeyword)
            ?: repair.dong?.takeUnless(::looksLikeBadLocationKeyword),
        dongKeywords = mergeStringLists(
            primary.dongKeywords?.filterNot(::looksLikeBadLocationKeyword),
            repair.dongKeywords?.filterNot(::looksLikeBadLocationKeyword)
        ),
        subway = primary.subway ?: repair.subway,
        subwayKeywords = mergeStringLists(primary.subwayKeywords, repair.subwayKeywords)
    )
}

private fun mergeCategory(
    primary: VacancyCategoryFilter?,
    repair: VacancyCategoryFilter?,
    repairCommercial: VacancyCommercialFilter?
): VacancyCategoryFilter? {
    if (primary == null || primary.empty()) return repair
    if (repair == null || repair.empty()) {
        if (
            primary.categoryId == "9" &&
            (repairCommercial?.cafeToRestaurantRatioMax != null || repairCommercial?.cafeCount500mLevel == "low")
        ) {
            return null
        }
        return primary
    }
    val primaryGeneralCategory = primary.categoryId in setOf("5", "9") ||
        primary.categoryLabel in setOf("기타", "카페/디저트")
    val sameCategory = repair.categoryId == primary.categoryId || repair.categoryLabel == primary.categoryLabel
    if (primaryGeneralCategory && repair.categoryId != null && !sameCategory) {
        return repair.copy(scoreMin = primary.scoreMin ?: repair.scoreMin)
    }
    return primary.copy(scoreMin = primary.scoreMin ?: repair.scoreMin)
}

private fun mergePrice(primary: VacancyPriceFilter?, repair: VacancyPriceFilter?): VacancyPriceFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        monthlyRentMin = primary.monthlyRentMin ?: repair.monthlyRentMin,
        monthlyRentMax = repairMoneyMax(primary.monthlyRentMax, repair.monthlyRentMax),
        depositMin = primary.depositMin ?: repair.depositMin,
        depositMax = repairMoneyMax(primary.depositMax, repair.depositMax),
        maintenanceFeeMin = primary.maintenanceFeeMin ?: repair.maintenanceFeeMin,
        maintenanceFeeMax = repairMoneyMax(primary.maintenanceFeeMax, repair.maintenanceFeeMax),
        premiumMin = primary.premiumMin ?: repair.premiumMin,
        premiumMax = repairMoneyMax(primary.premiumMax, repair.premiumMax),
        salePriceMin = primary.salePriceMin ?: repair.salePriceMin,
        salePriceMax = repairMoneyMax(primary.salePriceMax, repair.salePriceMax),
        priceNegotiable = primary.priceNegotiable ?: repair.priceNegotiable,
        rentAdjustable = primary.rentAdjustable ?: repair.rentAdjustable,
        rentFreePeriodAvailable = primary.rentFreePeriodAvailable ?: repair.rentFreePeriodAvailable
    )
}

private fun mergeSpace(primary: VacancySpaceFilter?, repair: VacancySpaceFilter?): VacancySpaceFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        dedicatedAreaMin = repairArea(primary.dedicatedAreaMin, repair.dedicatedAreaMin),
        dedicatedAreaMax = repairArea(primary.dedicatedAreaMax, repair.dedicatedAreaMax),
        supplyAreaMin = repairArea(primary.supplyAreaMin, repair.supplyAreaMin),
        supplyAreaMax = repairArea(primary.supplyAreaMax, repair.supplyAreaMax),
        floorText = primary.floorText ?: repair.floorText,
        groundFloor = primary.groundFloor ?: repair.groundFloor,
        basement = primary.basement ?: repair.basement
    )
}

private fun mergeBuilding(primary: VacancyBuildingFilter?, repair: VacancyBuildingFilter?): VacancyBuildingFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        buildingName = primary.buildingName ?: repair.buildingName,
        buildingType = primary.buildingType ?: repair.buildingType,
        buildingUse = primary.buildingUse ?: repair.buildingUse,
        buildingGrade = primary.buildingGrade ?: repair.buildingGrade,
        direction = primary.direction ?: repair.direction,
        approvalDateFrom = primary.approvalDateFrom ?: repair.approvalDateFrom,
        approvalDateTo = primary.approvalDateTo ?: repair.approvalDateTo
    )
}

private fun mergeAmenities(primary: VacancyAmenityFilter?, repair: VacancyAmenityFilter?): VacancyAmenityFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        elevatorAvailable = primary.elevatorAvailable ?: repair.elevatorAvailable,
        parkingAvailable = primary.parkingAvailable ?: repair.parkingAvailable,
        parkingCountMin = primary.parkingCountMin ?: repair.parkingCountMin,
        terrace = primary.terrace ?: repair.terrace,
        rooftop = primary.rooftop ?: repair.rooftop,
        interior = primary.interior ?: repair.interior,
        storage = primary.storage ?: repair.storage,
        airConditioner = primary.airConditioner ?: repair.airConditioner,
        heater = primary.heater ?: repair.heater,
        lateNightOperationAvailable = primary.lateNightOperationAvailable ?: repair.lateNightOperationAvailable,
        restroomType = primary.restroomType ?: repair.restroomType,
        restroomCountMin = primary.restroomCountMin ?: repair.restroomCountMin
    )
}

private fun mergeCommercial(primary: VacancyCommercialFilter?, repair: VacancyCommercialFilter?): VacancyCommercialFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        facilityTotalSizeMin = primary.facilityTotalSizeMin ?: repair.facilityTotalSizeMin,
        facilityTotalSizeMax = primary.facilityTotalSizeMax ?: repair.facilityTotalSizeMax,
        locationAreaMin = primary.locationAreaMin ?: repair.locationAreaMin,
        locationAreaMax = primary.locationAreaMax ?: repair.locationAreaMax,
        multiUseFacility = primary.multiUseFacility ?: repair.multiUseFacility,
        floatingPopulationQuarterlyMin = repairRelativeNumber(primary.floatingPopulationQuarterlyMin, repair.floatingPopulationQuarterlyMin, repair.floatingPopulationQuarterlyLevel),
        floatingPopulationQuarterlyLevel = primary.floatingPopulationQuarterlyLevel ?: repair.floatingPopulationQuarterlyLevel,
        residentPopulationQuarterlyMin = repairRelativeNumber(primary.residentPopulationQuarterlyMin, repair.residentPopulationQuarterlyMin, repair.residentPopulationQuarterlyLevel),
        residentPopulationQuarterlyLevel = primary.residentPopulationQuarterlyLevel ?: repair.residentPopulationQuarterlyLevel,
        workerPopulationQuarterlyMin = repairRelativeNumber(primary.workerPopulationQuarterlyMin, repair.workerPopulationQuarterlyMin, repair.workerPopulationQuarterlyLevel),
        workerPopulationQuarterlyLevel = primary.workerPopulationQuarterlyLevel ?: repair.workerPopulationQuarterlyLevel,
        eveningPopulationRatioMin = repairRelativeNumber(primary.eveningPopulationRatioMin, repair.eveningPopulationRatioMin, repair.eveningPopulationRatioLevel),
        eveningPopulationRatioLevel = primary.eveningPopulationRatioLevel ?: repair.eveningPopulationRatioLevel,
        lateNightPopulationRatioMin = repairRelativeNumber(primary.lateNightPopulationRatioMin, repair.lateNightPopulationRatioMin, repair.lateNightPopulationRatioLevel),
        lateNightPopulationRatioLevel = primary.lateNightPopulationRatioLevel ?: repair.lateNightPopulationRatioLevel,
        morningPopulationRatioMin = repairRelativeNumber(primary.morningPopulationRatioMin, repair.morningPopulationRatioMin, repair.morningPopulationRatioLevel),
        morningPopulationRatioLevel = primary.morningPopulationRatioLevel ?: repair.morningPopulationRatioLevel,
        weekendPopulationRatioMin = repairRelativeNumber(primary.weekendPopulationRatioMin, repair.weekendPopulationRatioMin, repair.weekendPopulationRatioLevel),
        weekendPopulationRatioLevel = primary.weekendPopulationRatioLevel ?: repair.weekendPopulationRatioLevel,
        age2030PopulationRatioMin = repairRelativeNumber(primary.age2030PopulationRatioMin, repair.age2030PopulationRatioMin, repair.age2030PopulationRatioLevel),
        age2030PopulationRatioLevel = primary.age2030PopulationRatioLevel ?: repair.age2030PopulationRatioLevel,
        age40PlusPopulationRatioMin = repairRelativeNumber(primary.age40PlusPopulationRatioMin, repair.age40PlusPopulationRatioMin, repair.age40PlusPopulationRatioLevel),
        age40PlusPopulationRatioLevel = primary.age40PlusPopulationRatioLevel ?: repair.age40PlusPopulationRatioLevel,
        femalePopulationRatioMin = repairRelativeNumber(primary.femalePopulationRatioMin, repair.femalePopulationRatioMin, repair.femalePopulationRatioLevel),
        femalePopulationRatioLevel = primary.femalePopulationRatioLevel ?: repair.femalePopulationRatioLevel,
        restaurantCount500mMin = if (repair.restaurantCount500mLevel != null || repair.cafeToRestaurantRatioMax != null) repair.restaurantCount500mMin else primary.restaurantCount500mMin ?: repair.restaurantCount500mMin,
        restaurantCount500mMax = primary.restaurantCount500mMax ?: repair.restaurantCount500mMax,
        restaurantCount500mLevel = primary.restaurantCount500mLevel ?: repair.restaurantCount500mLevel,
        cafeCount500mMin = if (repair.cafeCount500mLevel != null || repair.cafeToRestaurantRatioMax != null) repair.cafeCount500mMin else primary.cafeCount500mMin ?: repair.cafeCount500mMin,
        cafeCount500mMax = if (repair.cafeCount500mLevel != null || repair.cafeToRestaurantRatioMax != null) repair.cafeCount500mMax else primary.cafeCount500mMax ?: repair.cafeCount500mMax,
        cafeCount500mLevel = if (primary.cafeToRestaurantRatioMax != null || repair.cafeToRestaurantRatioMax != null) null else primary.cafeCount500mLevel ?: repair.cafeCount500mLevel,
        cafeToRestaurantRatioMax = primary.cafeToRestaurantRatioMax ?: repair.cafeToRestaurantRatioMax,
        closureRateMax = if (repair.closureRateLevel != null) repair.closureRateMax else primary.closureRateMax ?: repair.closureRateMax,
        closureRateLevel = primary.closureRateLevel ?: repair.closureRateLevel,
        openingRateMin = repairRelativeNumber(primary.openingRateMin, repair.openingRateMin, repair.openingRateLevel),
        openingRateLevel = primary.openingRateLevel ?: repair.openingRateLevel,
        averageSalesPerStoreMin = repairRelativeNumber(primary.averageSalesPerStoreMin, repair.averageSalesPerStoreMin, repair.averageSalesPerStoreLevel),
        averageSalesPerStoreLevel = primary.averageSalesPerStoreLevel ?: repair.averageSalesPerStoreLevel,
        eveningSalesRatioMin = repairRelativeNumber(primary.eveningSalesRatioMin, repair.eveningSalesRatioMin, repair.eveningSalesRatioLevel),
        eveningSalesRatioLevel = primary.eveningSalesRatioLevel ?: repair.eveningSalesRatioLevel,
        lateNightSalesRatioMin = repairRelativeNumber(primary.lateNightSalesRatioMin, repair.lateNightSalesRatioMin, repair.lateNightSalesRatioLevel),
        lateNightSalesRatioLevel = primary.lateNightSalesRatioLevel ?: repair.lateNightSalesRatioLevel,
        weekendSalesRatioMin = repairRelativeNumber(primary.weekendSalesRatioMin, repair.weekendSalesRatioMin, repair.weekendSalesRatioLevel),
        weekendSalesRatioLevel = primary.weekendSalesRatioLevel ?: repair.weekendSalesRatioLevel,
        age2030SalesRatioMin = repairRelativeNumber(primary.age2030SalesRatioMin, repair.age2030SalesRatioMin, repair.age2030SalesRatioLevel),
        age2030SalesRatioLevel = primary.age2030SalesRatioLevel ?: repair.age2030SalesRatioLevel,
        femaleSalesRatioMin = repairRelativeNumber(primary.femaleSalesRatioMin, repair.femaleSalesRatioMin, repair.femaleSalesRatioLevel),
        femaleSalesRatioLevel = primary.femaleSalesRatioLevel ?: repair.femaleSalesRatioLevel,
        officialLandPriceMax = if (repair.officialLandPriceLevel != null) repair.officialLandPriceMax else primary.officialLandPriceMax ?: repair.officialLandPriceMax,
        officialLandPriceLevel = primary.officialLandPriceLevel ?: repair.officialLandPriceLevel,
        totalSpendingMin = repairRelativeNumber(primary.totalSpendingMin, repair.totalSpendingMin, repair.totalSpendingLevel),
        totalSpendingLevel = primary.totalSpendingLevel ?: repair.totalSpendingLevel,
        foodSpendingMin = repairRelativeNumber(primary.foodSpendingMin, repair.foodSpendingMin, repair.foodSpendingLevel),
        foodSpendingLevel = primary.foodSpendingLevel ?: repair.foodSpendingLevel,
        spendingPerStoreMin = repairRelativeNumber(primary.spendingPerStoreMin, repair.spendingPerStoreMin, repair.spendingPerStoreLevel),
        spendingPerStoreLevel = primary.spendingPerStoreLevel ?: repair.spendingPerStoreLevel,
        commercialTurnoverTypeMin = repairRelativeNumber(primary.commercialTurnoverTypeMin, repair.commercialTurnoverTypeMin, repair.commercialTurnoverTypeLevel),
        commercialTurnoverTypeLevel = primary.commercialTurnoverTypeLevel ?: repair.commercialTurnoverTypeLevel,
        commercialGrowthTypeMin = repairRelativeNumber(primary.commercialGrowthTypeMin, repair.commercialGrowthTypeMin, repair.commercialGrowthTypeLevel),
        commercialGrowthTypeLevel = primary.commercialGrowthTypeLevel ?: repair.commercialGrowthTypeLevel
    )
}

private fun mergeSpatial(primary: VacancySpatialFilter?, repair: VacancySpatialFilter?): VacancySpatialFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        sameCategoryRestaurantCount500mMin = primary.sameCategoryRestaurantCount500mMin ?: repair.sameCategoryRestaurantCount500mMin,
        sameCategoryRestaurantCount500mMax = repairCountMax(primary.sameCategoryRestaurantCount500mMax, repair.sameCategoryRestaurantCount500mMax),
        industryGrowthRate500mMin = primary.industryGrowthRate500mMin ?: repair.industryGrowthRate500mMin
    )
}

private fun mergeStringLists(primary: List<String>?, repair: List<String>?): List<String>? {
    return (primary.orEmpty() + repair.orEmpty())
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
        .takeIf { it.isNotEmpty() }
}

private fun repairMoneyMax(primary: Long?, repair: Long?): Long? {
    if (primary == null) return repair
    if (repair != null && repair >= primary * 5) return repair
    if (repair != null && repair < primary && primary <= (repair * 1.2).toLong()) return repair
    return primary
}

private fun repairArea(primary: BigDecimal?, repair: BigDecimal?): BigDecimal? {
    if (primary == null) return repair
    if (repair != null && repair > primary.multiply(BigDecimal("2.5"))) return repair
    return primary
}

private fun repairPositive(primary: BigDecimal?, repair: BigDecimal?): BigDecimal? {
    if (primary == null) return repair
    if (repair != null && primary <= BigDecimal.ZERO && repair > BigDecimal.ZERO) return repair
    if (repair != null && primary > repair.multiply(BigDecimal("100"))) return repair
    return primary
}

private fun repairRelativeNumber(primary: BigDecimal?, repair: BigDecimal?, repairLevel: String?): BigDecimal? {
    if (!repairLevel.isNullOrBlank()) return repair
    return repairPositive(primary, repair)
}

private fun repairCountMax(primary: Int?, repair: Int?): Int? {
    if (primary == null) return repair
    if (repair != null && repair < primary && primary > repair * 2) return repair
    return primary
}

private fun looksLikeSeoulDistrict(value: String): Boolean = Regex("""^[가-힣]{1,4}구$""").matches(value)

private fun looksLikeBadLocationKeyword(value: String): Boolean {
    return value in setOf("테라스가", "학원가") ||
        value.contains("인구") ||
        Regex("""(상권|주변|근처|후보)$""").containsMatchIn(value)
}

@Component
class OpenAiVacancyPromptClient(
    private val properties: VacancyPromptProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OpenAiVacancyPromptClient::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(properties.openai.timeoutSeconds.coerceAtLeast(1)))
        .build()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    fun isAvailable(): Boolean {
        val openAi = properties.openai
        return openAi.enabled && openAi.apiKey.isNotBlank()
    }

    fun parse(prompt: String): VacancyStructuredFilter? {
        val openAi = properties.openai
        if (!isAvailable()) return null

        val body = mapOf(
            "model" to openAi.model,
            "input" to listOf(
                mapOf("role" to "system", "content" to VacancyPromptSchema.systemPrompt),
                mapOf("role" to "user", "content" to prompt)
            ),
            "text" to mapOf("format" to VacancyPromptSchema.openAiTextFormat),
            "temperature" to 0,
            "max_output_tokens" to openAi.maxOutputTokens.coerceIn(256, 3000)
        )

        return runCatching {
            val request = HttpRequest.newBuilder(URI.create(openAi.endpoint))
                .timeout(Duration.ofSeconds(openAi.timeoutSeconds.coerceAtLeast(1)))
                .header("Authorization", "Bearer ${openAi.apiKey}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                logger.warn("OpenAI vacancy prompt parse failed with status {}", response.statusCode())
                return null
            }
            val responseBody = objectMapper.readValue(response.body(), mapType)
            val text = extractOutputText(responseBody) ?: return null
            objectMapper.readValue(text, VacancyStructuredFilter::class.java)
        }.onFailure {
            logger.warn("OpenAI vacancy prompt parse failed: {}", it.message)
        }.getOrNull()
    }

    private fun extractOutputText(responseBody: Map<String, Any?>): String? {
        (responseBody["output_text"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val output = responseBody["output"] as? List<*> ?: return null
        output.forEach { outputItem ->
            val item = outputItem as? Map<*, *> ?: return@forEach
            val content = item["content"] as? List<*> ?: return@forEach
            content.forEach { contentItem ->
                val contentMap = contentItem as? Map<*, *> ?: return@forEach
                val refusal = contentMap["refusal"] as? String
                if (!refusal.isNullOrBlank()) return null
                val text = contentMap["text"] as? String
                if (!text.isNullOrBlank()) return text.trim()
            }
        }
        return null
    }
}

private object VacancyPromptFallbackParser {
    private val categoryAliases = linkedMapOf(
        Regex("고기집|고깃집|한식|한식당|백반|국밥|삼겹살|갈비") to ("1" to "한식"),
        Regex("중식|중국집|짜장|짬뽕|마라") to ("2" to "중식"),
        Regex("일식|초밥|스시|라멘|돈까스") to ("3" to "일식"),
        Regex("양식|파스타|피자|스테이크") to ("4" to "서양식"),
        Regex("기타\\s*업종|공방") to ("5" to "기타"),
        Regex("뷔페|구내식당|분식|도시락") to ("6" to "구내식당 및 뷔페"),
        Regex("패스트푸드|햄버거|버거") to ("7" to "패스트푸드"),
        Regex("주점|술집|포차|호프|맥주") to ("8" to "주점업"),
        Regex("카페|커피|디저트|베이커리|빵집") to ("9" to "카페/디저트")
    )
    private val moneyPattern = Regex(
        """(월세|보증금|전세|권리금|매매가|매매|관리비)(?:은|는|이|가|도)?[^0-9]{0,12}([0-9]+(?:\.[0-9]+)?)\s*(억원|억|천만원|천만|만원|만)?\s*(이하|이내|미만|아래|내외|정도|쯤|언저리|전후)?"""
    )
    private val compoundMoneyPattern = Regex(
        """(월세|보증금|전세|권리금|매매가|매매|관리비)(?:은|는|이|가|도)?[^0-9]{0,12}([0-9]+(?:\.[0-9]+)?)\s*(억원|억)\s*([0-9]+(?:\.[0-9]+)?)\s*(만원|만)?\s*(이하|이내|미만|아래|내외|정도|쯤|언저리|전후)?"""
    )
    private val areaPattern = Regex(
        """((?:전용|공급)?면적|전용|공급)?\s*([0-9]+(?:\.[0-9]+)?)\s*(평|제곱미터|㎡)\s*(이상|이하|이내|내외|정도|쯤|전후)?"""
    )
    private val approximateTerms = setOf("내외", "정도", "쯤", "언저리", "전후")
    private val seoulDistricts = setOf(
        "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
        "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
        "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"
    )
    private val ignoredDongWords = setOf("유동", "이동", "활동", "테라스가", "학원가")

    fun parse(prompt: String): VacancyStructuredFilter {
        var location = parseLocation(prompt)
        val category = parseCategory(prompt)
        val transactionType = parseTransactionType(prompt)
        var price = VacancyPriceFilter()
        moneyPattern.findAll(prompt).forEach { match ->
            val field = match.groupValues[1]
            val amount = toManwon(match.groupValues[2], match.groupValues[3])
            val qualifier = match.groupValues.getOrNull(4).orEmpty()
            price = applyMoney(price, field, amount, qualifier)
        }
        compoundMoneyPattern.findAll(prompt).forEach { match ->
            val field = match.groupValues[1]
            val amount = toManwon(match.groupValues[2], match.groupValues[3]) +
                toManwon(match.groupValues[4], match.groupValues.getOrNull(5).orEmpty())
            val qualifier = match.groupValues.getOrNull(6).orEmpty()
            price = applyMoney(price, field, amount, qualifier)
        }
        price = price.copy(
            priceNegotiable = if (Regex("""가격\s*협의|협의\s*가능""").containsMatchIn(prompt)) true else price.priceNegotiable,
            rentAdjustable = if (Regex("""임대료\s*조정|월세\s*조정""").containsMatchIn(prompt)) true else price.rentAdjustable,
            rentFreePeriodAvailable = if (Regex("""무상\s*임대|렌트프리|무상임대기간""").containsMatchIn(prompt)) true else price.rentFreePeriodAvailable
        )
        val space = parseSpace(prompt)
        val amenities = parseAmenities(prompt)
        val building = parseBuilding(prompt)
        val commercial = parseCommercial(prompt)
        val spatial = parseSpatial(prompt)
        val sort = parseSort(prompt, category)
        val recognized = location != null ||
            category != null ||
            transactionType != null ||
            !price.empty() ||
            space != null ||
            amenities != null ||
            building != null ||
            commercial != null ||
            spatial != null

        return VacancyStructuredFilter(
            q = if (recognized) null else prompt,
            location = location,
            category = category,
            transactionType = transactionType,
            price = price.takeUnless { it.empty() },
            space = space,
            building = building,
            amenities = amenities,
            commercial = commercial,
            spatial = spatial,
            sort = sort
        )
    }

    private fun parseLocation(prompt: String): VacancyLocationFilter? {
        val subwayKeywords = Regex("""([가-힣A-Za-z0-9]+역)""")
            .findAll(prompt)
            .map { it.value }
            .distinct()
            .toList()
        var locationText = prompt
        subwayKeywords.forEach { locationText = locationText.replace(it, " ") }
        val districts = seoulDistricts
            .filter { locationText.contains(it) }
            .distinct()
        districts.forEach { locationText = locationText.replace(it, " ") }
        val dong = Regex("""([가-힣]{1,}(?:동|가))""")
            .findAll(locationText)
            .map { it.value }
            .filter { it !in ignoredDongWords }
            .distinct()
            .toList()
        val subway = subwayKeywords.singleOrNull()
        val district = districts.singleOrNull()
        if (districts.isEmpty() && dong.isEmpty() && subwayKeywords.isEmpty()) return null
        return VacancyLocationFilter(
            district = district,
            districtKeywords = districts.takeIf { it.isNotEmpty() },
            dong = dong.singleOrNull(),
            dongKeywords = dong.takeIf { it.isNotEmpty() },
            subway = subway,
            subwayKeywords = subwayKeywords.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseCategory(prompt: String): VacancyCategoryFilter? {
        val match = categoryAliases.entries.firstOrNull { it.key.containsMatchIn(prompt) } ?: return null
        val (categoryId, label) = match.value
        if (categoryId == "9" && Regex("""카페(?:는|은|가|도)?[^,.，。]{0,12}(적은|적고|적게|부족|없는|많지|피하)""").containsMatchIn(prompt)) {
            return null
        }
        val asksSuitability = Regex("적합|추천|좋은|맞는|어울리는").containsMatchIn(prompt)
        val explicitScore = Regex("""(?:점수|생존점수|스코어)[^0-9]{0,8}([0-9]{1,3})(?:점)?\s*(이상|부터|넘)?""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::BigDecimal)
        return VacancyCategoryFilter(
            categoryId = categoryId,
            categoryLabel = label,
            scoreMode = "category",
            scoreMin = explicitScore ?: if (asksSuitability && Regex("높은|상위|우수").containsMatchIn(prompt)) BigDecimal("70") else null
        )
    }

    private fun parseTransactionType(prompt: String): String? {
        return when {
            prompt.contains("전세") -> "전세"
            prompt.contains("매매") -> "매매"
            Regex("월세|임대").containsMatchIn(prompt) -> "임대"
            else -> null
        }
    }

    private fun parseSpace(prompt: String): VacancySpaceFilter? {
        val basementMentioned = prompt.contains("지하")
        val basementNegated = Regex("""지하[^,.，。]*?(말고|아닌|아니고|제외|빼고|싫)""").containsMatchIn(prompt)
        val basementOne = Regex("""지하\s*1층""").containsMatchIn(prompt)
        val floorText = Regex("""[0-9]+층""").find(prompt)?.value
        var space = VacancySpaceFilter(
            floorText = floorText?.takeUnless { it == "1층" },
            groundFloor = if (!basementOne && Regex("""(^|[^0-9])1층""").containsMatchIn(prompt)) true else null,
            basement = when {
                basementNegated -> false
                basementMentioned -> true
                else -> null
            }
        )
        areaPattern.findAll(prompt).forEach { match ->
            val areaKind = match.groupValues[1]
            val amount = toSquareMeters(match.groupValues[2], match.groupValues[3])
            val qualifier = match.groupValues.getOrNull(4).orEmpty()
            val (min, max) = numericRange(amount, qualifier)
            space = if (areaKind.startsWith("공급")) {
                space.copy(
                    supplyAreaMin = min ?: space.supplyAreaMin,
                    supplyAreaMax = max ?: space.supplyAreaMax
                )
            } else {
                space.copy(
                    dedicatedAreaMin = min ?: space.dedicatedAreaMin,
                    dedicatedAreaMax = max ?: space.dedicatedAreaMax
                )
            }
        }
        return space.normalized().takeUnless { it.empty() }
    }

    private fun parseBuilding(prompt: String): VacancyBuildingFilter? {
        val building = VacancyBuildingFilter(
            buildingType = when {
                prompt.contains("사무실형") -> "사무실형"
                prompt.contains("상가형") -> "상가형"
                prompt.contains("주택형") -> "주택형"
                else -> null
            },
            direction = Regex("""([동서남북]향)""").find(prompt)?.value,
            approvalDateFrom = Regex("""([0-9]{4})년\s*이후\s*사용승인|([0-9]{4})년\s*이후\s*승인""")
                .find(prompt)
                ?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotBlank() }
                ?.let { "$it-01-01" }
        )
        return building.normalized().takeUnless { it.empty() }
    }

    private fun parseAmenities(prompt: String): VacancyAmenityFilter? {
        val parkingCount = Regex("""주차[^0-9]{0,8}([0-9]+)\s*대\s*이상""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::BigDecimal)
        val restroomCount = Regex("""화장실[^0-9]{0,8}([0-9]+)\s*개\s*이상""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::BigDecimal)
        val amenities = VacancyAmenityFilter(
            parkingAvailable = if (prompt.contains("주차")) true else null,
            parkingCountMin = parkingCount,
            elevatorAvailable = if (prompt.contains("엘리베이터") || prompt.contains("엘베")) true else null,
            terrace = if (prompt.contains("테라스")) true else null,
            rooftop = if (prompt.contains("루프탑")) true else null,
            interior = if (prompt.contains("인테리어")) true else null,
            storage = if (prompt.contains("창고")) true else null,
            airConditioner = if (Regex("""에어컨|냉방|냉난방""").containsMatchIn(prompt)) true else null,
            heater = if (Regex("""난방|냉난방""").containsMatchIn(prompt)) true else null,
            lateNightOperationAvailable = if (prompt.contains("심야") || prompt.contains("야간")) true else null,
            restroomType = when {
                prompt.contains("외부") && Regex("""화장실|남녀""").containsMatchIn(prompt) && !Regex("""외부[^,.，。]*(피하|싫|말고|제외)""").containsMatchIn(prompt) -> "외부"
                Regex("""남녀\s*(구분|분리)""").containsMatchIn(prompt) -> "남녀구분"
                else -> null
            },
            restroomCountMin = restroomCount
        )
        return amenities.takeUnless { it.empty() }
    }

    private fun parseCommercial(prompt: String): VacancyCommercialFilter? {
        val commercial = VacancyCommercialFilter(
            facilityTotalSizeMin = Regex("""시설총규모[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)\s*(평|제곱미터|㎡)?\s*이상""")
                .find(prompt)
                ?.let { toSquareMeters(it.groupValues[1], it.groupValues.getOrNull(2).orEmpty()) },
            locationAreaMin = Regex("""소재지면적[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)\s*(평|제곱미터|㎡)?\s*이상""")
                .find(prompt)
                ?.let { toSquareMeters(it.groupValues[1], it.groupValues.getOrNull(2).orEmpty()) },
            multiUseFacility = if (prompt.contains("다중이용업소")) true else null,
            floatingPopulationQuarterlyMin = parseMetricMinimum(prompt, "유동인구"),
            floatingPopulationQuarterlyLevel = if (Regex("""유동인구[^0-9,.，。]*(많|높|풍부)|유동\s*(많|높)""").containsMatchIn(prompt)) "high" else null,
            residentPopulationQuarterlyLevel = if (Regex("""상주인구|거주인구""").containsMatchIn(prompt)) "high" else null,
            workerPopulationQuarterlyLevel = if (Regex("""직장인구|직장인\s*많""").containsMatchIn(prompt)) "high" else null,
            morningPopulationRatioLevel = if (Regex("""오전\s*유동|아침\s*유동""").containsMatchIn(prompt)) "high" else null,
            lateNightPopulationRatioLevel = if (Regex("""심야\s*유동|야간\s*유동""").containsMatchIn(prompt)) "high" else null,
            weekendPopulationRatioLevel = if (Regex("""주말\s*유동|주말\s*인구""").containsMatchIn(prompt)) "high" else null,
            age2030PopulationRatioLevel = if (Regex("""2030|20대|30대|학생""").containsMatchIn(prompt)) "high" else null,
            femalePopulationRatioLevel = if (Regex("""여성\s*(인구|비율)""").containsMatchIn(prompt)) "high" else null,
            femaleSalesRatioLevel = if (Regex("""여성\s*매출""").containsMatchIn(prompt)) "high" else null,
            restaurantCount500mLevel = if (Regex("""(음식점|식당)[^,.，。]*(많|밀집|풍부)""").containsMatchIn(prompt)) "high" else null,
            cafeCount500mLevel = if (Regex("""카페[^,.，。]*(적|부족|없는|많지|피하)""").containsMatchIn(prompt)) "low" else null,
            cafeToRestaurantRatioMax = if (
                Regex("""(음식점|식당)[^,.，。]*(많|밀집|풍부)""").containsMatchIn(prompt) &&
                Regex("""카페[^,.，。]*(적|부족|없는|많지|피하)""").containsMatchIn(prompt)
            ) BigDecimal("0.30") else null,
            closureRateMax = when {
                Regex("""폐업률[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)\s*%?\s*(이하|이내|미만|아래)""").containsMatchIn(prompt) ->
                    Regex("""폐업률[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)""").find(prompt)?.groupValues?.getOrNull(1)?.let(::BigDecimal)
                else -> null
            },
            closureRateLevel = if (Regex("""폐업률[^,.，。]*(낮|적)""").containsMatchIn(prompt)) "low" else null,
            openingRateLevel = if (Regex("""개업률[^,.，。]*(높|많)""").containsMatchIn(prompt)) "high" else null,
            averageSalesPerStoreLevel = if (Regex("""평균\s*매출|가게당\s*평균매출""").containsMatchIn(prompt)) "high" else null,
            eveningSalesRatioLevel = if (Regex("""저녁\s*매출""").containsMatchIn(prompt)) "high" else null,
            lateNightSalesRatioLevel = if (Regex("""심야\s*매출""").containsMatchIn(prompt)) "high" else null,
            weekendSalesRatioLevel = if (Regex("""주말\s*매출""").containsMatchIn(prompt)) "high" else null,
            age2030SalesRatioLevel = if (Regex("""2030\s*매출|20대\s*매출|30대\s*매출""").containsMatchIn(prompt)) "high" else null,
            officialLandPriceLevel = if (Regex("""공시지가[^,.，。]*(낮|부담)""").containsMatchIn(prompt)) "low" else null,
            totalSpendingLevel = if (Regex("""총\s*지출|전체\s*지출""").containsMatchIn(prompt)) "high" else null,
            foodSpendingLevel = if (Regex("""음식\s*지출|먹거리\s*지출""").containsMatchIn(prompt)) "high" else null,
            spendingPerStoreLevel = if (Regex("""점포당\s*지출""").containsMatchIn(prompt)) "high" else null,
            commercialGrowthTypeLevel = if (Regex("""성장형\s*상권|상권\s*성장""").containsMatchIn(prompt)) "high" else null
        )
        return commercial.takeUnless { it.empty() }
    }

    private fun parseSpatial(prompt: String): VacancySpatialFilter? {
        val sameCategoryMax = Regex("""(경쟁점포|경쟁\s*점포|동종\s*점포|동종\s*식당)[^,.，。]*?([0-9]+)\s*개?\s*(이하|이내|미만|아래)""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(2)
            ?.toIntOrNull()
        val growthMin = Regex("""업종성장률[^0-9-]{0,8}(-?[0-9]+(?:\.[0-9]+)?)\s*%?\s*(이상|부터|넘)""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::BigDecimal)
        val spatial = VacancySpatialFilter(
            sameCategoryRestaurantCount500mMax = sameCategoryMax,
            industryGrowthRate500mMin = growthMin
        )
        return spatial.takeUnless { it.empty() }
    }

    private fun parseMetricMinimum(prompt: String, label: String): BigDecimal? {
        return Regex("""$label[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)\s*(만)?\s*명?(?:보다)?\s*(이상|많|높|넘|초과)""")
            .find(prompt)
            ?.let {
                val value = BigDecimal(it.groupValues[1])
                if (it.groupValues.getOrNull(2) == "만") value.multiply(BigDecimal("10000")) else value
            }
    }

    private fun parseSort(prompt: String, category: VacancyCategoryFilter?): String {
        return when {
            Regex("저렴|싼|낮은 월세").containsMatchIn(prompt) -> "rent_asc"
            Regex("넓은|큰").containsMatchIn(prompt) -> "area_desc"
            Regex("최근|최신").containsMatchIn(prompt) -> "updated_desc"
            category != null -> "score_desc"
            else -> "score_desc"
        }
    }

    private fun applyMoney(price: VacancyPriceFilter, field: String, amount: Long, qualifier: String): VacancyPriceFilter {
        val (min, max) = moneyRange(amount, qualifier)
        return when (field) {
            "월세" -> price.copy(monthlyRentMin = min ?: price.monthlyRentMin, monthlyRentMax = max ?: price.monthlyRentMax)
            "보증금", "전세" -> price.copy(depositMin = min ?: price.depositMin, depositMax = max ?: price.depositMax)
            "권리금" -> price.copy(premiumMin = min ?: price.premiumMin, premiumMax = max ?: price.premiumMax)
            "매매가", "매매" -> price.copy(salePriceMin = min ?: price.salePriceMin, salePriceMax = max ?: price.salePriceMax)
            "관리비" -> price.copy(
                maintenanceFeeMin = min ?: price.maintenanceFeeMin,
                maintenanceFeeMax = max ?: price.maintenanceFeeMax
            )
            else -> price
        }
    }

    private fun moneyRange(amount: Long, qualifier: String): Pair<Long?, Long?> {
        if (qualifier in approximateTerms) {
            val slack = (amount * 0.1).toLong().coerceAtLeast(1)
            return (amount - slack).coerceAtLeast(0) to amount + slack
        }
        if (qualifier == "이상") return amount to null
        return null to amount
    }

    private fun numericRange(amount: BigDecimal, qualifier: String): Pair<BigDecimal?, BigDecimal?> {
        if (qualifier in approximateTerms) {
            val slack = amount.multiply(BigDecimal("0.1")).setScale(2, RoundingMode.HALF_UP)
            return amount.subtract(slack).max(BigDecimal.ZERO) to amount.add(slack)
        }
        if (qualifier == "이상") return amount to null
        return null to amount
    }

    private fun toManwon(numberText: String, unit: String): Long {
        val number = BigDecimal(numberText)
        val multiplier = when (unit) {
            "억원", "억" -> BigDecimal("10000")
            "천만원", "천만" -> BigDecimal("1000")
            else -> BigDecimal.ONE
        }
        return number.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun toSquareMeters(numberText: String, unit: String): BigDecimal {
        val number = BigDecimal(numberText)
        val squareMeters = if (unit == "평") number.multiply(BigDecimal("3.3058")) else number
        return squareMeters.setScale(2, RoundingMode.HALF_UP)
    }
}
