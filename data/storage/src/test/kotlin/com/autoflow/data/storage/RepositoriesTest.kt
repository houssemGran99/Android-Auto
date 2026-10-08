package com.autoflow.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.ExecutionRecord
import com.autoflow.core.model.ExecutionStatus
import com.autoflow.core.model.ExecutionStep
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.StepStatus
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.TriggerSpec
import com.autoflow.data.storage.repository.RoomAutomationRepository
import com.autoflow.data.storage.repository.RoomExecutionRepository
import com.autoflow.data.storage.repository.RoomVariableRepository
import com.autoflow.data.storage.security.SecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoriesTest {
    private lateinit var db: AutoFlowDatabase

    /** Reversible fake: the Android Keystore is not available under Robolectric. */
    private val cipher = object : SecretCipher {
        override fun encrypt(plainText: String) = "enc:" + plainText.reversed()
        override fun decrypt(cipherText: String) = cipherText.removePrefix("enc:").reversed()
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AutoFlowDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun automationRoundTrip() = runTest {
        var clock = 1000L
        val repo = RoomAutomationRepository(db.automationDao()) { clock }
        val automation = Automation(
            id = "a1",
            name = "Office",
            triggers = listOf(TriggerSpec.WifiConnected("Office")),
            condition = ConditionNode.TimeRange(TimeOfDay(8, 0), TimeOfDay(18, 0)),
            actions = listOf(ActionSpec.SetVolume(percent = 60), ActionSpec.IfElse(ConditionNode.Charging(), listOf(ActionSpec.Delay(5)))),
        )
        repo.upsert(automation)
        val stored = repo.get("a1")!!
        assertEquals(automation.copy(createdAt = 1000, updatedAt = 1000), stored)

        clock = 2000
        repo.upsert(stored.copy(name = "Office 2"))
        assertEquals(1000, repo.get("a1")!!.createdAt)
        assertEquals(2000, repo.get("a1")!!.updatedAt)

        repo.setEnabled("a1", false)
        assertEquals(emptyList<Automation>(), repo.getEnabled())
        assertEquals(1, repo.observeAll().first().size)

        repo.delete("a1")
        assertNull(repo.get("a1"))
    }

    @Test
    fun corruptRowsAreSkipped() = runTest {
        val repo = RoomAutomationRepository(db.automationDao())
        repo.upsert(Automation(id = "ok", name = "ok"))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO automations (id, name, description, enabled, triggers_json, condition_json, actions_json, " +
                "stop_on_error, quick_action, created_at, updated_at) " +
                "VALUES ('bad','bad','',1,'[{\"type\":\"NOPE\"}]',NULL,'[]',0,0,0,0)",
        )
        assertEquals(listOf("ok"), repo.getAll().map { it.id })
    }

    @Test
    fun executionsAreStoredWithStepsAndPruned() = runTest {
        val repo = RoomExecutionRepository(db.executionDao(), keepRecords = 2)
        repeat(3) { i ->
            repo.record(
                ExecutionRecord(
                    id = "e$i",
                    automationId = "a",
                    automationName = "A",
                    triggerKey = "MANUAL",
                    triggerDetail = "",
                    status = ExecutionStatus.PARTIAL,
                    startedAt = i.toLong(),
                    finishedAt = i + 1L,
                    steps = listOf(
                        ExecutionStep(StepKind.TRIGGER, "MANUAL", StepStatus.SUCCESS, timestamp = 0),
                        ExecutionStep(StepKind.ACTION, "SET_BRIGHTNESS", StepStatus.FAILURE, "denied", FailureKind.PERMISSION_DENIED, 1),
                    ),
                ),
            )
        }
        val recent = repo.observeRecent(10).first()
        assertEquals(listOf("e2", "e1"), recent.map { it.id })
        assertEquals(FailureKind.PERMISSION_DENIED, recent.first().steps[1].failureKind)
        assertEquals(listOf(StepKind.TRIGGER, StepKind.ACTION), recent.first().steps.map { it.kind })

        repo.clear()
        assertEquals(0, repo.observeRecent(10).first().size)
    }

    @Test
    fun secretVariablesAreEncryptedAtRest() = runTest {
        val repo = RoomVariableRepository(db.variableDao(), cipher)
        repo.set("token", "abc123", secret = true)
        repo.set("userName", "John")
        repo.set("token", "xyz") // keeps secret flag

        assertEquals("xyz", repo.get("token")!!.value)
        val raw = db.variableDao().get("token")!!
        assertNotEquals("xyz", raw.value)
        assertFalse(raw.value.contains("xyz"))
        assertEquals(listOf("token", "userName"), repo.getAll().map { it.name })
    }
}
