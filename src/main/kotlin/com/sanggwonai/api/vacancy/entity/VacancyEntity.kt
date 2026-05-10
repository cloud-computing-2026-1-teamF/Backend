package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "vacancies")
class VacancyEntity(
    @Id
    @Column(name = "property_id", nullable = false, length = 40)
    val id: String,

    @Column(name = "id", length = 40)
    val listingId: String?,

    @Column(name = "매물번호", length = 40)
    val listingNumber: String?,

    @Column(name = "도로명주소", length = 255)
    val roadAddress: String?,

    @Column(name = "지번주소", length = 255)
    val lotAddress: String?,

    @Column(name = "우편번호", length = 20)
    val postalCode: String?,

    @Column(name = "건물명", length = 200)
    val buildingName: String?,

    @Column(name = "시도", length = 50)
    val province: String?,

    @Column(name = "구", length = 80)
    val district: String?,

    @Column(name = "동", length = 80)
    val dong: String?,

    @Column(name = "상세주소", length = 255)
    val detailAddress: String?,

    @Column(name = "위도", precision = 10, scale = 7)
    val latitude: BigDecimal?,

    @Column(name = "경도", precision = 10, scale = 7)
    val longitude: BigDecimal?,

    @Column(name = "거래유형", length = 40)
    val transactionType: String?,

    @Column(name = "보증금_만원")
    val deposit: Long?,

    @Column(name = "월세_만원")
    val monthlyRent: Long?,

    @Column(name = "권리금_만원")
    val premium: Long?,

    @Column(name = "매매가_만원")
    val salePrice: Long?,

    @Column(name = "관리비_만원")
    val maintenanceFee: Long?,

    @Column(name = "전용면적_제곱미터", precision = 14, scale = 2)
    val dedicatedArea: BigDecimal?,

    @Column(name = "공급면적_제곱미터", precision = 14, scale = 2)
    val supplyArea: BigDecimal?,

    @Column(name = "층", length = 40)
    val floor: String?,

    @Column(name = "건물총층수", length = 40)
    val totalFloors: String?,

    @Column(name = "지하층수", length = 40)
    val basementFloors: String?,

    @Column(name = "건물유형", length = 80)
    val buildingType: String?,

    @Column(name = "건물용도", length = 120)
    val buildingUse: String?,

    @Column(name = "건물등급", length = 40)
    val buildingGrade: String?,

    @Column(name = "사용승인일")
    val approvalDate: String?,

    @Column(name = "방향", length = 40)
    val direction: String?,

    @Column(name = "엘리베이터여부")
    val elevatorAvailable: Boolean?,

    @Column(name = "엘리베이터수")
    val elevatorCount: Int?,

    @Column(name = "난방방식", length = 80)
    val heatingType: String?,

    @Column(name = "화장실유형", length = 80)
    val restroomType: String?,

    @Column(name = "화장실수", precision = 8, scale = 2)
    val restroomCount: BigDecimal?,

    @Column(name = "주차여부")
    val parkingAvailable: Boolean?,

    @Column(name = "주차면수", precision = 8, scale = 2)
    val parkingCount: BigDecimal?,

    @Column(name = "테라스")
    val terrace: Boolean?,

    @Column(name = "루프탑")
    val rooftop: Boolean?,

    @Column(name = "인테리어")
    val interior: Boolean?,

    @Column(name = "창고")
    val storage: Boolean?,

    @Column(name = "에어컨")
    val airConditioner: Boolean?,

    @Column(name = "난방기")
    val heater: Boolean?,

    @Column(name = "심야영업가능")
    val lateNightOperationAvailable: Boolean?,

    @Column(name = "가격협의")
    val priceNegotiable: Boolean?,

    @Column(name = "임대료조정가능")
    val rentAdjustable: Boolean?,

    @Column(name = "무상임대기간")
    val rentFreePeriodAvailable: Boolean?,

    @Column(name = "지하철", length = 255)
    val subway: String?,

    @Column(name = "중개수수료_원")
    val brokerageFee: Long?,

    @Column(name = "수수료율", precision = 12, scale = 6)
    val brokerageRate: BigDecimal?,

    @Column(name = "조회수")
    val viewCount: Int?,

    @Column(name = "찜")
    val favoriteCount: Int?,

    @Column(name = "등록일")
    val registeredAt: String?,

    @Column(name = "수정일")
    val modifiedAt: String?,

    @Column(name = "업종대분류", length = 120)
    val majorBusinessCategory: String?,

    @Column(name = "업종중분류", length = 120)
    val middleBusinessCategory: String?
)
