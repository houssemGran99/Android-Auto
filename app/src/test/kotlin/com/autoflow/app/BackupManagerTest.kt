package com.autoflow.app

import com.autoflow.app.data.BackupManager
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Automation
import com.autoflow.core.model.InvalidAutomationFileException
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Variable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    private class Automations : AutomationRepository {
        val items = MutableStateFlow<Map<String, Automation>>(emptyMap())
        override fun observeAll() = items.map { it.values.toList() }
        override fun observe(id: String) = items.map { it[id] }
        override suspend fun getAll() = items.value.values.toList()
        override suspend fun getEnabled() = getAll().filter { it.enabled }
        override suspend fun get(id: String) = items.value[id]
        override suspend fun upsert(automation: Automation) {
            items.value += automation.id to automation
        }
        override suspend fun delete(id: String) {
            items.value -= id
        }
        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
    }

    private class Variables(vararg initial: Variable) : VariableRepository {
        val items = MutableStateFlow(initial.associateBy { it.name })
        override fun observeAll() = items.map { it.values.toList() }
        override suspend fun getAll() = items.value.values.toList()
        override suspend fun get(name: String) = items.value[name]
        override suspend fun set(name: String, value: String, secret: Boolean?) {
            items.value += name to Variable(name, value, secret ?: false)
        }
        override suspend fun delete(name: String) {
            items.value -= name
        }
    }

    private val office = Automation(
        id = "office",
        name = "Office Mode",
        triggers = listOf(TriggerSpec.WifiConnected("Office")),
        actions = listOf(ActionSpec.SetVolume(percent = 60)),
    )

    @Test
    fun exportImportRoundTripNeverLeaksSecretsOrOverwrites() = runTest {
        val source = Automations().apply { upsert(office) }
        val sourceVars = Variables(Variable("token", "s3cret", secret = true), Variable("userName", "John"))
        val json = BackupManager(source, sourceVars).exportAll()
        assertFalse(json.contains("s3cret"))

        val target = Automations().apply { upsert(office) }
        val targetVars = Variables(Variable("userName", "Jane"))
        val result = BackupManager(target, targetVars).import(json)

        assertEquals(1, result.automations)
        assertEquals(0, result.variables)
        assertEquals(2, target.getAll().size)
        val imported = target.getAll().first { it.id != "office" }
        assertEquals("Office Mode", imported.name)
        assertFalse("imported automations start disabled", imported.enabled)
        assertEquals("Jane", targetVars.get("userName")?.value)
    }

    @Test
    fun duplicateCreatesDisabledCopy() = runTest {
        val repo = Automations().apply { upsert(office) }
        val copy = BackupManager(repo, Variables()).duplicate("office", "(copy)")!!
        assertNotEquals("office", copy.id)
        assertEquals("Office Mode (copy)", copy.name)
        assertEquals(office.actions, copy.actions)
        assertTrue(repo.getAll().size == 2)
    }

    @Test(expected = InvalidAutomationFileException::class)
    fun invalidFileIsRejected() = runTest {
        BackupManager(Automations(), Variables()).import("{\"automations\": [{\"id\": 1}]}")
    }
}
