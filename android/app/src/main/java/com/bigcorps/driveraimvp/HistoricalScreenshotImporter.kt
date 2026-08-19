package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object HistoricalScreenshotImporter {
    private const val MAX_LONG_EDGE = 2100
    private val executor = Executors.newSingleThreadExecutor()

    fun import(
        context: Context,
        uris: List<Uri>,
        onProgress: (HistoricalImportProgress) -> Unit,
        onResult: (Result<HistoricalImportResult>) -> Unit,
    ) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                require(uris.isNotEmpty()) { "Selecione pelo menos uma imagem." }

                val repo = SettingsRepository(app)
                require(!repo.isProjectionActive() && repo.currentJourneyId().isBlank()) {
                    "Encerre a jornada antes de importar screenshots. A importação não deve competir com o OCR ao vivo."
                }

                val settings = repo.load()
                val importStore = HistoricalImportStore.get(app)
                val localStore = LocalStore.get(app)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                var processedFiles = 0
                var skippedFiles = 0
                var noOfferFiles = 0
                var failedFiles = 0
                var importedOffers = 0
                var duplicateOffers = 0

                try {
                    uris.distinctBy(Uri::toString).forEachIndexed { index, uri ->
                        val displayName = displayName(app, uri)
                        postProgress(
                            onProgress,
                            HistoricalImportProgress(
                                current = index + 1,
                                total = uris.size,
                                displayName = displayName,
                                importedOffers = importedOffers,
                                duplicateOffers = duplicateOffers,
                            ),
                        )

                        val sha = runCatching { sha256(app, uri) }.getOrNull()
                        if (sha.isNullOrBlank()) {
                            failedFiles++
                            return@forEachIndexed
                        }

                        if (importStore.isAlreadyProcessed(sha)) {
                            skippedFiles++
                            return@forEachIndexed
                        }

                        val sourceTime = HistoricalImportTime.resolve(app, uri, displayName)
                        val bitmap = decode(app, uri)

                        if (bitmap == null) {
                            failedFiles++
                            importStore.record(
                                sha, displayName, sourceTime.observedAt, sourceTime.confidence.wire,
                                "failed", 0, 0, 0, "Não foi possível decodificar a imagem.",
                            )
                            return@forEachIndexed
                        }

                        try {
                            val text = Tasks.await(
                                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                                20,
                                TimeUnit.SECONDS,
                            )

                            val parsed = SpatialOfferParser.parse(
                                result = text,
                                sourcePackage = AppSignals.UBER_PACKAGE,
                                captureMethod = "historical-import/${sourceTime.confidence.wire}",
                                settings = settings,
                                frameWidth = bitmap.width,
                                frameHeight = bitmap.height,
                            )

                            if (parsed.isEmpty()) {
                                noOfferFiles++
                                importStore.record(
                                    sha, displayName, sourceTime.observedAt, sourceTime.confidence.wire,
                                    "no_offer", 0, 0, 0,
                                )
                                return@forEachIndexed
                            }

                            var savedInFile = 0
                            var duplicatesInFile = 0

                            parsed.forEach { current ->
                                val historicalContext = current.context?.copy(
                                    estimatedArrivalAt = OfferContextEngine.estimatedArrivalAt(
                                        sourceTime.observedAt,
                                        current.totalMinutes,
                                    ),
                                    sourceType = "historical_screenshot",
                                    timeSource = "historical_${sourceTime.confidence.wire}",
                                )

                                val baseOffer = current.copy(context = historicalContext)
                                val normalized = baseOffer.copy(
                                    localId = UUID.randomUUID().toString(),
                                    journeyId = null,
                                    observedAt = sourceTime.observedAt,
                                    captureMethod = "historical-import/${sourceTime.confidence.wire}",
                                    dedupeKey = HistoricalImportSemanticKey.key(
                                        baseOffer,
                                        sourceTime.observedAt,
                                        sha,
                                        sourceTime.confidence,
                                    ),
                                )

                                if (localStore.saveOffer(normalized)) {
                                    savedInFile++
                                    importedOffers++
                                    BackendClient.sendOffer(app, normalized)

                                    val ctx = normalized.context
                                    if (ctx?.hasTextContext() == true && ctx.geocodeStatus == "pending") {
                                        OfferContextGeocoder.enrichAsync(app, normalized) { resolved ->
                                            localStore.saveOrUpdateContext(
                                                normalized.localId,
                                                resolved,
                                                syncState = 0,
                                            )
                                            BackendClient.sendOfferContext(
                                                app,
                                                normalized.localId,
                                                normalized.dedupeKey,
                                                resolved,
                                            )
                                        }
                                    }
                                } else {
                                    duplicatesInFile++
                                    duplicateOffers++
                                }
                            }

                            processedFiles++
                            importStore.record(
                                sha, displayName, sourceTime.observedAt, sourceTime.confidence.wire,
                                "processed", parsed.size, savedInFile, duplicatesInFile,
                            )
                        } catch (e: Exception) {
                            failedFiles++
                            importStore.record(
                                sha, displayName, sourceTime.observedAt, sourceTime.confidence.wire,
                                "failed", 0, 0, 0,
                                e.message ?: e.javaClass.simpleName,
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                } finally {
                    recognizer.close()
                }

                BackendClient.flushPendingOffers(app)

                HistoricalImportResult(
                    selectedFiles = uris.size,
                    processedFiles = processedFiles,
                    skippedFiles = skippedFiles,
                    noOfferFiles = noOfferFiles,
                    failedFiles = failedFiles,
                    importedOffers = importedOffers,
                    duplicateOffers = duplicateOffers,
                )
            }

            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    private fun postProgress(
        callback: (HistoricalImportProgress) -> Unit,
        progress: HistoricalImportProgress,
    ) {
        Handler(Looper.getMainLooper()).post { callback(progress) }
    }

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i < 0) null else c.getString(i)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "imagem"

    private fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Arquivo indisponível." }
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_LONG_EDGE * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) null else BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= MAX_LONG_EDGE) return decoded

        val ratio = MAX_LONG_EDGE.toFloat() / longEdge.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
