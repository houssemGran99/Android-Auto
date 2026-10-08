package com.autoflow.platform.actions

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AudioStream
import com.autoflow.core.model.Automation
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.Variable
import com.autoflow.platform.actions.handler.LaunchAppActionHandler
import com.autoflow.platform.actions.handler.SetBrightnessActionHandler
import com.autoflow.platform.actions.handler.SetVolumeActionHandler
import com.autoflow.platform.actions.handler.ShowNotificationActionHandler
import com.autoflow.platform.permissions.PermissionManager
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ActionHandlersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val permissions = PermissionManager(context)

    private val variables = object : VariableRepository {
        override fun observeAll() = emptyFlow<List<Variable>>()
        override suspend fun getAll() = emptyList<Variable>()
        override suspend fun get(name: String): Variable? = null
        override suspend fun set(name: String, value: String, secret: Boolean?) = Unit
        override suspend fun delete(name: String) = Unit
    }

    private val automationContext = AutomationContext(
        automation = Automation(id = "a", name = "Music mode"),
        event = TriggerEvent.Manual("test"),
        deviceState = DeviceState(batteryLevel = 55),
        globalVariables = mapOf("userName" to "John"),
        variableRepository = variables,
        deviceStateProvider = { DeviceState() },
        clock = Clock.systemUTC(),
    )

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
    }

    @Test
    fun volumeIsScaledToStreamRange() = runTest {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val result = SetVolumeActionHandler(context).execute(ActionSpec.SetVolume(AudioStream.MEDIA, 70), automationContext)
        assertTrue(result is ActionResult.Success)
        assertEquals(Math.round(max * 0.7).toInt(), audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test
    fun notificationNeedsPermissionThenResolvesVariables() = runTest {
        val handler = ShowNotificationActionHandler(context, permissions)
        val action = ActionSpec.ShowNotification("Hi \$userName", "Battery %battery%%")

        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val denied = handler.execute(action, automationContext)
        assertEquals(FailureKind.PERMISSION_DENIED, (denied as ActionResult.Failure).kind)

        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val result = handler.execute(action, automationContext)
        assertEquals(ActionResult.Success("Hi John"), result)
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Battery 55%", shadowOf(posted).contentText)
    }

    @Test
    fun brightnessRequiresWriteSettings() = runTest {
        val handler = SetBrightnessActionHandler(context)
        // Settings.System.canWrite() checks the WRITE_SETTINGS app op (no ShadowSettings switch exists).
        val appOps = shadowOf(context.getSystemService(AppOpsManager::class.java))
        appOps.setMode(AppOpsManager.OPSTR_WRITE_SETTINGS, Process.myUid(), context.opPackageName, AppOpsManager.MODE_ERRORED)
        val denied = handler.execute(ActionSpec.SetBrightness(50), automationContext)
        assertEquals(FailureKind.PERMISSION_DENIED, (denied as ActionResult.Failure).kind)

        appOps.setMode(AppOpsManager.OPSTR_WRITE_SETTINGS, Process.myUid(), context.opPackageName, AppOpsManager.MODE_ALLOWED)
        assertTrue(handler.execute(ActionSpec.SetBrightness(50), automationContext) is ActionResult.Success)
        assertEquals(128, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    }

    @Test
    fun launchFromBackgroundFallsBackToNotification() = runTest {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.spotify.music")
        shadowOf(context.packageManager).addResolveInfoForIntent(
            launchIntent,
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    packageName = "com.spotify.music"
                    name = "com.spotify.music.MainActivity"
                }
            },
        )
        shadowOf(context.packageManager).addActivityIfNotPresent(
            android.content.ComponentName("com.spotify.music", "com.spotify.music.MainActivity"),
        )
        val launcher = ActivityLauncher(context, permissions, isAppInForeground = { false })
        val result = LaunchAppActionHandler(context, launcher)
            .execute(ActionSpec.LaunchApp("com.spotify.music", "Spotify"), automationContext)

        assertTrue("expected fallback but was $result", result is ActionResult.Fallback)
        assertEquals(1, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)

        val missing = LaunchAppActionHandler(context, launcher)
            .execute(ActionSpec.LaunchApp("com.not.installed"), automationContext)
        assertEquals(FailureKind.INVALID_CONFIGURATION, (missing as ActionResult.Failure).kind)
    }
}
