package com.autoflow.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Portable file format for export / import / backup. */
@Serializable
data class AutomationBundle(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    @SerialName("exported_at") val exportedAt: Long = 0,
    val automations: List<Automation> = emptyList(),
    /** Only non-secret variables are ever exported. */
    val variables: List<Variable> = emptyList(),
) {
    companion object {
        const val FORMAT = "autoflow.automations"
        const val CURRENT_VERSION = 1
    }
}

class InvalidAutomationFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Single JSON configuration shared by persistence and import/export. */
object AutomationJson {
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val prettyJson: Json = Json(json) { prettyPrint = true }

    fun encodeTriggers(triggers: List<TriggerSpec>): String = json.encodeToString(triggers)

    fun decodeTriggers(text: String): List<TriggerSpec> = json.decodeFromString(text)

    fun encodeCondition(condition: ConditionNode?): String? = condition?.let { json.encodeToString(it) }

    fun decodeCondition(text: String?): ConditionNode? = text?.let { json.decodeFromString<ConditionNode>(it) }

    fun encodeActions(actions: List<ActionSpec>): String = json.encodeToString(actions)

    fun decodeActions(text: String): List<ActionSpec> = json.decodeFromString(text)

    fun encodeBundle(bundle: AutomationBundle): String =
        prettyJson.encodeToString(bundle.copy(variables = bundle.variables.filterNot { it.secret }))

    /**
     * Accepts either a bundle or a single automation object.
     * @throws InvalidAutomationFileException if the content is not a valid AutoFlow file.
     */
    fun decodeBundle(text: String): AutomationBundle {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw InvalidAutomationFileException("File is not valid JSON", e)
        }
        return try {
            val obj = element as? kotlinx.serialization.json.JsonObject
                ?: throw InvalidAutomationFileException("Expected a JSON object")
            if ("automations" in obj) {
                val bundle = json.decodeFromJsonElement(AutomationBundle.serializer(), obj)
                if (bundle.version > AutomationBundle.CURRENT_VERSION) {
                    throw InvalidAutomationFileException("File version ${bundle.version} is newer than supported")
                }
                bundle
            } else {
                AutomationBundle(automations = listOf(json.decodeFromJsonElement(Automation.serializer(), obj)))
            }
        } catch (e: SerializationException) {
            throw InvalidAutomationFileException(e.message ?: "Unsupported automation content", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidAutomationFileException(e.message ?: "Invalid automation values", e)
        }
    }
}
