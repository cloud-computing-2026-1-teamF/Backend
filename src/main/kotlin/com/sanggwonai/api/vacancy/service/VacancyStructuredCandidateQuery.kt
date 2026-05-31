package com.sanggwonai.api.vacancy.service

import com.sanggwonai.api.vacancy.dto.VacancyStructuredFilter
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.cos

@Component
class VacancyStructuredCandidateQuery(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun findCandidateIds(filters: VacancyStructuredFilter): Set<String>? {
        val normalized = filters.normalized()
        val where = mutableListOf<String>()
        val params = MapSqlParameterSource()
        val location = normalized.location
        val category = normalized.category
        val price = normalized.price
        val space = normalized.space
        val building = normalized.building
        val amenities = normalized.amenities
        val commercial = normalized.commercial
        val spatial = normalized.spatial
        val categoryId = category?.categoryId ?: category?.categoryLabel?.let(::categoryIdFromLabel)

        addText(where, params, """cf."행정동_행정동_코드"""", location?.areaId, "areaId")
        addText(where, params, """v."시도"""", location?.province, "province")
        addText(where, params, """v."구"""", location?.district, "district")
        addText(where, params, """v."동"""", location?.dong, "dong")
        addCombinedText(
            where = where,
            params = params,
            fields = listOf("""v."도로명주소"""", """v."지번주소"""", """v."상세주소"""", """v."건물명""""),
            rawValue = location?.address,
            name = "address"
        )
        addText(where, params, """v."지하철"""", location?.subway, "subway")
        addCombinedText(
            where = where,
            params = params,
            fields = listOf(
                "v.property_id",
                """v."도로명주소"""",
                """v."지번주소"""",
                """v."건물명"""",
                """v."시도"""",
                """v."구"""",
                """v."동"""",
                """v."상세주소"""",
                """v."거래유형"""",
                """v."건물유형"""",
                """v."건물용도"""",
                """v."업종대분류"""",
                """v."업종중분류"""",
                """v."지하철"""",
                """cf."행정동_행정동_명""""
            ),
            rawValue = normalized.q,
            name = "q"
        )
        addRadiusBox(where, params, location?.latitude, location?.longitude, location?.radiusM)

        categoryId?.let {
            where += "cs.category_id = :categoryId"
            params.addValue("categoryId", it)
        }
        category?.scoreMin?.let {
            where += """(cs."생존점수" * 100) >= :scoreMin"""
            params.addValue("scoreMin", it)
        }
        if (category?.recommendedOnly == true) {
            where += """cs."추천여부" = 1"""
        }

        normalized.transactionType?.let {
            where += """v."거래유형" = :transactionType"""
            params.addValue("transactionType", it)
        }

        addRange(where, params, """v."월세_만원"""", price?.monthlyRentMin, price?.monthlyRentMax, "monthlyRent")
        addRange(where, params, """v."보증금_만원"""", price?.depositMin, price?.depositMax, "deposit")
        addRange(where, params, """v."관리비_만원"""", price?.maintenanceFeeMin, price?.maintenanceFeeMax, "maintenanceFee")
        addRange(where, params, """v."권리금_만원"""", price?.premiumMin, price?.premiumMax, "premium")
        addRange(where, params, """v."매매가_만원"""", price?.salePriceMin, price?.salePriceMax, "salePrice")
        addBoolean(where, params, """v."가격협의"""", price?.priceNegotiable, "priceNegotiable")
        addBoolean(where, params, """v."임대료조정가능"""", price?.rentAdjustable, "rentAdjustable")
        addBoolean(where, params, """v."무상임대기간"""", price?.rentFreePeriodAvailable, "rentFreePeriodAvailable")

        addRange(where, params, """v."전용면적_제곱미터"""", space?.dedicatedAreaMin, space?.dedicatedAreaMax, "dedicatedArea")
        addRange(where, params, """v."공급면적_제곱미터"""", space?.supplyAreaMin, space?.supplyAreaMax, "supplyArea")
        addText(where, params, """v."층"""", space?.floorText, "floorText")
        if (space?.groundFloor == true) {
            where += """v."층" like '%1%' and coalesce(v."층", '') not like '%지하%'"""
        }
        space?.basement?.let {
            where += if (it) """coalesce(v."층", '') like '%지하%'""" else """coalesce(v."층", '') not like '%지하%'"""
        }

        addText(where, params, """v."건물명"""", building?.buildingName, "buildingName")
        addText(where, params, """v."건물유형"""", building?.buildingType, "buildingType")
        addText(where, params, """v."건물용도"""", building?.buildingUse, "buildingUse")
        addText(where, params, """v."건물등급"""", building?.buildingGrade, "buildingGrade")
        addText(where, params, """v."방향"""", building?.direction, "direction")
        addComparableRange(where, params, """v."사용승인일"""", building?.approvalDateFrom, building?.approvalDateTo, "approvalDate")

        addBoolean(where, params, """v."엘리베이터여부"""", amenities?.elevatorAvailable, "elevatorAvailable")
        addBoolean(where, params, """v."주차여부"""", amenities?.parkingAvailable, "parkingAvailable")
        addMin(where, params, """v."주차면수"""", amenities?.parkingCountMin, "parkingCount")
        addBoolean(where, params, """v."테라스"""", amenities?.terrace, "terrace")
        addBoolean(where, params, """v."루프탑"""", amenities?.rooftop, "rooftop")
        addBoolean(where, params, """v."인테리어"""", amenities?.interior, "interior")
        addBoolean(where, params, """v."창고"""", amenities?.storage, "storage")
        addBoolean(where, params, """v."에어컨"""", amenities?.airConditioner, "airConditioner")
        addBoolean(where, params, """v."난방기"""", amenities?.heater, "heater")
        addBoolean(where, params, """v."심야영업가능"""", amenities?.lateNightOperationAvailable, "lateNightOperationAvailable")
        addText(where, params, """v."화장실유형"""", amenities?.restroomType, "restroomType")
        addMin(where, params, """v."화장실수"""", amenities?.restroomCountMin, "restroomCount")

        addRange(where, params, """cf."시설총규모"""", commercial?.facilityTotalSizeMin, commercial?.facilityTotalSizeMax, "facilityTotalSize")
        addRange(where, params, """coalesce(v."전용면적_제곱미터", cf."소재지면적")""", commercial?.locationAreaMin, commercial?.locationAreaMax, "locationArea")
        commercial?.multiUseFacility?.let {
            where += """cf."다중이용업소여부" = :multiUseFacility"""
            params.addValue("multiUseFacility", if (it) 1 else 0)
        }
        addMin(where, params, """cf."최종_유동인구_밀도_명_per_km2_2022_분기평균"""", commercial?.floatingPopulationQuarterlyMin, "floatingPopulationQuarterly")
        addMin(where, params, """cf."최종_상주인구_밀도_명_per_km2_2022_분기평균"""", commercial?.residentPopulationQuarterlyMin, "residentPopulationQuarterly")
        addMin(where, params, """cf."최종_직장인구_밀도_명_per_km2_2022_분기평균"""", commercial?.workerPopulationQuarterlyMin, "workerPopulationQuarterly")
        addMin(where, params, """cf."저녁_비율"""", commercial?.eveningPopulationRatioMin, "eveningPopulationRatio")
        addMin(where, params, """cf."심야_비율"""", commercial?.lateNightPopulationRatioMin, "lateNightPopulationRatio")
        addMin(where, params, """cf."아침_비율"""", commercial?.morningPopulationRatioMin, "morningPopulationRatio")
        addMin(where, params, """cf."주말_비율"""", commercial?.weekendPopulationRatioMin, "weekendPopulationRatio")
        addMin(where, params, """cf."연령_2030_비율"""", commercial?.age2030PopulationRatioMin, "age2030PopulationRatio")
        addMin(where, params, """cf."연령_40plus_비율"""", commercial?.age40PlusPopulationRatioMin, "age40PlusPopulationRatio")
        addMin(where, params, """cf."여성_비율"""", commercial?.femalePopulationRatioMin, "femalePopulationRatio")
        addRange(where, params, """cf."식당수_500m"""", commercial?.restaurantCount500mMin, commercial?.restaurantCount500mMax, "restaurantCount500m")
        addRange(where, params, """cf."카페수_500m"""", commercial?.cafeCount500mMin, commercial?.cafeCount500mMax, "cafeCount500m")
        addMax(where, params, """cf."동네_폐업률"""", commercial?.closureRateMax, "closureRate")
        addMin(where, params, """cf."동네_개업율"""", commercial?.openingRateMin, "openingRate")
        addMin(where, params, """cf."가게당_평균매출"""", commercial?.averageSalesPerStoreMin, "averageSalesPerStore")
        addMin(where, params, """cf."동네_저녁매출_비율"""", commercial?.eveningSalesRatioMin, "eveningSalesRatio")
        addMin(where, params, """cf."동네_심야매출_비율"""", commercial?.lateNightSalesRatioMin, "lateNightSalesRatio")
        addMin(where, params, """cf."동네_주말매출_비율"""", commercial?.weekendSalesRatioMin, "weekendSalesRatio")
        addMin(where, params, """cf."동네_2030매출_비율"""", commercial?.age2030SalesRatioMin, "age2030SalesRatio")
        addMin(where, params, """cf."동네_여성매출_비율"""", commercial?.femaleSalesRatioMin, "femaleSalesRatio")
        addMax(where, params, """cf."공시지가"""", commercial?.officialLandPriceMax, "officialLandPrice")
        addMin(where, params, """cf."지출_총금액"""", commercial?.totalSpendingMin, "totalSpending")
        addMin(where, params, """cf."음식_지출_총금액"""", commercial?.foodSpendingMin, "foodSpending")
        addMin(where, params, """cf."점포당_지출"""", commercial?.spendingPerStoreMin, "spendingPerStore")
        addMin(where, params, """cf."상권_교체활발형"""", commercial?.commercialTurnoverTypeMin, "commercialTurnoverType")
        addMin(where, params, """cf."상권_성장형"""", commercial?.commercialGrowthTypeMin, "commercialGrowthType")

        addRange(
            where,
            params,
            """sp."동종_식당수_500m"""",
            spatial?.sameCategoryRestaurantCount500mMin,
            spatial?.sameCategoryRestaurantCount500mMax,
            "sameCategoryRestaurantCount500m"
        )
        addMin(where, params, """sp."업종성장률_500m"""", spatial?.industryGrowthRate500mMin, "industryGrowthRate500m")

        if (where.isEmpty()) return null

        val sql = """
            select distinct v.property_id
            from vacancies v
            left join vacancy_common_features cf on cf.property_id = v.property_id
            left join vacancy_category_scores cs on cs.property_id = v.property_id
            left join vacancy_category_spatial sp on sp.property_id = v.property_id and sp.category_id = cs.category_id
            where ${where.joinToString(separator = "\n              and ")}
        """.trimIndent()

        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getString("property_id") }.toSet()
    }

    private fun categoryIdFromLabel(label: String): String? {
        return VacancyPromptSchema.categories.entries.firstOrNull { (_, categoryLabel) ->
            categoryLabel == label
        }?.key
    }

    private fun addText(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        field: String,
        rawValue: String?,
        name: String
    ) {
        val value = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return
        where += "lower(coalesce($field, '')) like :$name escape '\\'"
        params.addValue(name, like(value))
    }

    private fun addCombinedText(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        fields: List<String>,
        rawValue: String?,
        name: String
    ) {
        val value = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val expression = fields.joinToString(separator = ", ") { "coalesce($it, '')" }
        where += "lower(concat_ws(' ', $expression)) like :$name escape '\\'"
        params.addValue(name, like(value))
    }

    private fun addRadiusBox(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        latitude: Double?,
        longitude: Double?,
        radiusM: Int?
    ) {
        if (latitude == null || longitude == null || radiusM == null) return
        val radius = radiusM.coerceIn(1, 5000)
        val latDelta = radius / 111_320.0
        val lngScale = cos(Math.toRadians(latitude)).coerceAtLeast(0.1)
        val lngDelta = radius / (111_320.0 * lngScale)
        where += """v."위도" between :minLatitude and :maxLatitude"""
        where += """v."경도" between :minLongitude and :maxLongitude"""
        params.addValue("minLatitude", latitude - latDelta)
        params.addValue("maxLatitude", latitude + latDelta)
        params.addValue("minLongitude", longitude - lngDelta)
        params.addValue("maxLongitude", longitude + lngDelta)
    }

    private fun addBoolean(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        field: String,
        value: Boolean?,
        name: String
    ) {
        value ?: return
        where += "$field = :$name"
        params.addValue(name, value)
    }

    private fun addComparableRange(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        field: String,
        min: String?,
        max: String?,
        name: String
    ) {
        min?.trim()?.takeIf { it.isNotEmpty() }?.let {
            where += "$field >= :${name}From"
            params.addValue("${name}From", it)
        }
        max?.trim()?.takeIf { it.isNotEmpty() }?.let {
            where += "$field <= :${name}To"
            params.addValue("${name}To", it)
        }
    }

    private fun addRange(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        field: String,
        min: Number?,
        max: Number?,
        name: String
    ) {
        addMin(where, params, field, min, name)
        addMax(where, params, field, max, name)
    }

    private fun addMin(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        field: String,
        value: Number?,
        name: String
    ) {
        value ?: return
        where += "$field >= :${name}Min"
        params.addValue("${name}Min", value)
    }

    private fun addMax(
        where: MutableList<String>,
        params: MapSqlParameterSource,
        field: String,
        value: Number?,
        name: String
    ) {
        value ?: return
        where += "$field <= :${name}Max"
        params.addValue("${name}Max", value)
    }

    private fun like(value: String): String {
        val escaped = value
            .lowercase(Locale.KOREA)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }
}
