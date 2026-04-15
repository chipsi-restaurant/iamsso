package org.iamsso.apps.vacation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "vacation_requests", schema = "vacation_portal")
class VacationRequestEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val type: VacationRequestType,
    @Column(name = "start_date", nullable = false) var startDate: LocalDate,
    @Column(name = "end_date", nullable = false) var endDate: LocalDate,
    @Column(nullable = false) var reason: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: VacationRequestStatus = VacationRequestStatus.PENDING,
    @Column(name = "reviewer_id") var reviewerId: UUID? = null,
    @Column(name = "review_comment") var reviewComment: String? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun onUpdate() { updatedAt = Instant.now() }
}

enum class VacationRequestType { VACATION, SICK_LEAVE, BUSINESS_TRIP, OTHER }

enum class VacationRequestStatus {
    PENDING, APPROVED, REJECTED, CANCELLED;

    val isTerminal: Boolean get() = this != PENDING
}
