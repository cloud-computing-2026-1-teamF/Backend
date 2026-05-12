package com.sanggwonai.api.vacancy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "vacancy_accessibility_foottraffic")
class VacancyAccessibilityFoottrafficEntity(
    @Id
    @Column(name = "property_id", nullable = false, length = 40)
    val propertyId: String,

    @Column(name = "bus_stop_info", columnDefinition = "text")
    val busStopInfo: String?,

    @Column(name = "subway_station_info", columnDefinition = "text")
    val subwayStationInfo: String?,

    @Column(name = "parking_info", columnDefinition = "text")
    val parkingInfo: String?,

    @Column(name = "foottraffic_00", precision = 20, scale = 6)
    val foottraffic00: BigDecimal?,

    @Column(name = "foottraffic_01", precision = 20, scale = 6)
    val foottraffic01: BigDecimal?,

    @Column(name = "foottraffic_02", precision = 20, scale = 6)
    val foottraffic02: BigDecimal?,

    @Column(name = "foottraffic_03", precision = 20, scale = 6)
    val foottraffic03: BigDecimal?,

    @Column(name = "foottraffic_04", precision = 20, scale = 6)
    val foottraffic04: BigDecimal?,

    @Column(name = "foottraffic_05", precision = 20, scale = 6)
    val foottraffic05: BigDecimal?,

    @Column(name = "foottraffic_06", precision = 20, scale = 6)
    val foottraffic06: BigDecimal?,

    @Column(name = "foottraffic_07", precision = 20, scale = 6)
    val foottraffic07: BigDecimal?,

    @Column(name = "foottraffic_08", precision = 20, scale = 6)
    val foottraffic08: BigDecimal?,

    @Column(name = "foottraffic_09", precision = 20, scale = 6)
    val foottraffic09: BigDecimal?,

    @Column(name = "foottraffic_10", precision = 20, scale = 6)
    val foottraffic10: BigDecimal?,

    @Column(name = "foottraffic_11", precision = 20, scale = 6)
    val foottraffic11: BigDecimal?,

    @Column(name = "foottraffic_12", precision = 20, scale = 6)
    val foottraffic12: BigDecimal?,

    @Column(name = "foottraffic_13", precision = 20, scale = 6)
    val foottraffic13: BigDecimal?,

    @Column(name = "foottraffic_14", precision = 20, scale = 6)
    val foottraffic14: BigDecimal?,

    @Column(name = "foottraffic_15", precision = 20, scale = 6)
    val foottraffic15: BigDecimal?,

    @Column(name = "foottraffic_16", precision = 20, scale = 6)
    val foottraffic16: BigDecimal?,

    @Column(name = "foottraffic_17", precision = 20, scale = 6)
    val foottraffic17: BigDecimal?,

    @Column(name = "foottraffic_18", precision = 20, scale = 6)
    val foottraffic18: BigDecimal?,

    @Column(name = "foottraffic_19", precision = 20, scale = 6)
    val foottraffic19: BigDecimal?,

    @Column(name = "foottraffic_20", precision = 20, scale = 6)
    val foottraffic20: BigDecimal?,

    @Column(name = "foottraffic_21", precision = 20, scale = 6)
    val foottraffic21: BigDecimal?,

    @Column(name = "foottraffic_22", precision = 20, scale = 6)
    val foottraffic22: BigDecimal?,

    @Column(name = "foottraffic_23", precision = 20, scale = 6)
    val foottraffic23: BigDecimal?
) {
    fun hourlyFoottraffic(): List<BigDecimal> {
        return listOf(
            foottraffic00, foottraffic01, foottraffic02, foottraffic03,
            foottraffic04, foottraffic05, foottraffic06, foottraffic07,
            foottraffic08, foottraffic09, foottraffic10, foottraffic11,
            foottraffic12, foottraffic13, foottraffic14, foottraffic15,
            foottraffic16, foottraffic17, foottraffic18, foottraffic19,
            foottraffic20, foottraffic21, foottraffic22, foottraffic23
        ).map { it ?: BigDecimal.ZERO }
    }
}
