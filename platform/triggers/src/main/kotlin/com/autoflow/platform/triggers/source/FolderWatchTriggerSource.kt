package com.autoflow.platform.triggers.source

import android.content.Context
import android.util.Log
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily
import com.autoflow.platform.files.SafFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detects new files in user-picked folders. Apps get no file-system events for shared
 * storage, so the folders used by "New file in folder" triggers are listed every
 * [pollIntervalMs]. The first listing of a folder is a baseline: files that already exist
 * never fire, and neither do files that are only renamed back and forth within one interval.
 */
class FolderWatchTriggerSource(
    context: Context,
    private val scope: CoroutineScope,
    private val watchedFolders: suspend () -> Set<String>,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : TriggerSource {
    override val family = TriggerFamily.FILES

    private val appContext = context.applicationContext
    private var job: Job? = null

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        job = scope.launch {
            val known = mutableMapOf<String, Set<String>>()
            while (isActive) {
                val folders = runCatching { watchedFolders() }.getOrDefault(emptySet())
                known.keys.retainAll(folders)
                for (folderUri in folders) {
                    val names = withContext(Dispatchers.IO) { listNames(folderUri) } ?: continue
                    val previous = known[folderUri]
                    known[folderUri] = names
                    if (previous == null) continue
                    (names - previous).sorted().forEach { onEvent(TriggerEvent.FileAdded(folderUri, it)) }
                }
                delay(pollIntervalMs)
            }
        }
    }

    override fun unregister() {
        job?.cancel()
        job = null
    }

    /** File names of the folder, or null when it cannot be read (grant revoked, folder deleted). */
    private fun listNames(folderUri: String): Set<String>? {
        val folder = SafFolder.parse(appContext, folderUri) ?: return null
        if (!folder.hasAccess()) return null
        return try {
            folder.list().filterNot { it.isDirectory }.mapTo(mutableSetOf()) { it.name }
        } catch (e: Exception) {
            Log.w("AutoFlow", "Cannot list watched folder", e)
            null
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 60_000L
    }
}
