package com.autoflow.platform.actions

import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.Variable
import com.autoflow.platform.actions.handler.DismissNotificationsActionHandler
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock

class DismissNotificationsTest {
    private class FakeController(var connected: Boolean = true) : NotificationController {
        val shown = mutableListOf(
            ActiveNotification("1", "com.whatsapp", "Mom", "Dinner?", clearable = true),
            ActiveNotification("2", "com.whatsapp", "Promo", "Sale today", clearable = true),
            ActiveNotification("3", "com.spotify.music", "Song", "Playing", clearable = false),
            ActiveNotification("4", "com.autoflow.app", "Automations active", "", clearable = true),
        )
        val dismissed = mutableListOf<String>()
        override val isConnected get() = connected
        override fun activeNotifications() = shown
        override fun dismiss(key: String) {
            dismissed += key
        }
    }

    private val context = AutomationContext(
        automation = Automation(id = "a", name = "A"),
        event = TriggerEvent.Manual("test"),
        deviceState = DeviceState(),
        globalVariables = mapOf("word" to "sale"),
        variableRepository = object : VariableRepository {
            override fun observeAll() = emptyFlow<List<Variable>>()
            override suspend fun getAll() = emptyList<Variable>()
            override suspend fun get(name: String): Variable? = null
            override suspend fun set(name: String, value: String, secret: Boolean?) = Unit
            override suspend fun delete(name: String) = Unit
        },
        deviceStateProvider = { DeviceState() },
        clock = Clock.systemUTC(),
    )

    @Test
    fun dismissesOnlyMatchingClearableNotificationsOfOtherApps() = runTest {
        val controller = FakeController()
        val handler = DismissNotificationsActionHandler(controller, ownPackage = "com.autoflow.app")

        handler.execute(ActionSpec.DismissNotifications(textContains = "\$word"), context)
        assertEquals(listOf("2"), controller.dismissed)

        controller.dismissed.clear()
        handler.execute(ActionSpec.DismissNotifications(), context)
        assertEquals(listOf("1", "2"), controller.dismissed)
    }

    @Test
    fun reportsMissingAccess() = runTest {
        val result = DismissNotificationsActionHandler(FakeController(connected = false), "x")
            .execute(ActionSpec.DismissNotifications(), context)
        assertEquals(FailureKind.PERMISSION_DENIED, (result as ActionResult.Failure).kind)
    }
}
