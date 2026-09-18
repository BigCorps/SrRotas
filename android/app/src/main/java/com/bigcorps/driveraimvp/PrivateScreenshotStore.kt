package com.srrotas.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Backup local das ofertas reconhecidas.
 * RC3.6 limita a cópia diagnóstica à janela/área conhecida do Uber quando há
 * coordenadas confiáveis. O bitmap do OCR principal nunca é recortado aqui.
 */
object PrivateScreenshotStore {
    private const val MAX_PRIVATE_FILES = 30
    private const val PUBLIC_RELATIVE_PATH = "Pictures/SrRotas/Ofertas"

    private fun privateDir(context: Context) =
        File(context.filesDir, "private-offer-captures").apply { mkdirs() }

    fun save(context: Context, bitmap: Bitmap, offer: RideOffer) {
        val diagnostic = ReaderLab027036.cropDiagnosticBitmap(context, bitmap)
        try {
            savePrivate(context, diagnostic, offer)
            saveVisibleCopy(context, diagnostic, offer)
        } finally {
            if (diagnostic !== bitmap && !diagnostic.isRecycled) diagnostic.recycle()
        }
    }

    private fun savePrivate(context: Context, bitmap: Bitmap, offer: RideOffer) {
        runCatching {
            val folder = privateDir(context)
            FileOutputStream(File(folder, fileName(offer))).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it)
            }
            folder.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_PRIVATE_FILES)
                ?.forEach(File::delete)
        }.onFailure {
            LocalLog.append(context, "Falha ao salvar captura privada: ${it.message}")
        }
    }

    private fun saveVisibleCopy(context: Context, bitmap: Bitmap, offer: RideOffer) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName(offer))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, PUBLIC_RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: error("MediaStore não criou o arquivo")
                try {
                    resolver.openOutputStream(uri)?.use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                            "Falha ao compactar screenshot"
                        }
                    } ?: error("MediaStore sem stream")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (error: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw error
                }
            } else {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: error("Armazenamento externo indisponível")
                val folder = File(base, "SrRotas/Ofertas").apply { mkdirs() }
                val file = File(folder, fileName(offer))
                FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/jpeg"),
                    null,
                )
            }
        }.onFailure {
            LocalLog.append(context, "Falha ao salvar screenshot no aparelho: ${it.message}")
        }
    }

    private fun fileName(offer: RideOffer): String {
        val safe = Instant.now().toString().replace(':', '-').replace('.', '-')
        val platform = offer.platform.lowercase().replace(Regex("[^a-z0-9_-]"), "").ifBlank { "oferta" }
        val method = when {
            offer.captureMethod.startsWith("accessibility") -> "M2"
            offer.captureMethod.startsWith("media-projection") -> "M1"
            else -> "MX"
        }
        return "SrRotas_${method}_${safe}_${platform}_${offer.localId.take(8)}.jpg"
    }

    fun count(context: Context): Int = privateDir(context).listFiles()?.count { it.isFile } ?: 0

    /** Limpa apenas o cache técnico privado. Fotos salvas pelo motorista ficam intactas. */
    fun clear(context: Context) {
        privateDir(context).listFiles()?.forEach(File::delete)
    }
}
