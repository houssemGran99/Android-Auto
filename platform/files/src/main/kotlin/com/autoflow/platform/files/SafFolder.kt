package com.autoflow.platform.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException

/** A direct child of a picked folder. */
data class FolderEntry(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val uri: Uri,
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

/**
 * File operations inside one folder the user picked with the system folder picker
 * (a Storage Access Framework tree URI with a persisted grant). AutoFlow never asks for
 * storage permissions: it can only touch folders the user explicitly shared.
 * All calls do blocking I/O; call them off the main thread.
 */
class SafFolder(context: Context, val treeUri: Uri) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private val rootDocumentUri: Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    /** True while the app holds a persisted grant for the folder ([write] also requires write access). */
    fun hasAccess(write: Boolean = false): Boolean = resolver.persistedUriPermissions.any {
        it.uri == treeUri && it.isReadPermission && (!write || it.isWritePermission)
    }

    /** Files and subfolders directly inside the folder. */
    fun list(): List<FolderEntry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val entries = mutableListOf<FolderEntry>()
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                entries += FolderEntry(
                    documentId = id,
                    name = cursor.getString(1).orEmpty(),
                    mimeType = cursor.getString(2).orEmpty(),
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                )
            }
        }
        return entries
    }

    /** The file named exactly [name] (case-sensitive, like the file system), or null. */
    fun find(name: String): FolderEntry? = list().firstOrNull { !it.isDirectory && it.name == name }

    /** Reads a text file; null when it does not exist. Fails when it is larger than [maxBytes]. */
    fun readText(name: String, maxBytes: Int = MAX_TEXT_BYTES): String? {
        val entry = find(name) ?: return null
        val stream = resolver.openInputStream(entry.uri) ?: throw FileNotFoundException(name)
        val bytes = stream.use { input ->
            val buffer = input.readNBytesCompat(maxBytes + 1)
            require(buffer.size <= maxBytes) { "File is larger than ${maxBytes / 1024} KB" }
            buffer
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /** Writes (or appends) UTF-8 text; creates the file when it does not exist. Returns its URI. */
    fun writeText(name: String, text: String, append: Boolean): Uri {
        val uri = find(name)?.uri ?: create(name)
        val stream = resolver.openOutputStream(uri, if (append) "wa" else "wt") ?: throw FileNotFoundException(name)
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return uri
    }

    /** Copies [source] (any readable document) into this folder as [name], replacing an existing file. */
    fun copyFrom(source: Uri, name: String): Uri {
        val target = find(name)?.uri ?: create(name)
        val input = resolver.openInputStream(source) ?: throw FileNotFoundException(source.toString())
        input.use { from ->
            val output = resolver.openOutputStream(target, "wt") ?: throw FileNotFoundException(name)
            output.use { from.copyTo(it) }
        }
        return target
    }

    /** URI of [name] for writing: the existing file, or a newly created empty one. */
    fun fileForWrite(name: String): Uri = find(name)?.uri ?: create(name)

    /** Deletes the file; false when it did not exist. */
    fun delete(name: String): Boolean {
        val entry = find(name) ?: return false
        return DocumentsContract.deleteDocument(resolver, entry.uri)
    }

    private fun create(name: String): Uri =
        DocumentsContract.createDocument(resolver, rootDocumentUri, mimeTypeFor(name), name)
            ?: throw FileNotFoundException("Cannot create $name")

    companion object {
        const val MAX_TEXT_BYTES = 1024 * 1024

        /** Parses a stored folder URI; null when it is not a content:// tree URI. */
        fun parse(context: Context, folderUri: String): SafFolder? {
            if (!folderUri.startsWith("content://")) return null
            return runCatching { SafFolder(context, Uri.parse(folderUri)) }.getOrNull()
        }

        /**
         * File names may not contain path separators or be "." / ".."; returns the trimmed
         * name or null when it is not a plain file name.
         */
        fun validFileName(raw: String): String? {
            val name = raw.trim()
            if (name.isEmpty() || name == "." || name == ".." || name.length > 255) return null
            if (name.any { it == '/' || it == '\\' || it == '\u0000' }) return null
            return name
        }

        /** MIME type from the extension, so providers do not append their own extension. */
        fun mimeTypeFor(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) return "application/octet-stream"
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: when (extension) {
                    "txt", "log", "md" -> "text/plain"
                    "csv" -> "text/csv"
                    "json" -> "application/json"
                    else -> "application/octet-stream"
                }
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
