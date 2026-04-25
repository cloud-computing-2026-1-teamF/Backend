package com.sanggwonai.api.business.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "business_types")
class BusinessTypeEntity(
    @Id
    @Column(name = "business_key", nullable = false, length = 40)
    val businessKey: String,

    @Column(nullable = false, length = 100)
    val label: String,

    @Column(nullable = false, length = 8)
    val emoji: String,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int
)
