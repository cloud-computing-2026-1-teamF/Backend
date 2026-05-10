package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal

@Entity
@Table(name = "vacancy_common_features")
class VacancyCommonFeatureEntity(
    @Id
    @Column(name = "property_id", nullable = false, length = 40)
    val propertyId: String,

    @Column(name = "시설총규모", precision = 20, scale = 6)
    val facilityTotalSize: BigDecimal?,

    @Column(name = "소재지면적", precision = 20, scale = 6)
    val locationArea: BigDecimal?,

    @Column(name = "다중이용업소여부")
    val multiUseFacilityValue: Short?,

    @Column(name = "최종_유동인구_밀도_명_per_km2_2022_연간합계", precision = 20, scale = 6)
    val floatingPopulationAnnualDensity: BigDecimal?,

    @Column(name = "최종_유동인구_밀도_명_per_km2_2022_분기평균", precision = 20, scale = 6)
    val floatingPopulationQuarterlyDensity: BigDecimal?,

    @Column(name = "최종_상주인구_밀도_명_per_km2_2022_연간합계", precision = 20, scale = 6)
    val residentPopulationAnnualDensity: BigDecimal?,

    @Column(name = "최종_상주인구_밀도_명_per_km2_2022_분기평균", precision = 20, scale = 6)
    val residentPopulationQuarterlyDensity: BigDecimal?,

    @Column(name = "최종_직장인구_밀도_명_per_km2_2022_연간합계", precision = 20, scale = 6)
    val workerPopulationAnnualDensity: BigDecimal?,

    @Column(name = "최종_직장인구_밀도_명_per_km2_2022_분기평균", precision = 20, scale = 6)
    val workerPopulationQuarterlyDensity: BigDecimal?,

    @Column(name = "저녁_비율", precision = 20, scale = 6)
    val eveningPopulationRatio: BigDecimal?,

    @Column(name = "심야_비율", precision = 20, scale = 6)
    val lateNightPopulationRatio: BigDecimal?,

    @Column(name = "아침_비율", precision = 20, scale = 6)
    val morningPopulationRatio: BigDecimal?,

    @Column(name = "주말_비율", precision = 20, scale = 6)
    val weekendPopulationRatio: BigDecimal?,

    @Column(name = "연령_2030_비율", precision = 20, scale = 6)
    val age2030PopulationRatio: BigDecimal?,

    @Column(name = "연령_40plus_비율", precision = 20, scale = 6)
    val age40PlusPopulationRatio: BigDecimal?,

    @Column(name = "여성_비율", precision = 20, scale = 6)
    val femalePopulationRatio: BigDecimal?,

    @Column(name = "상주_유동_비", precision = 20, scale = 6)
    val residentToFloatingRatio: BigDecimal?,

    @Column(name = "직장_유동_비", precision = 20, scale = 6)
    val workerToFloatingRatio: BigDecimal?,

    @Column(name = "식당수_250m")
    val restaurantCount250m: Int?,

    @Column(name = "식당수_500m")
    val restaurantCount500m: Int?,

    @Column(name = "식당수_1000m")
    val restaurantCount1000m: Int?,

    @Column(name = "카페수_250m")
    val cafeCount250m: Int?,

    @Column(name = "카페수_500m")
    val cafeCount500m: Int?,

    @Column(name = "카페수_1000m")
    val cafeCount1000m: Int?,

    @Column(name = "동네_폐업률", precision = 20, scale = 6)
    val closureRate: BigDecimal?,

    @Column(name = "동네_개업율", precision = 20, scale = 6)
    val openingRate: BigDecimal?,

    @Column(name = "가게당_평균매출", precision = 20, scale = 6)
    val averageSalesPerStore: BigDecimal?,

    @Column(name = "동네_저녁매출_비율", precision = 20, scale = 6)
    val eveningSalesRatio: BigDecimal?,

    @Column(name = "동네_심야매출_비율", precision = 20, scale = 6)
    val lateNightSalesRatio: BigDecimal?,

    @Column(name = "동네_주말매출_비율", precision = 20, scale = 6)
    val weekendSalesRatio: BigDecimal?,

    @Column(name = "동네_2030매출_비율", precision = 20, scale = 6)
    val age2030SalesRatio: BigDecimal?,

    @Column(name = "동네_여성매출_비율", precision = 20, scale = 6)
    val femaleSalesRatio: BigDecimal?,

    @Column(name = "공시지가", precision = 20, scale = 6)
    val officialLandPrice: BigDecimal?,

    @Column(name = "지출_총금액", precision = 20, scale = 6)
    val totalSpending: BigDecimal?,

    @Column(name = "음식_지출_총금액", precision = 20, scale = 6)
    val foodSpending: BigDecimal?,

    @Column(name = "점포당_지출", precision = 20, scale = 6)
    val spendingPerStore: BigDecimal?,

    @Column(name = "상권_교체활발형", precision = 20, scale = 6)
    val commercialTurnoverType: BigDecimal?,

    @Column(name = "상권_성장형", precision = 20, scale = 6)
    val commercialGrowthType: BigDecimal?,

    @Column(name = "행정동_행정동_코드", length = 40)
    val areaCode: String?,

    @Column(name = "행정동_행정동_명", length = 200)
    val areaName: String?
) {
    @get:Transient
    val multiUseFacility: Boolean?
        get() = multiUseFacilityValue?.let { it.toInt() != 0 }
}
