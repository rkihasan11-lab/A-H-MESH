package com.ahmesh.app.data.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ahmesh.app.domain.security.AuditExportPayload
import java.io.File

object AuditLogExportFileWriter {

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }
            } ?: return false
            true
        }.getOrDefault(false)
    }

    fun createShareUri(context: Context, payload: AuditExportPayload): Uri? {
        return runCatching {
            val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeName = payload.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val outFile = File(sharedDir, safeName)
            outFile.writeText(payload.content, Charsets.UTF_8)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        }.getOrNull()
    }

    fun buildShareChooserIntent(
        context: Context,
        uri: Uri,
        payload: AuditExportPayload,
        chooserTitle: String
    ): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = payload.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }
}
