package com.sanggwonai.api.analysis.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "analyses")
class AnalysisEntity(
    @Id
    @Column(nullable = false, length = 40)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 40)
    val userId: String,

    @Column(name = "business_type_key", nullable = false, length = 40)
    val businessTypeKey: String,

    @Column(name = "vacancy_id", nullable = false, length = 40)
    val vacancyId: String,

    @Column(name = "budget_deposit_max")
    var budgetDepositMax: Long?,

    @Column(name = "budget_rent_max")
    var budgetRentMax: Long?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AnalysisStatus,

    @Column(nullable = false)
    var progress: Int,

    @Column(name = "step_index")
    var stepIndex: Int?,

    @Column(name = "step_total")
    var stepTotal: Int?,

    @Column(name = "step_label", length = 200)
    var stepLabel: String?,

    @Column(name = "error_code", length = 50)
    var errorCode: String?,

    @Column(name = "error_message", length = 500)
    var errorMessage: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "completed_at")
    var completedAt: Instant?,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant
)
