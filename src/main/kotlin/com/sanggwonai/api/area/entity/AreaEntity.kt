package com.sanggwonai.api.area.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "areas")
class AreaEntity(
    @Id
    @Column(nullable = false, length = 20)
    val id: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, length = 150)
    val region: String,

    @Column(name = "full_name", nullable = false, length = 255)
    val fullName: String,

    @Column(name = "center_lat", nullable = false, precision = 9, scale = 6)
    val centerLat: BigDecimal,

    @Column(name = "center_lng", nullable = false, precision = 9, scale = 6)
    val centerLng: BigDecimal
)
