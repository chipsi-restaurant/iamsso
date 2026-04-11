package org.iamsso.services.userservice.service

/**
 * Простая политика пароля для self-service сброса.
 * Используется только в [PasswordResetService.confirmReset].
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val errorMessage =
        "Пароль должен содержать минимум 8 символов, хотя бы одну букву и одну цифру"

    fun isValid(password: String): Boolean {
        if (password.length < MIN_LENGTH) return false
        if (!password.any { it.isLetter() }) return false
        if (!password.any { it.isDigit() }) return false
        return true
    }
}
