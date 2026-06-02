package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyAmenityFilter
import com.sanggwonai.api.vacancy.dto.VacancyCategoryFilter
import com.sanggwonai.api.vacancy.dto.VacancyLocationFilter
import com.sanggwonai.api.vacancy.dto.VacancyPriceFilter
import com.sanggwonai.api.vacancy.dto.VacancySpaceFilter
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

        val parsed = if (canUseOpenAi) openAiClient.parse(normalizedPrompt) else null
        val result = parsed
            ?.let { VacancyPromptParseResult(filters = it.normalized(), source = "openai") }
            ?: VacancyPromptParseResult(
                filters = VacancyPromptFallbackParser.parse(normalizedPrompt).normalized(),
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
        Regex("뷔페|구내식당") to ("6" to "구내식당 및 뷔페"),
        Regex("패스트푸드|햄버거|버거") to ("7" to "패스트푸드"),
        Regex("주점|술집|포차|호프|맥주") to ("8" to "주점업"),
        Regex("카페|커피|디저트|베이커리|빵집") to ("9" to "카페/디저트")
    )
    private val moneyPattern = Regex(
        """(월세|보증금|전세|권리금|매매가|매매|관리비)\s*([0-9]+(?:\.[0-9]+)?)\s*(억원|억|천만원|천만|만원|만)?\s*(이하|이내|미만|내외|정도|쯤|언저리|전후)?"""
    )
    private val areaPattern = Regex(
        """(전용|공급|면적)?\s*([0-9]+(?:\.[0-9]+)?)\s*(평|제곱미터|㎡)\s*(이상|이하|이내|내외|정도|쯤|전후)?"""
    )
    private val approximateTerms = setOf("내외", "정도", "쯤", "언저리", "전후")

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
        val space = parseSpace(prompt)
        val amenities = parseAmenities(prompt)
        val sort = parseSort(prompt, category)
        val recognized = location != null ||
            category != null ||
            transactionType != null ||
            !price.empty() ||
            space != null ||
            amenities != null

        return VacancyStructuredFilter(
            q = if (recognized) null else prompt,
            location = location,
            category = category,
            transactionType = transactionType,
            price = price.takeUnless { it.empty() },
            space = space,
            amenities = amenities,
            sort = sort
        )
    }

    private fun parseLocation(prompt: String): VacancyLocationFilter? {
        val district = Regex("""([가-힣]+구)""").find(prompt)?.value
        val dong = Regex("""([가-힣]+동)""").find(prompt)?.value
        val subwayKeywords = Regex("""([가-힣A-Za-z0-9]+역)""")
            .findAll(prompt)
            .map { it.value }
            .distinct()
            .toList()
        val subway = subwayKeywords.singleOrNull()
        if (district == null && dong == null && subwayKeywords.isEmpty()) return null
        return VacancyLocationFilter(
            district = district,
            dong = dong,
            subway = subway,
            subwayKeywords = subwayKeywords.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseCategory(prompt: String): VacancyCategoryFilter? {
        val match = categoryAliases.entries.firstOrNull { it.key.containsMatchIn(prompt) } ?: return null
        val (categoryId, label) = match.value
        val asksSuitability = Regex("적합|추천|좋은|맞는|어울리는").containsMatchIn(prompt)
        return VacancyCategoryFilter(
            categoryId = categoryId,
            categoryLabel = label,
            scoreMode = "category",
            scoreMin = if (asksSuitability && Regex("높은|상위|우수").containsMatchIn(prompt)) BigDecimal("70") else null
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
        var space = VacancySpaceFilter(
            floorText = Regex("""[0-9]+층""").find(prompt)?.value,
            groundFloor = if (Regex("""(^|[^0-9])1층""").containsMatchIn(prompt)) true else null,
            basement = if (prompt.contains("지하")) true else null
        )
        areaPattern.findAll(prompt).forEach { match ->
            val areaKind = match.groupValues[1]
            val amount = toSquareMeters(match.groupValues[2], match.groupValues[3])
            val qualifier = match.groupValues.getOrNull(4).orEmpty()
            val (min, max) = numericRange(amount, qualifier)
            space = if (areaKind == "공급") {
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

    private fun parseAmenities(prompt: String): VacancyAmenityFilter? {
        val amenities = VacancyAmenityFilter(
            parkingAvailable = if (prompt.contains("주차")) true else null,
            elevatorAvailable = if (prompt.contains("엘리베이터") || prompt.contains("엘베")) true else null,
            terrace = if (prompt.contains("테라스")) true else null,
            rooftop = if (prompt.contains("루프탑")) true else null,
            interior = if (prompt.contains("인테리어")) true else null,
            storage = if (prompt.contains("창고")) true else null,
            airConditioner = if (prompt.contains("에어컨")) true else null,
            lateNightOperationAvailable = if (prompt.contains("심야") || prompt.contains("야간")) true else null
        )
        return amenities.takeUnless { it.empty() }
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
