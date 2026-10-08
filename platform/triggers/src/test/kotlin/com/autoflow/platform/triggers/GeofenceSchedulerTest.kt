package com.autoflow.platform.triggers

import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.GeoPlace
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceSchedulerTest {
    private val home = GeoPlace("Home", 48.8566, 2.3522, 200)

    @Test
    fun registrationsCoverEnabledLocationTriggersOnly() {
        val automations = listOf(
            Automation(
                id = "a",
                name = "A",
                triggers = listOf(TriggerSpec.ChargerConnected, TriggerSpec.LocationEnter(home), TriggerSpec.LocationExit(home)),
                actions = listOf(ActionSpec.Vibrate()),
            ),
            Automation(id = "b", name = "B", enabled = false, triggers = listOf(TriggerSpec.LocationEnter(home))),
        )
        val registrations = GeofenceScheduler.registrations(automations)
        assertEquals(listOf("a/1", "a/2"), registrations.map { it.id })
        assertEquals(listOf(true, false), registrations.map { it.enter })
    }

    @Test
    fun requestIdsMapBackToEvents() {
        assertEquals(
            TriggerEvent.LocationTransition("a", 1, entered = true, placeName = "Home"),
            GeofenceScheduler.toEvent("a/1", entered = true) { "Home" },
        )
        // Automation ids are UUIDs, but the parser must cope with a "/" inside them.
        assertEquals("x/y", GeofenceScheduler.toEvent("x/y/3", entered = false) { null }?.automationId)
        assertNull(GeofenceScheduler.toEvent("garbage", entered = true) { null })
    }
}
