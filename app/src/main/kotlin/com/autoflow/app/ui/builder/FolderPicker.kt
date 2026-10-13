package com.autoflow.app.ui.builder

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.autoflow.app.R

/**
 * Opens the system folder picker. Only the picked folder becomes readable (a persisted
 * Storage Access Framework grant), so no storage permission is ever requested.
 */
@Composable
fun FolderPickerField(
    folderUri: String,
    label: String,
    emptyText: String = stringResource(R.string.pick_folder),
    onPick: (uri: String, label: String) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Read + write when the provider allows it (file actions write), otherwise read-only.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onPick(uri.toString(), folderLabel(uri))
    }
    OutlinedButton(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (folderUri.isBlank()) emptyText else label.ifBlank { folderUri })
    }
}

/** "primary:Music/Jazz" → "Music/Jazz". */
internal fun folderLabel(uri: Uri): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return uri.lastPathSegment.orEmpty()
    return id.substringAfter(':').ifBlank { id.substringBefore(':') }
}
