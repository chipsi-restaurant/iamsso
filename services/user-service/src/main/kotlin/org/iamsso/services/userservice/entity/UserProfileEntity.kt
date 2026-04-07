package org.iamsso.services.userservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_profiles", schema = "iamsso_users")
class UserProfileEntity(
    @Id @Column(name = "user_id") val userId: UUID = UUID.randomUUID(),
    @OneToOne(fetch = FetchType.LAZY) @MapsId @JoinColumn(name = "user_id") var user: UserEntity? = null,
    @Column(name = "first_name") var firstName: String? = null,
    @Column(name = "last_name") var lastName: String? = null,
    @Column(name = "avatar_url") var avatarUrl: String? = null,
    var timezone: String = "UTC",
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate fun onUpdate() { updatedAt = Instant.now() }
}