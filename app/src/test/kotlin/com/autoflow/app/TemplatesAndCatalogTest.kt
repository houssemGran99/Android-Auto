package com.autoflow.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.app.data.TemplateCatalog
import com.autoflow.app.ui.builder.BuilderCatalog
import com.autoflow.app.ui.text.SpecFormatter
import com.autoflow.core.engine.http.HttpExecutor
import com.autoflow.core.engine.http.HttpResponse
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AutomationBundle
import com.autoflow.core.model.AutomationJson
import com.autoflow.core.model.flattenActions
import com.autoflow.core.model.typeKey
import com.autoflow.platform.actions.PlatformActions
import com.autoflow.platform.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class TemplatesAndCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyTemplateIsValidRunnableAndRoundTrips() {
        val catalog = TemplateCatalog(context)
        val registry = PlatformActions.registry(context, PermissionManager(context), HttpExecutor { HttpResponse(200, "") }, { false })
        val engineHandled = setOf(ActionSpec.Delay::class, ActionSpec.SetVariable::class, ActionSpec.IfElse::class, ActionSpec.Repeat::class)

        catalog.templates.forEach { template ->
            val automation = catalog.instantiate(template.id)
            assertNotNull(automation)
            automation!!
            assertTrue(template.id, automation.name.isNotBlank())
            assertTrue(template.id, automation.actions.isNotEmpty())
            automation.actions.flattenActions()
                .filterNot { it::class in engineHandled }
                .forEach { action -> assertNotNull("${template.id}: ${action.typeKey}", registry.handlerFor(action)) }

            val json = AutomationJson.encodeBundle(AutomationBundle(automations = listOf(automation)))
            assertEquals(automation, AutomationJson.decodeBundle(json).automations.single())
        }
        // The MVP scenario template exists.
        assertNotNull(catalog.find("music_mode"))
    }

    @Test
    fun everyCatalogEntryHasALocalizedTitle() {
        val f = SpecFormatter(context.resources)
        BuilderCatalog.triggers.forEach { assertNotEquals(it.typeKey, f.triggerTitle(it.create())) }
        BuilderCatalog.actions.forEach { assertNotEquals(it.typeKey, f.actionTitle(it.create())) }
        BuilderCatalog.conditions.forEach { assertNotEquals(it.typeKey, f.conditionTitle(it.create())) }
    }
}
