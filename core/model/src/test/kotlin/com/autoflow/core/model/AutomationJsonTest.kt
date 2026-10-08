package com.autoflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationJsonTest {
    private val office = Automation(
        id = "office",
        name = "Office Mode",
        triggers = listOf(TriggerSpec.WifiConnected("Office-WiFi"), TriggerSpec.ChargerConnected),
        condition = ConditionNode.And(
            listOf(
                ConditionNode.TimeRange(TimeOfDay(8, 0), TimeOfDay(18, 0)),
                ConditionNode.DaysOfWeek(Weekday.WORKDAYS),
                ConditionNode.Not(ConditionNode.BatteryLevel(Comparison.LESS_THAN, 30)),
            ),
        ),
        actions = listOf(
            ActionSpec.SetVolume(AudioStream.MEDIA, 60),
            ActionSpec.LaunchApp("com.microsoft.teams", "Teams"),
            ActionSpec.HttpRequest(HttpMethod.POST, "https://example.com", auth = HttpAuth.Bearer("\$token")),
            ActionSpec.IfElse(ConditionNode.Charging(), listOf(ActionSpec.Delay(1000))),
        ),
    )

    @Test
    fun `round trip bundle keeps everything`() {
        val bundle = AutomationBundle(
            automations = listOf(office),
            variables = listOf(Variable("officeWifi", "Company-WiFi"), Variable("token", "secret!", secret = true)),
        )
        val text = AutomationJson.encodeBundle(bundle)
        assertTrue(text.contains("\"type\": \"WIFI_CONNECTED\""))
        assertTrue(text.contains("\"start\": \"08:00\""))
        assertTrue("secrets are never exported", !text.contains("secret!"))

        val decoded = AutomationJson.decodeBundle(text)
        assertEquals(listOf(office), decoded.automations)
        assertEquals(listOf("officeWifi"), decoded.variables.map { it.name })
    }

    @Test
    fun `single automation object is accepted`() {
        val text = """
            {
              "id": "x", "name": "Office Mode", "enabled": true,
              "triggers": [ { "type": "WIFI_CONNECTED", "ssid": "Office-WiFi" } ],
              "condition": { "type": "TIME_RANGE", "start": "08:00", "end": "18:00" },
              "actions": [ { "type": "SET_VOLUME", "percent": 60 },
                           { "type": "LAUNCH_APP", "packageName": "com.microsoft.teams", "unknownField": 1 } ]
            }
        """.trimIndent()
        val automation = AutomationJson.decodeBundle(text).automations.single()
        assertEquals(TriggerSpec.WifiConnected("Office-WiFi"), automation.triggers.single())
        assertEquals(ActionSpec.SetVolume(AudioStream.MEDIA, 60), automation.actions.first())
    }

    @Test
    fun `location triggers round trip and are validated`() {
        val place = GeoPlace("Office", 40.7128, -74.006, 300)
        val automation = Automation(id = "o", name = "Office", triggers = listOf(TriggerSpec.LocationEnter(place), TriggerSpec.LocationExit(place)))
        val decoded = AutomationJson.decodeBundle(AutomationJson.encodeBundle(AutomationBundle(automations = listOf(automation))))
        assertEquals(automation, decoded.automations.single())
        assertEquals(TriggerFamily.LOCATION, automation.triggers.first().family)
        assertTrue(!TriggerFamily.LOCATION.needsMonitoringService)
        assertTrue(Capability.BACKGROUND_LOCATION in automation.requiredCapabilities)
    }

    @Test(expected = InvalidAutomationFileException::class)
    fun `out of range coordinates are rejected`() {
        AutomationJson.decodeBundle(
            """{"id":"x","name":"n","triggers":[{"type":"LOCATION_ENTER","place":{"name":"p","latitude":95,"longitude":0}}]}""",
        )
    }

    @Test(expected = InvalidAutomationFileException::class)
    fun `unknown action type is rejected`() {
        AutomationJson.decodeBundle("""{"automations":[{"id":"x","name":"n","actions":[{"type":"TELEPORT"}]}]}""")
    }

    @Test(expected = InvalidAutomationFileException::class)
    fun `invalid values are rejected`() {
        AutomationJson.decodeBundle("""{"id":"x","name":"n","actions":[{"type":"SET_VOLUME","percent":150}]}""")
    }

    @Test(expected = InvalidAutomationFileException::class)
    fun `not json is rejected`() {
        AutomationJson.decodeBundle("hello")
    }

    @Test
    fun `capabilities are collected recursively`() {
        val automation = office.copy(
            actions = office.actions + ActionSpec.IfElse(ConditionNode.Charging(), listOf(ActionSpec.SetBrightness(10))),
        )
        val caps = automation.requiredCapabilities
        assertTrue(Capability.LOCATION in caps)
        assertTrue(Capability.WRITE_SETTINGS in caps)
        assertTrue(Capability.BACKGROUND_ACTIVITY_START in caps)
    }
}
