package com.ahmesh.app.data.pairing

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageProxy
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object QrCodeHelper {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = Color.parseColor("#0B0E14"),
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    fun createExportableQrBitmap(
        content: String,
        sizePx: Int = 720,
        paddingPx: Int = 48
    ): Bitmap? {
        val qrBitmap = generateQrBitmap(content = content, sizePx = sizePx) ?: return null
        return runCatching {
            val exported = Bitmap.createBitmap(
                qrBitmap.width + paddingPx * 2,
                qrBitmap.height + paddingPx * 2,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(exported)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(qrBitmap, paddingPx.toFloat(), paddingPx.toFloat(), null)
            exported
        }.also {
            qrBitmap.recycle()
        }.getOrNull()
    }

    fun buildGalleryImageFileName(nodeId: String): String {
        val safeNode = nodeId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "node" }
        return "ahmesh_qr_${safeNode}_${System.currentTimeMillis()}.png"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveQrCodeToGallery(
        context: Context,
        qrPayloadString: String,
        nodeId: String
    ): Uri? = withContext(Dispatchers.IO) {
        val exported = createExportableQrBitmap(content = qrPayloadString) ?: return@withContext null
        val resolver = context.contentResolver
        val fileName = buildGalleryImageFileName(nodeId)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AH Mesh")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        if (itemUri == null) {
            exported.recycle()
            return@withContext null
        }

        return@withContext try {
            val written = resolver.openOutputStream(itemUri)?.use { stream ->
                exported.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: false

            if (!written) {
                resolver.delete(itemUri, null, null)
                null
            } else {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
                itemUri
            }
        } catch (_: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            null
        } finally {
            exported.recycle()
        }
    }

    suspend fun writeQrCodeToUri(
        context: Context,
        targetUri: Uri,
        qrPayloadString: String
    ): Boolean = withContext(Dispatchers.IO) {
        val exported = createExportableQrBitmap(content = qrPayloadString) ?: return@withContext false
        return@withContext try {
            context.contentResolver.openOutputStream(targetUri)?.use { stream ->
                exported.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: false
        } catch (_: Throwable) {
            false
        } finally {
            exported.recycle()
        }
    }

    fun exportQrCodeImageUri(
        context: Context,
        content: String,
        nodeId: String
    ): Uri? {
        val exported = createExportableQrBitmap(content = content) ?: return null
        return runCatching {
            try {
                val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
                val safeNode = nodeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val outFile = File(sharedDir, "ahmesh_pairing_${safeNode}.png")
                FileOutputStream(outFile).use { stream ->
                    exported.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
            } finally {
                exported.recycle()
            }
        }.getOrNull()
    }

    fun buildShareQrImageIntent(
        context: Context,
        imageUri: Uri,
        chooserTitle: String,
        shareSubject: String = chooserTitle
    ): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
            clipData = ClipData.newUri(context.contentResolver, shareSubject, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun sharePublicKeyQrImage(
        context: Context,
        qrPayloadString: String,
        nodeId: String,
        chooserTitle: String
    ): Boolean {
        val imageUri = withContext(Dispatchers.IO) {
            exportQrCodeImageUri(
                context = context,
                content = qrPayloadString,
                nodeId = nodeId
            )
        } ?: return false

        val chooserIntent = buildShareQrImageIntent(
            context = context,
            imageUri = imageUri,
            chooserTitle = chooserTitle
        )
        return withContext(Dispatchers.Main.immediate) {
            runCatching {
                context.startActivity(chooserIntent)
                true
            }.getOrDefault(false)
        }
    }

    fun decodeQrFromImageProxy(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val data = extractLuminanceBytes(
            buffer = buffer,
            width = image.width,
            height = image.height,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride
        )

        val reader = QRCodeReader()
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )

        return decodeLuminance(reader, data, image.width, image.height, hints)
            ?: decodeLuminance(
                reader = reader,
                data = rotate90Clockwise(data, image.width, image.height),
                width = image.height,
                height = image.width,
                hints = hints
            )
    }

    private fun decodeLuminance(
        reader: QRCodeReader,
        data: ByteArray,
        width: Int,
        height: Int,
        hints: Map<DecodeHintType, Any>
    ): String? {
        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decode(bitmap, hints).text
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun extractLuminanceBytes(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        buffer.rewind()
        if (pixelStride == 1 && rowStride == width && buffer.remaining() >= width * height) {
            return ByteArray(width * height).also { buffer.get(it) }
        }
        val output = ByteArray(width * height)
        val rowData = ByteArray(rowStride)
        for (y in 0 until height) {
            val bytesToRead = minOf(rowStride, buffer.remaining())
            if (bytesToRead <= 0) break
            buffer.get(rowData, 0, bytesToRead)
            val destOffset = y * width
            if (pixelStride == 1) {
                System.arraycopy(rowData, 0, output, destOffset, minOf(width, bytesToRead))
            } else {
                for (x in 0 until width) {
                    val srcIndex = x * pixelStride
                    if (srcIndex < bytesToRead) {
                        output[destOffset + x] = rowData[srcIndex]
                    }
                }
            }
        }
        return output
    }

    private fun rotate90Clockwise(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - 1 - y)] = data[y * width + x]
            }
        }
        return rotated
    }
}
