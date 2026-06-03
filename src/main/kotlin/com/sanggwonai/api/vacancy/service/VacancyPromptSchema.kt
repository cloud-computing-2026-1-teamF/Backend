package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyCategoryFilter
import com.sanggwonai.api.vacancy.dto.VacancyLocationFilter
import com.sanggwonai.api.vacancy.dto.VacancyPriceFilter
import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter

object VacancyPromptSchema {
    const val VERSION = "vacancy_filter.v1"

    val transactionTypes = listOf("임대", "전세", "매매")
    val scoreModes = listOf("best", "category")
    val sorts = listOf("score_desc", "rent_asc", "rent_desc", "deposit_asc", "area_desc", "updated_desc")
    val relativeLevels = listOf("very_low", "low", "high", "very_high")
    val categories = mapOf(
        "1" to "한식",
        "2" to "중식",
        "3" to "일식",
        "4" to "서양식",
        "5" to "기타",
        "6" to "구내식당 및 뷔페",
        "7" to "패스트푸드",
        "8" to "주점업",
        "9" to "카페/디저트"
    )

    val systemPrompt = """
        You convert Korean commercial vacancy search text into the exact JSON filter contract.
        Use only the schema keys and enum values. Use null for every unknown or unspecified field.
        Money fields are in 만원. Convert 1억 to 10000 and 1천만원 to 1000.
        Apply this money conversion to every price field, including 권리금 and 매매가. Examples: 권리금 1억 이하 -> premium_max=10000, 매매가 20억 이하 -> sale_price_max=200000.
        Area fields are in 제곱미터. Convert 평 to square meters by multiplying by 3.3058. Examples: 전용 30평 이상 -> dedicated_area_min=99.17, 전용 40평 이하 -> dedicated_area_max=132.23.
        If the user says 내외, 정도, 쯤, 언저리, or 전후 for a number, use about a 10 percent range.
        Boolean intent should be literal: 가격협의 가능 -> price.price_negotiable=true, 임대료 조정 가능 -> price.rent_adjustable=true, 무상임대기간/렌트프리 -> price.rent_free_period_available=true, 엘리베이터/엘베 있음 -> amenities.elevator_available=true, 테라스/루프탑 있으면 좋음 -> the corresponding amenity boolean true.
        Canonical transaction_type values are: 임대, 전세, 매매.
        Canonical score_mode values are: best, category.
        Canonical sort values are: score_desc, rent_asc, rent_desc, deposit_asc, area_desc, updated_desc.
        If the user asks for 넓은/큰 매장 or explicitly wants larger area first, set sort=area_desc. If the user asks for 낮은 월세/저렴한 월세 order, set sort=rent_asc.
        Category ids and labels are: 1=한식, 2=중식, 3=일식, 4=서양식, 5=기타, 6=구내식당 및 뷔페, 7=패스트푸드, 8=주점업, 9=카페/디저트.
        Alias examples: 고기집/고깃집/한식당 -> 1, 중국집/중식당 -> 2, 일식집/초밥 -> 3, 양식/파스타 -> 4, 뷔페/구내식당 -> 6, 햄버거/패스트푸드 -> 7, 술집/주점/포차 -> 8, 카페/디저트/베이커리 -> 9.
        If a prompt asks for a business-suitable place, set category.category_id, category.category_label, category.score_mode=category, and sort=score_desc.
        For station-area prompts, put every mentioned station into location.subway_keywords. Example: "시청역이나 강남역 주변" -> ["시청역","강남역"].
        If exactly one station is mentioned, location.subway may also contain that station. If multiple stations are mentioned, keep location.subway null and use location.subway_keywords.
        For multi-dong prompts, put every mentioned dong into location.dong_keywords. Example: "논현동이나 신사동" -> ["논현동","신사동"]. If exactly one dong is mentioned, location.dong may also contain it. If multiple dongs are mentioned, keep location.dong null and use location.dong_keywords.
        Do not invent area_id unless it is explicitly provided. Put Korean 구 terms into location.district.
        For commercial metrics, explicit numeric prompts must use numeric min/max fields. Examples: 유동인구 1만명 이상 -> floating_population_quarterly_min=10000, 카페 30개 이하 -> cafe_count500m_max=30.
        For vague soft phrases without a number, never invent an absolute number. Use relative level fields instead: 많은/높은 -> *_level="high", 아주 많은/상위 -> "very_high", 적은/낮은 -> "low", 아주 적은 -> "very_low".
        If the prompt compares two nearby store counts, use ratios when possible. Example: "식당은 많은데 카페는 적은/많이 없는" -> restaurant_count500m_level="high" and cafe_to_restaurant_ratio_max=0.30, not cafe_count500m_max.
        Soft commercial examples: 유동인구 많은/학생 유동 많은 -> floating_population_quarterly_level="high"; 직장인구 많은 -> worker_population_quarterly_level="high"; 2030 많은/학생 많은 -> age2030_population_ratio_level="high"; 음식점 많은 -> restaurant_count500m_level="high"; 카페 적은/카페 너무 많지 않은/카페 많은 곳 피함 -> cafe_count500m_level="low"; 여성 매출 비율 높은 -> female_sales_ratio_level="high"; 여성 인구 비율 높은 -> female_population_ratio_level="high"; 저녁매출/심야매출/주말매출 높은 -> matching *_sales_ratio_level="high"; 폐업률 낮음 -> closure_rate_level="low"; 개업률 높음 -> opening_rate_level="high"; 공시지가 부담 낮음 -> official_land_price_level="low"; 음식 지출/총 지출 높은 -> food_spending_level or total_spending_level="high".
        Map "사무실형 상가" to building.building_type="사무실형".
    """.trimIndent()

    val jsonSchema: Map<String, Any?> = objectSchema(
        properties = linkedMapOf(
            "q" to nullableString("Fallback keyword text search when the prompt contains a meaningful term that is not represented by another field."),
            "location" to nullableObject(locationProperties()),
            "category" to nullableObject(categoryProperties()),
            "transaction_type" to nullableEnum(transactionTypes, "거래유형. Existing backend values only."),
            "price" to nullableObject(priceProperties()),
            "space" to nullableObject(spaceProperties()),
            "building" to nullableObject(buildingProperties()),
            "amenities" to nullableObject(amenityProperties()),
            "commercial" to nullableObject(commercialProperties()),
            "spatial" to nullableObject(spatialProperties()),
            "sort" to nullableEnum(sorts, "Result sort wire value."),
            "page" to nullableInteger("Zero-based page number. Usually null for prompt parsing."),
            "size" to nullableInteger("Page size. Usually null for prompt parsing.")
        ),
        nullable = false,
        description = "Canonical structured vacancy filter."
    )

    val openAiTextFormat: Map<String, Any?> = mapOf(
        "type" to "json_schema",
        "name" to "vacancy_filter",
        "strict" to true,
        "schema" to jsonSchema
    )

    val enums: Map<String, Any> = mapOf(
        "transaction_type" to transactionTypes,
        "score_mode" to scoreModes,
        "sort" to sorts,
        "relative_level" to relativeLevels,
        "categories" to categories
    )

    val example = VacancyStructuredFilter(
        location = VacancyLocationFilter(
            district = "송파구",
            dong = "방이동"
        ),
        category = VacancyCategoryFilter(
            categoryId = "1",
            categoryLabel = "한식",
            scoreMode = "category"
        ),
        transactionType = "임대",
        price = VacancyPriceFilter(
            monthlyRentMin = 450,
            monthlyRentMax = 550
        ),
        sort = "score_desc"
    )

    private fun locationProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "area_id" to nullableString("행정동 코드. Do not guess."),
        "province" to nullableString("시도, for example 서울특별시."),
        "district" to nullableString("구, for example 송파구."),
        "dong" to nullableString("동, for example 방이동."),
        "dong_keywords" to nullableStringArray("Multiple dong keywords, for example [\"논현동\", \"신사동\"]. Use this for A동이나 B동 prompts."),
        "address" to nullableString("도로명주소/지번주소/building keyword."),
        "subway" to nullableString("지하철역 keyword, for example 잠실역."),
        "subway_keywords" to nullableStringArray("Multiple station keywords, for example [\"시청역\", \"강남역\"]. Use this for A역이나 B역 prompts."),
        "latitude" to nullableNumber("Center latitude for radius search."),
        "longitude" to nullableNumber("Center longitude for radius search."),
        "radius_m" to nullableInteger("Radius in meters, max 5000.")
    )

    private fun categoryProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "category_id" to nullableEnum(categories.keys.toList(), "Business category id."),
        "category_label" to nullableEnum(categories.values.toList(), "Business category label."),
        "score_mode" to nullableEnum(scoreModes, "Use category when category_id is specified."),
        "score_min" to nullableNumber("Minimum survival score percent, 0 to 100."),
        "recommended_only" to nullableBoolean("Whether to keep only recommended category-score rows.")
    )

    private fun priceProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "monthly_rent_min" to nullableInteger("월세 minimum in 만원."),
        "monthly_rent_max" to nullableInteger("월세 maximum in 만원."),
        "deposit_min" to nullableInteger("보증금/전세금 minimum in 만원."),
        "deposit_max" to nullableInteger("보증금/전세금 maximum in 만원."),
        "maintenance_fee_min" to nullableInteger("관리비 minimum in 만원."),
        "maintenance_fee_max" to nullableInteger("관리비 maximum in 만원."),
        "premium_min" to nullableInteger("권리금 minimum in 만원."),
        "premium_max" to nullableInteger("권리금 maximum in 만원."),
        "sale_price_min" to nullableInteger("매매가 minimum in 만원."),
        "sale_price_max" to nullableInteger("매매가 maximum in 만원."),
        "price_negotiable" to nullableBoolean("가격협의."),
        "rent_adjustable" to nullableBoolean("임대료조정가능."),
        "rent_free_period_available" to nullableBoolean("무상임대기간.")
    )

    private fun spaceProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "dedicated_area_min" to nullableNumber("전용면적 minimum in 제곱미터."),
        "dedicated_area_max" to nullableNumber("전용면적 maximum in 제곱미터."),
        "supply_area_min" to nullableNumber("공급면적 minimum in 제곱미터."),
        "supply_area_max" to nullableNumber("공급면적 maximum in 제곱미터."),
        "floor_text" to nullableString("층 text keyword, for example 1층."),
        "ground_floor" to nullableBoolean("1층 preference."),
        "basement" to nullableBoolean("지하층 preference.")
    )

    private fun buildingProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "building_name" to nullableString("건물명 keyword."),
        "building_type" to nullableString("건물유형 keyword."),
        "building_use" to nullableString("건물용도 keyword."),
        "building_grade" to nullableString("건물등급 keyword."),
        "direction" to nullableString("방향 keyword."),
        "approval_date_from" to nullableString("사용승인일 lower bound, yyyyMMdd or yyyy-MM-dd if present."),
        "approval_date_to" to nullableString("사용승인일 upper bound, yyyyMMdd or yyyy-MM-dd if present.")
    )

    private fun amenityProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "elevator_available" to nullableBoolean("엘리베이터여부."),
        "parking_available" to nullableBoolean("주차여부."),
        "parking_count_min" to nullableNumber("주차면수 minimum."),
        "terrace" to nullableBoolean("테라스."),
        "rooftop" to nullableBoolean("루프탑."),
        "interior" to nullableBoolean("인테리어."),
        "storage" to nullableBoolean("창고."),
        "air_conditioner" to nullableBoolean("에어컨."),
        "heater" to nullableBoolean("난방기."),
        "late_night_operation_available" to nullableBoolean("심야영업가능."),
        "restroom_type" to nullableString("화장실유형 keyword."),
        "restroom_count_min" to nullableNumber("화장실수 minimum.")
    )

    private fun commercialProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "facility_total_size_min" to nullableNumber("시설총규모 minimum."),
        "facility_total_size_max" to nullableNumber("시설총규모 maximum."),
        "location_area_min" to nullableNumber("소재지면적 minimum."),
        "location_area_max" to nullableNumber("소재지면적 maximum."),
        "multi_use_facility" to nullableBoolean("다중이용업소여부."),
        "floating_population_quarterly_min" to nullableNumber("유동인구 분기평균 minimum."),
        "floating_population_quarterly_level" to nullableEnum(relativeLevels, "Relative 유동인구 level for vague phrases."),
        "resident_population_quarterly_min" to nullableNumber("상주인구 분기평균 minimum."),
        "resident_population_quarterly_level" to nullableEnum(relativeLevels, "Relative 상주인구 level for vague phrases."),
        "worker_population_quarterly_min" to nullableNumber("직장인구 분기평균 minimum."),
        "worker_population_quarterly_level" to nullableEnum(relativeLevels, "Relative 직장인구 level for vague phrases."),
        "evening_population_ratio_min" to nullableNumber("저녁 인구 비율 minimum."),
        "evening_population_ratio_level" to nullableEnum(relativeLevels, "Relative 저녁 인구 비율 level."),
        "late_night_population_ratio_min" to nullableNumber("심야 인구 비율 minimum."),
        "late_night_population_ratio_level" to nullableEnum(relativeLevels, "Relative 심야 인구 비율 level."),
        "morning_population_ratio_min" to nullableNumber("아침 인구 비율 minimum."),
        "morning_population_ratio_level" to nullableEnum(relativeLevels, "Relative 아침 인구 비율 level."),
        "weekend_population_ratio_min" to nullableNumber("주말 인구 비율 minimum."),
        "weekend_population_ratio_level" to nullableEnum(relativeLevels, "Relative 주말 인구 비율 level."),
        "age2030_population_ratio_min" to nullableNumber("2030 인구 비율 minimum."),
        "age2030_population_ratio_level" to nullableEnum(relativeLevels, "Relative 2030 인구 비율 level."),
        "age40_plus_population_ratio_min" to nullableNumber("40대 이상 인구 비율 minimum."),
        "age40_plus_population_ratio_level" to nullableEnum(relativeLevels, "Relative 40대 이상 인구 비율 level."),
        "female_population_ratio_min" to nullableNumber("여성 인구 비율 minimum."),
        "female_population_ratio_level" to nullableEnum(relativeLevels, "Relative 여성 인구 비율 level."),
        "restaurant_count500m_min" to nullableInteger("500m 식당수 minimum."),
        "restaurant_count500m_max" to nullableInteger("500m 식당수 maximum."),
        "restaurant_count500m_level" to nullableEnum(relativeLevels, "Relative 500m 식당수 level."),
        "cafe_count500m_min" to nullableInteger("500m 카페수 minimum."),
        "cafe_count500m_max" to nullableInteger("500m 카페수 maximum."),
        "cafe_count500m_level" to nullableEnum(relativeLevels, "Relative 500m 카페수 level."),
        "cafe_to_restaurant_ratio_max" to nullableNumber("Maximum cafe_count500m / restaurant_count500m ratio."),
        "closure_rate_max" to nullableNumber("동네 폐업률 maximum."),
        "closure_rate_level" to nullableEnum(relativeLevels, "Relative 동네 폐업률 level."),
        "opening_rate_min" to nullableNumber("동네 개업률 minimum."),
        "opening_rate_level" to nullableEnum(relativeLevels, "Relative 동네 개업률 level."),
        "average_sales_per_store_min" to nullableNumber("가게당 평균매출 minimum."),
        "average_sales_per_store_level" to nullableEnum(relativeLevels, "Relative 가게당 평균매출 level."),
        "evening_sales_ratio_min" to nullableNumber("저녁매출 비율 minimum."),
        "evening_sales_ratio_level" to nullableEnum(relativeLevels, "Relative 저녁매출 비율 level."),
        "late_night_sales_ratio_min" to nullableNumber("심야매출 비율 minimum."),
        "late_night_sales_ratio_level" to nullableEnum(relativeLevels, "Relative 심야매출 비율 level."),
        "weekend_sales_ratio_min" to nullableNumber("주말매출 비율 minimum."),
        "weekend_sales_ratio_level" to nullableEnum(relativeLevels, "Relative 주말매출 비율 level."),
        "age2030_sales_ratio_min" to nullableNumber("2030매출 비율 minimum."),
        "age2030_sales_ratio_level" to nullableEnum(relativeLevels, "Relative 2030매출 비율 level."),
        "female_sales_ratio_min" to nullableNumber("여성매출 비율 minimum."),
        "female_sales_ratio_level" to nullableEnum(relativeLevels, "Relative 여성매출 비율 level."),
        "official_land_price_max" to nullableNumber("공시지가 maximum."),
        "official_land_price_level" to nullableEnum(relativeLevels, "Relative 공시지가 level."),
        "total_spending_min" to nullableNumber("지출 총금액 minimum."),
        "total_spending_level" to nullableEnum(relativeLevels, "Relative 지출 총금액 level."),
        "food_spending_min" to nullableNumber("음식 지출 총금액 minimum."),
        "food_spending_level" to nullableEnum(relativeLevels, "Relative 음식 지출 총금액 level."),
        "spending_per_store_min" to nullableNumber("점포당 지출 minimum."),
        "spending_per_store_level" to nullableEnum(relativeLevels, "Relative 점포당 지출 level."),
        "commercial_turnover_type_min" to nullableNumber("상권 교체활발형 minimum."),
        "commercial_turnover_type_level" to nullableEnum(relativeLevels, "Relative 상권 교체활발형 level."),
        "commercial_growth_type_min" to nullableNumber("상권 성장형 minimum."),
        "commercial_growth_type_level" to nullableEnum(relativeLevels, "Relative 상권 성장형 level.")
    )

    private fun spatialProperties(): LinkedHashMap<String, Map<String, Any?>> = linkedMapOf(
        "same_category_restaurant_count500m_min" to nullableInteger("500m 동종 식당수 minimum."),
        "same_category_restaurant_count500m_max" to nullableInteger("500m 동종 식당수 maximum."),
        "industry_growth_rate500m_min" to nullableNumber("500m 업종성장률 minimum.")
    )

    private fun objectSchema(
        properties: LinkedHashMap<String, Map<String, Any?>>,
        nullable: Boolean,
        description: String
    ): Map<String, Any?> = linkedMapOf(
        "type" to if (nullable) listOf("object", "null") else "object",
        "description" to description,
        "additionalProperties" to false,
        "properties" to properties,
        "required" to properties.keys.toList()
    )

    private fun nullableObject(properties: LinkedHashMap<String, Map<String, Any?>>): Map<String, Any?> {
        return objectSchema(properties, nullable = true, description = "Nullable nested filter object.")
    }

    private fun nullableString(description: String): Map<String, Any?> = linkedMapOf(
        "type" to listOf("string", "null"),
        "description" to description
    )

    private fun nullableStringArray(description: String): Map<String, Any?> = linkedMapOf(
        "type" to listOf("array", "null"),
        "description" to description,
        "items" to mapOf("type" to "string")
    )

    private fun nullableNumber(description: String): Map<String, Any?> = linkedMapOf(
        "type" to listOf("number", "null"),
        "description" to description
    )

    private fun nullableInteger(description: String): Map<String, Any?> = linkedMapOf(
        "type" to listOf("integer", "null"),
        "description" to description
    )

    private fun nullableBoolean(description: String): Map<String, Any?> = linkedMapOf(
        "type" to listOf("boolean", "null"),
        "description" to description
    )

    private fun nullableEnum(values: List<String>, description: String): Map<String, Any?> = linkedMapOf(
        "type" to listOf("string", "null"),
        "description" to description,
        "enum" to values + listOf(null)
    )
}
