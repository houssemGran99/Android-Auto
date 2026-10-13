package com.autoflow.app.runtime

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.autoflow.app.R
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.data.storage.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TileEntryPoint {
    fun settings(): SettingsRepository
    fun automations(): AutomationRepository
}

object QuickTiles {
    private val services = listOf(
        AutomationTile1Service::class.java,
        AutomationTile2Service::class.java,
        AutomationTile3Service::class.java,
        MasterSwitchTileService::class.java,
    )

    /** Asks the system to re-bind added tiles so they show current names and state. */
    fun requestRefresh(context: Context) {
        services.forEach { service ->
            runCatching { TileService.requestListeningState(context, ComponentName(context, service)) }
        }
    }
}

/** Shared plumbing: Hilt access, a coroutine scope and opening the app. */
abstract class AutoFlowTileService : TileService() {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected val entryPoint: TileEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // The Intent overload is only used below Android 14, where the PendingIntent one does not exist.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    protected fun Tile.show(label: String, subtitle: String?, state: Int) {
        this.label = label
        this.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) this.subtitle = subtitle
        icon = Icon.createWithResource(this@AutoFlowTileService, com.autoflow.platform.actions.R.drawable.ic_stat_autoflow)
        updateTile()
    }
}

/**
 * Runs the automation assigned to this tile in Settings → Quick Settings tiles.
 * On the lock screen the device is unlocked first, so nobody can run automations without unlocking.
 */
abstract class AutomationTileService(private val slot: Int) : AutoFlowTileService() {

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val id = assignedId()
            val automation = id?.let { entryPoint.automations().get(it) }
            if (automation == null) {
                openApp()
                return@launch
            }
            val run = { AutomationRunWorker.enqueueManual(applicationContext, automation.id, SOURCE) }
            if (isLocked) unlockAndRun(run) else run()
        }
    }

    private suspend fun assignedId(): String? = entryPoint.settings().settings.first().quickTiles.getOrNull(slot)

    private suspend fun render() {
        val tile = qsTile ?: return
        val automation = assignedId()?.let { entryPoint.automations().get(it) }
        if (automation == null) {
            tile.show(getString(R.string.tile_not_set_up, slot + 1), getString(R.string.tile_tap_to_set_up), Tile.STATE_INACTIVE)
        } else {
            tile.show(automation.name, getString(R.string.app_name), Tile.STATE_INACTIVE)
        }
    }

    companion object {
        const val SOURCE = "quick_settings_tile"
    }
}

class AutomationTile1Service : AutomationTileService(0)

class AutomationTile2Service : AutomationTileService(1)

class AutomationTile3Service : AutomationTileService(2)

/** Toggles the master switch (all automations on / off). */
class MasterSwitchTileService : AutoFlowTileService() {
    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val settings = entryPoint.settings()
            settings.setMasterEnabled(!settings.settings.first().masterEnabled)
            render()
        }
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val enabled = entryPoint.settings().settings.first().masterEnabled
        tile.show(
            getString(R.string.tile_master),
            getString(if (enabled) R.string.state_on else R.string.state_off),
            if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
        )
    }
}
