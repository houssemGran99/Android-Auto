package com.autoflow.core.engine.variable

import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.Variable
import com.autoflow.core.model.VariableOp
import java.net.URLEncoder
import java.util.regex.PatternSyntaxException

/** Implements the "Variable operation" action: string and number helpers on one variable. */
object VariableOperations {

    suspend fun apply(action: ActionSpec.VariableOperation, context: AutomationContext): ActionResult {
        val target = action.target?.takeIf { it.isNotBlank() } ?: action.name
        if (!Variable.isValidName(action.name) || !Variable.isValidName(target)) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, "Invalid variable name")
        }
        val current = context.variable(action.name).orEmpty()
        val arg1 = context.resolve(action.argument1)
        val arg2 = context.resolve(action.argument2)
        val result = try {
            compute(action.operation, current, arg1, arg2)
        } catch (e: IllegalArgumentException) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, e.message ?: "Invalid arguments")
        }
        context.setVariable(target, result, action.scope)
        return ActionResult.Success("$target = ${result.take(80)}")
    }

    /** Pure computation, separated for tests. Throws [IllegalArgumentException] for invalid arguments. */
    fun compute(op: VariableOp, value: String, arg1: String, arg2: String): String = when (op) {
        VariableOp.INCREMENT -> number(value) + step(arg1)
        VariableOp.DECREMENT -> number(value) - step(arg1)
        VariableOp.APPEND -> value + arg1
        VariableOp.REPLACE -> {
            require(arg1.isNotEmpty()) { "Text to replace is empty" }
            value.replace(arg1, arg2)
        }
        VariableOp.UPPERCASE -> value.uppercase()
        VariableOp.LOWERCASE -> value.lowercase()
        VariableOp.TRIM -> value.trim()
        VariableOp.SUBSTRING -> {
            val start = index(arg1, "start").coerceIn(0, value.length)
            val end = if (arg2.isBlank()) value.length else index(arg2, "end").coerceIn(start, value.length)
            value.substring(start, end)
        }
        VariableOp.SPLIT -> {
            require(arg1.isNotEmpty()) { "Separator is empty" }
            value.split(arg1).getOrElse(if (arg2.isBlank()) 0 else index(arg2, "index")) { "" }
        }
        VariableOp.REGEX_EXTRACT -> {
            val regex = try {
                Regex(arg1)
            } catch (e: PatternSyntaxException) {
                throw IllegalArgumentException("Invalid regex: ${e.description}")
            }
            val match = regex.find(value)
            val group = if (arg2.isBlank()) (if (match != null && match.groupValues.size > 1) 1 else 0) else index(arg2, "group")
            match?.groupValues?.getOrNull(group).orEmpty()
        }
        VariableOp.LENGTH -> value.length.toString()
        VariableOp.URL_ENCODE -> URLEncoder.encode(value, Charsets.UTF_8.name())
    }.let { if (it is Double) ExpressionEvaluator.format(it) else it.toString() }

    private fun number(value: String): Double = value.trim().ifEmpty { "0" }.toDoubleOrNull()
        ?: throw IllegalArgumentException("'$value' is not a number")

    private fun step(arg: String): Double = arg.trim().ifEmpty { "1" }.toDoubleOrNull()
        ?: throw IllegalArgumentException("'$arg' is not a number")

    private fun index(arg: String, name: String): Int = arg.trim().toIntOrNull()?.takeIf { it >= 0 }
        ?: throw IllegalArgumentException("$name must be a whole number ≥ 0")
}
