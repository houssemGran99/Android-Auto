package com.autoflow.core.engine.variable

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal JSON path extraction using dot notation: `data.items.0.name`.
 * Returns primitives as plain strings and objects/arrays as JSON text.
 */
object JsonPath {
    fun extract(json: String, path: List<String>): String? {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return extract(root, path)?.asText()
    }

    fun extract(root: JsonElement, path: List<String>): JsonElement? {
        var current: JsonElement = root
        for (segment in path) {
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> segment.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun JsonElement.asText(): String = when (this) {
        is JsonNull -> "null"
        is JsonPrimitive -> content
        else -> toString()
    }
}
