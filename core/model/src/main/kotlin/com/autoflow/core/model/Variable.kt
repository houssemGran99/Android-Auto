package com.autoflow.core.model

import kotlinx.serialization.Serializable

/** A user-defined variable referenced as `$name`. Secret values are encrypted at rest and never exported. */
@Serializable
data class Variable(
    val name: String,
    val value: String,
    val secret: Boolean = false,
    val updatedAt: Long = 0,
) {
    companion object {
        private val NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")

        fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)
    }
}
