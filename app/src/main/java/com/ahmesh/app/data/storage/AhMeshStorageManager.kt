package com.ahmesh.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.ahmesh.app.domain.model.MessageType
import com.ahmesh.app.domain.model.PendingOutgoingFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ReceivedFileSink(
    val outputStream: OutputStream,
    val displayUri: String,
    val finalizeSink: () -> Unit,
    val abortSink: () -> Unit
)

@Singleton
class AhMeshStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun openIncomingFileSink(
        rawFileName: String,
        rawMimeType: String
    ): ReceivedFileSink {
        val safeName = sanitizeFileName(rawFileName)
        val mimeType = resolveMimeType(safeName, rawMimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaStoreSink = tryCreateMediaStoreDownloadSink(safeName, mimeType)
            if (mediaStoreSink != null) return mediaStoreSink
        }

        return createAppSafeFolderSink(safeName)
    }

    private fun tryCreateMediaStoreDownloadSink(
        safeName: String,
        mimeType: String
    ): ReceivedFileSink? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$AH_MESH_FOLDER_NAME"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val itemUri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null

        val outputStream = runCatching {
            resolver.openOutputStream(itemUri, "w")
        }.getOrNull()

        if (outputStream == null) {
            runCatching { resolver.delete(itemUri, null, null) }
            return null
        }

        return ReceivedFileSink(
            outputStream = outputStream,
            displayUri = itemUri.toString(),
            finalizeSink = {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                runCatching { resolver.update(itemUri, doneValues, null, null) }
            },
            abortSink = {
                runCatching { resolver.delete(itemUri, null, null) }
            }
        )
    }

    private fun createAppSafeFolderSink(safeName: String): ReceivedFileSink {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val ahMeshDir = File(baseDir, AH_MESH_FOLDER_NAME).apply {
            if (!exists()) mkdirs()
        }
        val targetFile = resolveUniqueFile(ahMeshDir, safeName)
        val outputStream = FileOutputStream(targetFile)
        val displayUri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            ).toString()
        }.getOrElse {
            targetFile.absolutePath
        }

        return ReceivedFileSink(
            outputStream = outputStream,
            displayUri = displayUri,
            finalizeSink = { /* File already flushed and closed */ },
            abortSink = {
                runCatching { targetFile.delete() }
            }
        )
    }

    suspend fun inspectPickedUri(uri: Uri): PendingOutgoingFile? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName: String? = null
        var fileSize = -1L

        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        if (fileSize < 0L) {
            fileSize = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }

        val resolvedName = sanitizeFileName(
            fileName ?: uri.lastPathSegment ?: "shared_${System.currentTimeMillis()}.bin"
        )
        val mimeType = resolveMimeType(resolvedName, resolver.getType(uri).orEmpty())
        val messageType = classifyMessageType(resolvedName, mimeType)

        PendingOutgoingFile(
            fileName = resolvedName,
            mimeType = mimeType,
            messageType = messageType,
            sizeBytes = maxOf(fileSize, 0L),
            uriString = uri.toString()
        )
    }

    fun openOutgoingInputStream(item: PendingOutgoingFile): InputStream? {
        val localPath = item.localFilePath
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists() && file.canRead()) {
                return FileInputStream(file)
            }
        }
        val uriStr = item.uriString ?: return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriStr))
        }.getOrNull()
    }

    fun getAppSafeStorageFolderPath(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DOWNLOADS}/$AH_MESH_FOLDER_NAME"
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            File(baseDir, AH_MESH_FOLDER_NAME).absolutePath
        }
    }

    private fun resolveUniqueFile(directory: File, safeName: String): File {
        var candidate = File(directory, safeName)
        if (!candidate.exists()) return candidate

        val dotIndex = safeName.lastIndexOf('.')
        val stem = if (dotIndex > 0) safeName.substring(0, dotIndex) else safeName
        val ext = if (dotIndex > 0) safeName.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "${stem}_($counter)$ext")
            counter++
        }
        return candidate
    }

    companion object {
        const val AH_MESH_FOLDER_NAME = "AH Mesh"

        fun sanitizeFileName(raw: String): String {
            val cleaned = raw
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .trim()
                .trimStart('.')
            return if (cleaned.isBlank()) "ahmesh_file_${System.currentTimeMillis()}.bin" else cleaned.take(120)
        }

        fun resolveMimeType(fileName: String, candidateMime: String): String {
            if (candidateMime.isNotBlank() && candidateMime != "application/octet-stream") {
                return candidateMime
            }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext == "apk") return "application/vnd.android.package-archive"
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }

        fun classifyMessageType(fileName: String, mimeType: String): MessageType {
            val lowerName = fileName.lowercase()
            val lowerMime = mimeType.lowercase()
            return when {
                lowerName.endsWith(".apk") || lowerMime == "application/vnd.android.package-archive" ->
                    MessageType.APK
                lowerMime.startsWith("image/") -> MessageType.IMAGE
                lowerMime.startsWith("video/") -> MessageType.VIDEO
                else -> MessageType.FILE
            }
        }
    }
}
