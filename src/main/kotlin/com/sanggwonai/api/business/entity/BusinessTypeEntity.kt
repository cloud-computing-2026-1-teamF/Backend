package com.sanggwonai.api.business.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "categories")
class BusinessTypeEntity(
    @Id
    @Column(name = "category_id", nullable = false, length = 40)
    val businessKey: String,

    @Column(name = "카테고리명", nullable = false, length = 100)
    val label: String
)
