package com.autoflow.core.engine

import com.autoflow.core.engine.variable.ExpressionEvaluator
import com.autoflow.core.engine.variable.ExpressionException
import com.autoflow.core.engine.variable.JsonPath
import com.autoflow.core.engine.variable.TemplateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariablesTest {
    private val builtIns = mapOf("battery" to "42", "time" to "08:15")
    private val users = mapOf("userName" to "John", "price" to "\$5", "data.city" to "Paris")

    private fun resolve(text: String) = TemplateResolver.resolve(text, builtIns::get, users::get)

    @Test
    fun `resolves built-in and user placeholders`() {
        assertEquals("Battery level: 42%", resolve("Battery level: %battery%%"))
        assertEquals("Battery 42 at 08:15", resolve("Battery %battery at %TIME%"))
        assertEquals("Hello John.", resolve("Hello \$userName."))
        assertEquals("City Paris", resolve("City \$data.city"))
    }

    @Test
    fun `unknown placeholders and literal percent stay untouched`() {
        assertEquals("%unknown% and \$nobody and 50% and \$5", resolve("%unknown% and \$nobody and 50% and \$5"))
    }

    @Test
    fun `substituted values are not parsed again`() {
        assertEquals("cost \$5", resolve("cost \$price"))
    }

    @Test
    fun `json path extraction`() {
        val json = """{"type":"alarm","data":{"items":[{"name":"a"},{"name":"b"}],"temp":21.5}}"""
        assertEquals("alarm", JsonPath.extract(json, listOf("type")))
        assertEquals("b", JsonPath.extract(json, listOf("data", "items", "1", "name")))
        assertEquals("21.5", JsonPath.extract(json, listOf("data", "temp")))
        assertNull(JsonPath.extract(json, listOf("data", "missing")))
        assertNull(JsonPath.extract("not json", listOf("a")))
    }

    @Test
    fun `arithmetic expressions`() {
        assertEquals("11", ExpressionEvaluator.format(ExpressionEvaluator.evaluate("10 + 1")))
        assertEquals("30", ExpressionEvaluator.format(ExpressionEvaluator.evaluate("(10 + 5) * 2")))
        assertEquals("2.5", ExpressionEvaluator.format(ExpressionEvaluator.evaluate("5 / 2")))
        assertEquals("-3", ExpressionEvaluator.format(ExpressionEvaluator.evaluate("-(1 + 2)")))
        assertEquals("1", ExpressionEvaluator.format(ExpressionEvaluator.evaluate("7 % 3")))
    }

    @Test(expected = ExpressionException::class)
    fun `division by zero fails`() {
        ExpressionEvaluator.evaluate("1 / 0")
    }

    @Test(expected = ExpressionException::class)
    fun `garbage fails`() {
        ExpressionEvaluator.evaluate("2 + abc")
    }
}
