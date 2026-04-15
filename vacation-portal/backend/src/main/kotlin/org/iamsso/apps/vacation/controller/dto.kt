package org.iamsso.apps.vacation.controller

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.iamsso.apps.vacation.entity.VacationRequestEntity
import org.iamsso.apps.vacation.entity.VacationRequestStatus
import org.iamsso.apps.vacation.entity.VacationRequestType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateRequestBody(
    @field:NotNull val type: VacationRequestType?,
    @field:NotNull
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val startDate: LocalDate?,
    @field:NotNull
    @field:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val endDate: LocalDate?,
    @field:NotBlank @field:Size(max = 2000) val reason: String?,
)

data class ReviewCommentBody(
    @field:Size(max = 2000) val comment: String? = null,
)

data class VacationRequestResponse(
    val id: UUID,
    val userId: UUID,
    val type: VacationRequestType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String,
    val status: VacationRequestStatus,
    val reviewerId: UUID?,
    val reviewComment: String?,
    val reviewedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: VacationRequestEntity) = VacationRequestResponse(
            id = e.id, userId = e.userId, type = e.type,
            startDate = e.startDate, endDate = e.endDate, reason = e.reason,
            status = e.status, reviewerId = e.reviewerId,
            reviewComment = e.reviewComment, reviewedAt = e.reviewedAt,
            createdAt = e.createdAt, updatedAt = e.updatedAt,
        )
    }
}

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
