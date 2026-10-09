package com.autoflow.core.engine

import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.engine.action.ActionPipeline
import com.autoflow.core.engine.action.ActionRegistry
import com.autoflow.core.engine.condition.ConditionEvaluator
import com.autoflow.core.engine.http.HttpExecutor
import com.autoflow.core.engine.http.HttpRequestActionHandler
import com.autoflow.core.engine.http.HttpResponse
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AudioStream
import com.autoflow.core.model.Automation
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.ExecutionStatus
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.StepStatus
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Variable
import com.autoflow.core.model.VariableScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AutomationEngineTest {
    private val zone = ZoneId.of("UTC")

    /** Wednesday 2026-10-07 at [hour]:00. */
    private fun clockAt(hour: Int) = Clock.fixed(Instant.parse("2026-10-07T%02d:00:00Z".format(hour)), zone)

    private val executed = mutableListOf<ActionSpec>()
    private val shownNotifications = mutableListOf<String>()

    private fun registry(extra: ActionRegistry.Builder.() -> Unit = {}) = ActionRegistry.Builder()
        .register<ActionSpec.SetVolume> { action, _ -> executed += action; ActionResult.Success() }
        .register<ActionSpec.LaunchApp> { action, _ ->
            executed += action
            ActionResult.Fallback("Background launch blocked, notification shown")
        }
        .register<ActionSpec.ShowNotification> { action, ctx ->
            executed += action
            shownNotifications += ctx.resolve(action.message)
            ActionResult.Success()
        }
        .apply(extra)
        .build()

    private class Harness(
        val engine: AutomationEngine,
        val automations: FakeAutomationRepository,
        val executions: FakeExecutionRepository,
        val variables: FakeVariableRepository,
        val settings: FakeSettings,
    )

    private fun harness(
        vararg automations: Automation,
        hour: Int = 9,
        state: DeviceState = DeviceState(batteryLevel = 64, headphonesConnected = true),
        registry: ActionRegistry = registry(),
        variables: FakeVariableRepository = FakeVariableRepository(),
    ): Harness {
        val repo = FakeAutomationRepository(*automations)
        val executions = FakeExecutionRepository()
        val settings = FakeSettings()
        val evaluator = ConditionEvaluator()
        val clock = clockAt(hour)
        val engine = AutomationEngine(
            automations = repo,
            executions = executions,
            variables = variables,
            settings = settings,
            deviceStateProvider = { state },
            pipeline = ActionPipeline(registry, evaluator),
            conditionEvaluator = evaluator,
            clock = clock,
            idGenerator = { "exec-" + executions.records.value.size },
        )
        return Harness(engine, repo, executions, variables, settings)
    }

    private val musicMode = Automation(
        id = "music",
        name = "Music mode",
        triggers = listOf(TriggerSpec.HeadphonesConnected(HeadphoneKind.ANY)),
        condition = ConditionNode.TimeRange(TimeOfDay(7, 0), TimeOfDay(22, 0)),
        actions = listOf(
            ActionSpec.SetVolume(AudioStream.MEDIA, 70),
            ActionSpec.LaunchApp("com.spotify.music", "Spotify"),
            ActionSpec.ShowNotification("AutoFlow", "Music mode activated (battery %battery%%)"),
        ),
    )

    @Test
    fun `expected MVP scenario runs all actions in order`() = runTest {
        val h = harness(musicMode)
        val records = h.engine.handleEvent(TriggerEvent.HeadphonesConnected(HeadphoneKind.BLUETOOTH, "Buds"))

        assertEquals(1, records.size)
        assertEquals(
            listOf("SET_VOLUME", "LAUNCH_APP", "NOTIFICATION"),
            records.single().steps.filter { it.kind == StepKind.ACTION }.map { it.key },
        )
        assertEquals(3, executed.size)
        assertEquals(listOf("Music mode activated (battery 64%)"), shownNotifications)
        // Launch used a fallback (Android background restriction) -> partial.
        assertEquals(ExecutionStatus.PARTIAL, records.single().status)
        assertEquals(records.single(), h.executions.records.value.single())
    }

    @Test
    fun `conditions not met skips and logs`() = runTest {
        val h = harness(musicMode, hour = 23)
        val record = h.engine.handleEvent(TriggerEvent.HeadphonesConnected(HeadphoneKind.WIRED, null)).single()
        assertEquals(ExecutionStatus.SKIPPED, record.status)
        assertTrue(executed.isEmpty())
        assertTrue(record.steps.any { it.kind == StepKind.CONDITION && it.status == StepStatus.FAILURE })

        h.settings.logSkipped = false
        assertTrue(h.engine.handleEvent(TriggerEvent.HeadphonesConnected(HeadphoneKind.WIRED, null)).isEmpty())
        assertEquals(1, h.executions.records.value.size)
    }

    @Test
    fun `master switch and disabled automations block triggers but not manual runs`() = runTest {
        val h = harness(musicMode, musicMode.copy(id = "off", enabled = false))
        h.settings.master = false
        assertTrue(h.engine.handleEvent(TriggerEvent.HeadphonesConnected(HeadphoneKind.WIRED, null)).isEmpty())

        h.settings.master = true
        val records = h.engine.handleEvent(TriggerEvent.HeadphonesConnected(HeadphoneKind.WIRED, null))
        assertEquals(listOf("music"), records.map { it.automationId })

        h.settings.master = false
        val manual = h.engine.runById("off", TriggerEvent.Manual("widget"))
        assertEquals("off", manual?.automationId)
    }

    @Test
    fun `non matching event runs nothing`() = runTest {
        val h = harness(musicMode)
        assertTrue(h.engine.handleEvent(TriggerEvent.ChargerConnected).isEmpty())
    }

    @Test
    fun `time alarm targets one automation`() = runTest {
        val timed = musicMode.copy(id = "timed", triggers = listOf(TriggerSpec.Time(TimeOfDay(9, 0))), condition = null)
        val h = harness(timed, musicMode)
        val records = h.engine.handleEvent(TriggerEvent.TimeAlarm("timed", 0))
        assertEquals(listOf("timed"), records.map { it.automationId })
    }

    @Test
    fun `location transitions target one automation and the matching direction`() = runTest {
        val home = com.autoflow.core.model.GeoPlace("Home", 48.8566, 2.3522, 200)
        val arriveLeave = Automation(
            id = "home",
            name = "Home",
            triggers = listOf(TriggerSpec.LocationEnter(home), TriggerSpec.LocationExit(home)),
            actions = listOf(ActionSpec.ShowNotification("Home", "at %trigger_place% (%location%)")),
        )
        val h = harness(arriveLeave, musicMode, state = DeviceState(latitude = 48.85661, longitude = 2.35222))

        val arrived = h.engine.handleEvent(TriggerEvent.LocationTransition("home", 0, entered = true, placeName = "Home"))
        assertEquals(listOf("home"), arrived.map { it.automationId })
        assertEquals(listOf("at Home (48.856610,2.352220)"), shownNotifications)

        // Index 0 is an "enter" trigger: an exit transition addressed to it is ignored.
        assertTrue(h.engine.handleEvent(TriggerEvent.LocationTransition("home", 0, entered = false, placeName = "Home")).isEmpty())
        assertEquals(1, h.engine.handleEvent(TriggerEvent.LocationTransition("home", 1, entered = false, placeName = "Home")).size)
        // Unknown index or automation is ignored.
        assertTrue(h.engine.handleEvent(TriggerEvent.LocationTransition("home", 7, entered = true, placeName = "Home")).isEmpty())
        assertTrue(h.engine.handleEvent(TriggerEvent.LocationTransition("missing", 0, entered = true, placeName = "Home")).isEmpty())
    }

    @Test
    fun `notification trigger filters by app and text, never logs content`() = runTest {
        val readOut = Automation(
            id = "wa",
            name = "Read WhatsApp",
            triggers = listOf(TriggerSpec.NotificationReceived(packageName = "com.whatsapp", textContains = "urgent")),
            actions = listOf(ActionSpec.ShowNotification("Echo", "%notification_app%: %notification_title% - %notification_text%")),
        )
        val h = harness(readOut)
        val other = TriggerEvent.NotificationPosted("com.other", "Other", "URGENT", "x")
        assertTrue(h.engine.handleEvent(other).isEmpty())
        assertTrue(h.engine.handleEvent(TriggerEvent.NotificationPosted("com.whatsapp", "WhatsApp", "Mom", "hello")).isEmpty())

        val record = h.engine.handleEvent(TriggerEvent.NotificationPosted("com.whatsapp", "WhatsApp", "Mom", "Urgent: call me")).single()
        assertEquals(listOf("WhatsApp: Mom - Urgent: call me"), shownNotifications)
        // Notification content stays out of the history.
        assertEquals("package=com.whatsapp", record.triggerDetail)
        assertTrue(record.steps.none { it.message.contains("call me") })
    }

    @Test
    fun `calendar events target one automation and filter by direction and title`() = runTest {
        val meetings = Automation(
            id = "meet",
            name = "Meetings",
            triggers = listOf(TriggerSpec.CalendarEventStart("meeting"), TriggerSpec.CalendarEventEnd()),
            actions = listOf(ActionSpec.ShowNotification("Cal", "%event_title% @ %event_location%")),
        )
        val h = harness(meetings)
        fun event(index: Int, started: Boolean, title: String) =
            TriggerEvent.CalendarEvent("meet", index, started, title, "Room 1")

        assertEquals(1, h.engine.handleEvent(event(0, true, "Team Meeting")).size)
        assertTrue(h.engine.handleEvent(event(0, true, "Lunch")).isEmpty())
        assertTrue(h.engine.handleEvent(event(0, false, "Team Meeting")).isEmpty())
        assertEquals(1, h.engine.handleEvent(event(1, false, "Lunch")).size)
        assertEquals(listOf("Team Meeting @ Room 1", "Lunch @ Room 1"), shownNotifications)
        assertTrue(h.executions.records.value.all { it.triggerDetail.isEmpty() })
    }

    @Test
    fun `failures are reported and stopOnError aborts`() = runTest {
        val failing = registry {
            register<ActionSpec.SetBrightness> { _, _ -> throw SecurityException("WRITE_SETTINGS not granted") }
        }
        val automation = Automation(
            id = "a",
            name = "A",
            actions = listOf(ActionSpec.SetBrightness(50), ActionSpec.SetVolume(percent = 10), ActionSpec.Speak("hi")),
        )
        val h = harness(automation, registry = failing)
        val record = h.engine.runById("a")!!
        val actionSteps = record.steps.filter { it.kind == StepKind.ACTION }
        assertEquals(FailureKind.PERMISSION_DENIED, actionSteps[0].failureKind)
        assertEquals(StepStatus.SUCCESS, actionSteps[1].status)
        assertEquals("missing handler", FailureKind.NOT_SUPPORTED, actionSteps[2].failureKind)
        assertEquals(ExecutionStatus.PARTIAL, record.status)

        h.automations.upsert(automation.copy(stopOnError = true))
        val aborted = h.engine.runById("a")!!
        assertEquals(1, aborted.steps.count { it.kind == StepKind.ACTION })
        assertEquals(ExecutionStatus.FAILED, aborted.status)
    }

    @Test
    fun `variables, math, if else and repeat`() = runTest {
        val vars = FakeVariableRepository(Variable("counter", "10"))
        val automation = Automation(
            id = "logic",
            name = "Logic",
            actions = listOf(
                ActionSpec.SetVariable("counter", "\$counter + 1", evaluateMath = true),
                ActionSpec.Repeat(3, listOf(ActionSpec.SetVariable("counter", "\$counter * 2", evaluateMath = true))),
                ActionSpec.IfElse(
                    condition = ConditionNode.VariableCompare("counter", Comparison.GREATER_THAN, "50"),
                    thenActions = listOf(ActionSpec.ShowNotification("t", "big \$counter")),
                    elseActions = listOf(ActionSpec.ShowNotification("t", "small \$counter")),
                ),
                ActionSpec.SetVariable("temp", "x", scope = VariableScope.LOCAL),
            ),
        )
        val h = harness(automation, variables = vars)
        val record = h.engine.runById("logic")!!
        assertEquals(ExecutionStatus.SUCCESS, record.status)
        assertEquals("88", vars.get("counter")?.value)
        assertEquals(listOf("big 88"), shownNotifications)
        assertNull("local variables are not persisted", vars.get("temp"))
    }

    @Test
    fun `while, wait until, stop, variable operation and json parsing`() = runTest {
        val vars = FakeVariableRepository(Variable("count", "0"), Variable("payload", """{"alarm":{"level":"high"}}"""))
        val automation = Automation(
            id = "flow",
            name = "Flow",
            actions = listOf(
                ActionSpec.While(
                    condition = ConditionNode.VariableCompare("count", Comparison.LESS_THAN, "3"),
                    actions = listOf(ActionSpec.VariableOperation("count", com.autoflow.core.model.VariableOp.INCREMENT)),
                ),
                ActionSpec.WaitUntil(ConditionNode.VariableCompare("count", Comparison.EQUALS, "3"), timeoutSeconds = 5),
                ActionSpec.ParseJson("\$payload", "alarm.level", "level"),
                ActionSpec.ShowNotification("t", "count=\$count level=\$level"),
                ActionSpec.IfElse(ConditionNode.VariableCompare("level", Comparison.EQUALS, "high"), listOf(ActionSpec.Stop)),
                ActionSpec.ShowNotification("t", "never shown"),
            ),
        )
        val h = harness(automation, variables = vars)
        val record = h.engine.runById("flow")!!
        assertEquals(listOf("count=3 level=high"), shownNotifications)
        assertEquals("3", vars.get("count")?.value)
        assertNull("parse_json defaults to a local variable", vars.get("level"))
        assertEquals(ExecutionStatus.SUCCESS, record.status)
        assertTrue(record.steps.any { it.key == "STOP" })
    }

    @Test
    fun `while is capped and wait until times out with virtual time`() = runTest {
        val automation = Automation(
            id = "cap",
            name = "Cap",
            actions = listOf(
                ActionSpec.While(ConditionNode.And(emptyList()), listOf(ActionSpec.SetVolume(percent = 1)), maxIterations = 5),
                ActionSpec.WaitUntil(ConditionNode.Charging(true), timeoutSeconds = 600, checkIntervalSeconds = 60),
            ),
        )
        val h = harness(automation)
        val record = h.engine.runById("cap")!!
        assertEquals(5, executed.size)
        val wait = record.steps.last { it.key == "WAIT_UNTIL" }
        assertEquals(FailureKind.TIMEOUT, wait.failureKind)
        assertEquals(ExecutionStatus.PARTIAL, record.status)
    }

    @Test
    fun `delay uses virtual time`() = runTest {
        val automation = Automation(id = "d", name = "D", actions = listOf(ActionSpec.Delay(60_000), ActionSpec.SetVolume(percent = 5)))
        val h = harness(automation)
        val record = h.engine.runById("d")!!
        assertEquals(ExecutionStatus.SUCCESS, record.status)
        assertEquals(1, executed.size)
    }

    @Test
    fun `concurrent run of same automation is skipped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = registry {
            register<ActionSpec.Speak> { _, _ -> gate.await(); ActionResult.Success() }
        }
        val automation = Automation(id = "s", name = "S", actions = listOf(ActionSpec.Speak("hello")))
        val h = harness(automation, registry = slow)
        val first = async { h.engine.runById("s") }
        testScheduler.runCurrent()
        val second = h.engine.runById("s")
        assertEquals(ExecutionStatus.SKIPPED, second?.status)
        gate.complete(Unit)
        assertEquals(ExecutionStatus.SUCCESS, first.await()?.status)
    }

    @Test
    fun `http request stores response variables`() = runTest {
        val calls = mutableListOf<com.autoflow.core.engine.http.HttpCall>()
        val http = HttpRequestActionHandler(
            HttpExecutor { call ->
                calls += call
                if (call.url.contains("fail")) throw IOException("unreachable")
                HttpResponse(200, """{"type":"alarm","value":7}""")
            },
        )
        val reg = registry { register(ActionSpec.HttpRequest::class, http) }
        val automation = Automation(
            id = "h",
            name = "H",
            actions = listOf(
                ActionSpec.HttpRequest(
                    method = com.autoflow.core.model.HttpMethod.POST,
                    url = "https://example.com/api/device",
                    queryParameters = mapOf("q" to "a b"),
                    body = """{"battery":"%battery%"}""",
                ),
                ActionSpec.IfElse(
                    ConditionNode.VariableCompare("http.type", Comparison.EQUALS, "alarm"),
                    listOf(ActionSpec.ShowNotification("Alarm", "value \$http.value status \$http_status")),
                ),
                ActionSpec.HttpRequest(url = "https://fail.example.com"),
            ),
        )
        val h = harness(automation, registry = reg)
        val record = h.engine.runById("h")!!
        assertEquals("https://example.com/api/device?q=a+b", calls[0].url)
        assertEquals("{\"battery\":\"64\"}", calls[0].body)
        assertNull("GET has no body", calls[1].body)
        assertEquals(listOf("value 7 status 200"), shownNotifications)
        assertEquals(ExecutionStatus.PARTIAL, record.status)
    }
}
