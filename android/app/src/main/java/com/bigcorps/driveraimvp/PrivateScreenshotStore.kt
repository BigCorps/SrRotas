package com.srrotas.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Backup local das ofertas reconhecidas.
 *
 * 0.33 mantém o recorte diagnóstico já validado, mas passa a aplicar um gate
 * semântico antes do I/O: frames repetidos da mesma oferta não geram novos
 * arquivos. A qualidade JPEG e a retenção da cópia visível também são limitadas
 * para impedir crescimento indefinido do armazenamento do aparelho.
 */
object PrivateScreenshotStore {
    private const val MAX_PRIVATE_FILES = 30
    private const val MAX_VISIBLE_FILES = 180
    private const val JPEG_QUALITY = 72
    private const val PUBLIC_RELATIVE_PATH = "Pictures/SrRotas/Ofertas"

    private fun privateDir(context: Context) =
        File(context.filesDir, "private-offer-captures").apply { mkdirs() }

    fun save(context: Context, bitmap: Bitmap, offer: RideOffer) {
        val candidate = ScreenshotStorageGuard033.Candidate(
            platform = offer.platform,
            fare = offer.fare,
            pickupKm = offer.pickupKm,
            tripKm = offer.tripKm,
            totalKm = offer.totalKm,
        )
        if (!ScreenshotStorageGuard033.allow(candidate)) return

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
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) {
                    "Falha ao compactar screenshot privado"
                }
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
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                            "Falha ao compactar screenshot"
                        }
                    } ?: error("MediaStore sem stream")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    pruneVisibleMediaStore(context)
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
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) {
                        "Falha ao compactar screenshot"
                    }
                }
                folder.listFiles()
                    ?.filter(File::isFile)
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(MAX_VISIBLE_FILES)
                    ?.forEach(File::delete)
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

    private fun pruneVisibleMediaStore(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$PUBLIC_RELATIVE_PATH%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var index = 0
            while (cursor.moveToNext()) {
                if (index++ < MAX_VISIBLE_FILES) continue
                val id = cursor.getLong(idIndex)
                runCatching {
                    resolver.delete(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        null,
                        null,
                    )
                }
            }
        }
    }

    private fun visibleCount(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return 0
            return File(base, "SrRotas/Ofertas").listFiles()?.count(File::isFile) ?: 0
        }
        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("$PUBLIC_RELATIVE_PATH%"),
                null,
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
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

    fun toJson(context: Context): JSONObject = ScreenshotStorageGuard033.toJson().apply {
        put("private_files", count(context))
        put("private_limit", MAX_PRIVATE_FILES)
        put("visible_files", visibleCount(context))
        put("visible_limit", MAX_VISIBLE_FILES)
        put("jpeg_quality", JPEG_QUALITY)
        put("crop_preserved", true)
        put("public_path", PUBLIC_RELATIVE_PATH)
    }

    /** Limpa apenas o cache técnico privado. Fotos visíveis permanecem intactas. */
    fun clear(context: Context) {
        privateDir(context).listFiles()?.forEach(File::delete)
    }
}
