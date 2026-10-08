package com.autoflow.core.engine.variable

/**
 * Replaces placeholders in user text in a single pass (substituted values are never re-parsed):
 *  - `%name%` or `%name` → built-in variables (battery, time, trigger_ssid…)
 *  - `$name` or `$name.path.0` → user / local variables, with optional JSON path
 * Unknown placeholders are left untouched so problems are visible in the output.
 */
object TemplateResolver {
    private val PLACEHOLDER = Regex("%([A-Za-z_][A-Za-z0-9_]*)%?|\\$([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)")
    private val USER = Regex("\\$([A-Za-z_][A-Za-z0-9_]*)")

    fun resolve(
        text: String,
        builtIn: (String) -> String?,
        user: (String) -> String?,
    ): String {
        if (text.isEmpty()) return text
        return PLACEHOLDER.replace(text) { match ->
            val builtInName = match.groups[1]?.value
            val resolved = if (builtInName != null) builtIn(builtInName.lowercase()) else user(match.groupValues[2])
            resolved ?: match.value
        }
    }

    /** Names of user variables (`$name`) referenced in [text], without JSON paths. */
    fun referencedUserVariables(text: String): Set<String> =
        USER.findAll(text).map { it.groupValues[1] }.toSet()
}
