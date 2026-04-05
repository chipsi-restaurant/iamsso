package org.iamsso.services.authservice.repository

import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OAuthClientRepository : JpaRepository<OAuthClientEntity, String> {
    fun existsByClientName(name: String): Boolean
}