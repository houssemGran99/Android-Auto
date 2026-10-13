package com.autoflow.platform.actions.music

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** An audio file found in a picked folder. */
data class Track(val uri: Uri, val title: String)

/**
 * Lists audio files below a Storage Access Framework tree URI (the folder the user picked).
 * Only that folder is readable: no storage permission is requested.
 */
class AudioFolderScanner(private val context: Context) {

    /** True while the app still holds the persisted read grant for [treeUri]. */
    fun hasAccess(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    /** Audio files of the folder and its subfolders (up to [MAX_DEPTH] levels, at most [MAX_TRACKS]). */
    fun scan(treeUri: Uri): List<Track> {
        val tracks = mutableListOf<Track>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        collect(treeUri, rootId, depth = 0, tracks)
        return tracks
    }

    private fun collect(treeUri: Uri, documentId: String, depth: Int, into: MutableList<Track>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val folders = mutableListOf<String>()
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && into.size < MAX_TRACKS) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1).orEmpty()
                val mime = cursor.getString(2).orEmpty()
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> folders += id
                    isAudio(mime, name) -> into += Track(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        name.substringBeforeLast('.').ifBlank { name },
                    )
                }
            }
        }
        if (depth < MAX_DEPTH) {
            for (folder in folders) {
                if (into.size >= MAX_TRACKS) break
                collect(treeUri, folder, depth + 1, into)
            }
        }
    }

    companion object {
        const val MAX_DEPTH = 3
        const val MAX_TRACKS = 2000

        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "mid", "midi", "amr", "3gp")

        fun isAudio(mime: String, name: String): Boolean =
            mime.startsWith("audio/") || mime == "application/ogg" ||
                (mime.isEmpty() || mime == "application/octet-stream") &&
                name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
    }
}
