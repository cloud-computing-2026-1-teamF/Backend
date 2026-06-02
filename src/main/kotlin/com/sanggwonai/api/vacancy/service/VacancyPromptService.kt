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
import java.util.LinkedHashMap

data class VacancyPromptParseResult(
    val filters: VacancyStructuredFilter,
    val source: String,
    val schemaVersion: String = VacancyPromptSchema.VERSION
)

@Service
class VacancyPromptService(
    private val openAiClient: OpenAiVacancyPromptClient,
    properties: VacancyPromptProperties
) {
    private val cache = object : LinkedHashMap<String, VacancyPromptParseResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VacancyPromptParseResult>?): Boolean {
            return size > properties.cacheSize.coerceAtLeast(0)
        }
    }

    fun parse(prompt: String, allowOpenAi: Boolean): VacancyPromptParseResult {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty()) {
            return VacancyPromptParseResult(
                filters = VacancyStructuredFilter(sort = "score_desc").normalized(),
                source = "empty"
            )
        }

        val canUseOpenAi = allowOpenAi && openAiClient.isAvailable()
        val cacheKey = "${if (canUseOpenAi) "openai" else "fallback"}:$normalizedPrompt"
        synchronized(cache) {
            cache[cacheKey]?.let {
                return it.copy(source = if (it.source == "openai") "cache" else it.source)
            }
        }

        val fallbackFilters = VacancyPromptFallbackParser.parse(normalizedPrompt)
        val parsed = if (canUseOpenAi) openAiClient.parse(normalizedPrompt) else null
        val result = parsed
            ?.let { VacancyPromptParseResult(filters = mergePromptRepairs(it, fallbackFilters).normalized(), source = "openai") }
            ?: VacancyPromptParseResult(
                filters = fallbackFilters.normalized(),
                source = "fallback"
            )

        if (result.source == "openai" || !canUseOpenAi) {
            synchronized(cache) {
                cache[cacheKey] = result
            }
        }
        return result
    }
}

private fun mergePromptRepairs(primary: VacancyStructuredFilter, repair: VacancyStructuredFilter): VacancyStructuredFilter {
    return primary.copy(
        location = mergeLocation(primary.location, repair.location),
        category = mergeCategory(primary.category, repair.category),
        transactionType = primary.transactionType ?: repair.transactionType,
        price = mergePrice(primary.price, repair.price),
        space = mergeSpace(primary.space, repair.space),
        building = mergeBuilding(primary.building, repair.building),
        amenities = mergeAmenities(primary.amenities, repair.amenities),
        commercial = mergeCommercial(primary.commercial, repair.commercial),
        spatial = mergeSpatial(primary.spatial, repair.spatial),
        sort = if (primary.sort == null || primary.sort == "score_desc") repair.sort ?: primary.sort else primary.sort
    )
}

private fun mergeLocation(primary: VacancyLocationFilter?, repair: VacancyLocationFilter?): VacancyLocationFilter? {
    if (primary == null) return repair
    if (repair == null) return primary
    return primary.copy(
        district = primary.district?.takeIf(::looksLikeSeoulDistrict) ?: repair.district,
        dong = primary.dong?.takeUnless(::looksLikeBadLocationKeyword) ?: repair.dong,
        dongKeywords = mergeStringLists(
            primary.dongKeywords?.filterNot(::looksLikeBadLocationKeyword),
            repair.dongKeywords?.filterNot(::looksLikeBadLocationKeyword)
        ),
        subway = primary.subway ?: repair.subway,
        subwayKeywords = mergeStringLists(primary.subwayKeywords, repair.subwayKeywords)
    )
}

private fun mergeCategory(primary: VacancyCategoryFilter?, repair: VacancyCategoryFilter?): VacancyCategoryFilter? {
    if (primary == null || primary.empty()) return repair
    if (repair == null || repair.empty()) return primary
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
        floatingPopulationQuarterlyMin = repairPositive(primary.floatingPopulationQuarterlyMin, repair.floatingPopulationQuarterlyMin),
        residentPopulationQuarterlyMin = repairPositive(primary.residentPopulationQuarterlyMin, repair.residentPopulationQuarterlyMin),
        workerPopulationQuarterlyMin = repairPositive(primary.workerPopulationQuarterlyMin, repair.workerPopulationQuarterlyMin),
        eveningPopulationRatioMin = repairPositive(primary.eveningPopulationRatioMin, repair.eveningPopulationRatioMin),
        lateNightPopulationRatioMin = repairPositive(primary.lateNightPopulationRatioMin, repair.lateNightPopulationRatioMin),
        morningPopulationRatioMin = repairPositive(primary.morningPopulationRatioMin, repair.morningPopulationRatioMin),
        weekendPopulationRatioMin = repairPositive(primary.weekendPopulationRatioMin, repair.weekendPopulationRatioMin),
        age2030PopulationRatioMin = repairPositive(primary.age2030PopulationRatioMin, repair.age2030PopulationRatioMin),
        age40PlusPopulationRatioMin = repairPositive(primary.age40PlusPopulationRatioMin, repair.age40PlusPopulationRatioMin),
        femalePopulationRatioMin = repairPositive(primary.femalePopulationRatioMin, repair.femalePopulationRatioMin),
        restaurantCount500mMin = primary.restaurantCount500mMin ?: repair.restaurantCount500mMin,
        restaurantCount500mMax = primary.restaurantCount500mMax ?: repair.restaurantCount500mMax,
        cafeCount500mMin = primary.cafeCount500mMin ?: repair.cafeCount500mMin,
        cafeCount500mMax = primary.cafeCount500mMax ?: repair.cafeCount500mMax,
        closureRateMax = primary.closureRateMax ?: repair.closureRateMax,
        openingRateMin = repairPositive(primary.openingRateMin, repair.openingRateMin),
        averageSalesPerStoreMin = repairPositive(primary.averageSalesPerStoreMin, repair.averageSalesPerStoreMin),
        eveningSalesRatioMin = repairPositive(primary.eveningSalesRatioMin, repair.eveningSalesRatioMin),
        lateNightSalesRatioMin = repairPositive(primary.lateNightSalesRatioMin, repair.lateNightSalesRatioMin),
        weekendSalesRatioMin = repairPositive(primary.weekendSalesRatioMin, repair.weekendSalesRatioMin),
        age2030SalesRatioMin = repairPositive(primary.age2030SalesRatioMin, repair.age2030SalesRatioMin),
        femaleSalesRatioMin = repairPositive(primary.femaleSalesRatioMin, repair.femaleSalesRatioMin),
        officialLandPriceMax = primary.officialLandPriceMax ?: repair.officialLandPriceMax,
        totalSpendingMin = repairPositive(primary.totalSpendingMin, repair.totalSpendingMin),
        foodSpendingMin = repairPositive(primary.foodSpendingMin, repair.foodSpendingMin),
        spendingPerStoreMin = repairPositive(primary.spendingPerStoreMin, repair.spendingPerStoreMin),
        commercialTurnoverTypeMin = repairPositive(primary.commercialTurnoverTypeMin, repair.commercialTurnoverTypeMin),
        commercialGrowthTypeMin = repairPositive(primary.commercialGrowthTypeMin, repair.commercialGrowthTypeMin)
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

private fun repairCountMax(primary: Int?, repair: Int?): Int? {
    if (primary == null) return repair
    if (repair != null && repair < primary && primary > repair * 2) return repair
    return primary
}

private fun looksLikeSeoulDistrict(value: String): Boolean = Regex("""^[가-힣]{1,4}구$""").matches(value)

private fun looksLikeBadLocationKeyword(value: String): Boolean {
    return value in setOf("테라스가", "학원가") || Regex("""(상권|주변|근처|후보)$""").containsMatchIn(value)
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
        val district = seoulDistricts.firstOrNull { locationText.contains(it) }
        district?.let { locationText = locationText.replace(it, " ") }
        val dong = Regex("""([가-힣]{1,}(?:동|가))""")
            .findAll(locationText)
            .map { it.value }
            .filter { it !in ignoredDongWords }
            .distinct()
            .toList()
        val subway = subwayKeywords.singleOrNull()
        if (district == null && dong.isEmpty() && subwayKeywords.isEmpty()) return null
        return VacancyLocationFilter(
            district = district,
            dong = dong.singleOrNull(),
            dongKeywords = dong.takeIf { it.isNotEmpty() },
            subway = subway,
            subwayKeywords = subwayKeywords.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseCategory(prompt: String): VacancyCategoryFilter? {
        val match = categoryAliases.entries.firstOrNull { it.key.containsMatchIn(prompt) } ?: return null
        val (categoryId, label) = match.value
        if (categoryId == "9" && Regex("""카페(?:는|은|가|도)?\s*(적은|적고|적게|부족|없는)""").containsMatchIn(prompt)) {
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
            floatingPopulationQuarterlyMin = if (Regex("""유동인구|유동\s*많|유동\s*높""").containsMatchIn(prompt)) BigDecimal("200000") else null,
            residentPopulationQuarterlyMin = if (Regex("""상주인구|거주인구""").containsMatchIn(prompt)) BigDecimal("200000") else null,
            workerPopulationQuarterlyMin = if (Regex("""직장인구|직장인\s*많""").containsMatchIn(prompt)) BigDecimal("200000") else null,
            morningPopulationRatioMin = if (Regex("""오전\s*유동|아침\s*유동""").containsMatchIn(prompt)) BigDecimal("0.20") else null,
            lateNightPopulationRatioMin = if (Regex("""심야\s*유동|야간\s*유동""").containsMatchIn(prompt)) BigDecimal("0.08") else null,
            weekendPopulationRatioMin = if (Regex("""주말\s*유동|주말\s*인구""").containsMatchIn(prompt)) BigDecimal("0.20") else null,
            age2030PopulationRatioMin = if (Regex("""2030|20대|30대|학생""").containsMatchIn(prompt)) BigDecimal("0.30") else null,
            femalePopulationRatioMin = if (Regex("""여성\s*(인구|비율)""").containsMatchIn(prompt)) BigDecimal("0.45") else null,
            femaleSalesRatioMin = if (Regex("""여성\s*매출""").containsMatchIn(prompt)) BigDecimal("0.35") else null,
            restaurantCount500mMin = if (Regex("""(음식점|식당)[^,.，。]*(많|밀집|풍부)""").containsMatchIn(prompt)) 100 else null,
            cafeCount500mMax = if (Regex("""카페[^,.，。]*(적|부족|없는|많지|피하)""").containsMatchIn(prompt)) 30 else null,
            closureRateMax = when {
                Regex("""폐업률[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)\s*%?\s*(이하|이내|미만|아래)""").containsMatchIn(prompt) ->
                    Regex("""폐업률[^0-9]{0,8}([0-9]+(?:\.[0-9]+)?)""").find(prompt)?.groupValues?.getOrNull(1)?.let(::BigDecimal)
                Regex("""폐업률[^,.，。]*(낮|적)""").containsMatchIn(prompt) -> BigDecimal("2")
                else -> null
            },
            openingRateMin = if (Regex("""개업률[^,.，。]*(높|많)""").containsMatchIn(prompt)) BigDecimal("1") else null,
            averageSalesPerStoreMin = if (Regex("""평균\s*매출|가게당\s*평균매출""").containsMatchIn(prompt)) BigDecimal("20000000") else null,
            eveningSalesRatioMin = if (Regex("""저녁\s*매출""").containsMatchIn(prompt)) BigDecimal("0.20") else null,
            lateNightSalesRatioMin = if (Regex("""심야\s*매출""").containsMatchIn(prompt)) BigDecimal("0.08") else null,
            weekendSalesRatioMin = if (Regex("""주말\s*매출""").containsMatchIn(prompt)) BigDecimal("0.20") else null,
            age2030SalesRatioMin = if (Regex("""2030\s*매출|20대\s*매출|30대\s*매출""").containsMatchIn(prompt)) BigDecimal("0.20") else null,
            officialLandPriceMax = if (Regex("""공시지가[^,.，。]*(낮|부담)""").containsMatchIn(prompt)) BigDecimal("20000000") else null,
            totalSpendingMin = if (Regex("""총\s*지출|전체\s*지출""").containsMatchIn(prompt)) BigDecimal("1000000000") else null,
            foodSpendingMin = if (Regex("""음식\s*지출|먹거리\s*지출""").containsMatchIn(prompt)) BigDecimal("1000000000") else null,
            spendingPerStoreMin = if (Regex("""점포당\s*지출""").containsMatchIn(prompt)) BigDecimal("1000000") else null,
            commercialGrowthTypeMin = if (Regex("""성장형\s*상권|상권\s*성장""").containsMatchIn(prompt)) BigDecimal("1") else null
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
