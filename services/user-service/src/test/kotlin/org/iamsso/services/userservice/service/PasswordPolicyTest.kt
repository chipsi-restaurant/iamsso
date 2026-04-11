package org.iamsso.services.userservice.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordPolicyTest {

    @Test
    fun `rejects password shorter than 8 chars`() {
        assertThat(PasswordPolicy.isValid("Ab1")).isFalse()
    }

    @Test
    fun `rejects password without letters`() {
        assertThat(PasswordPolicy.isValid("12345678")).isFalse()
    }

    @Test
    fun `rejects password without digits`() {
        assertThat(PasswordPolicy.isValid("abcdefgh")).isFalse()
    }

    @Test
    fun `accepts password with letter digit and minimum length`() {
        assertThat(PasswordPolicy.isValid("abcdefg1")).isTrue()
    }

    @Test
    fun `accepts longer complex password`() {
        assertThat(PasswordPolicy.isValid("NewSecureP@ss456")).isTrue()
    }

    @Test
    fun `errorMessage is non-empty`() {
        assertThat(PasswordPolicy.errorMessage).isNotEmpty()
    }
}
