package com.srrotas.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/** Ephemeral view-only snapshot. Never decodes a path after verification or writes a retained copy. */
internal class VerifiedCaptureSnapshotV1 private constructor(
    private var encoded: ByteArray?, val sha256: String, val byteCount: Int,
) : Closeable {
    enum class Failure { TOO_LARGE, CHANGED, UNREADABLE }
    data class ReadResult(val snapshot: VerifiedCaptureSnapshotV1?, val failure: Failure? = null)

    companion object {
        const val MAX_ENCODED_BYTES = 8 * 1024 * 1024
        const val MAX_DISPLAY_SIDE = 2048

        fun read(file: File, expectedSha256: String): ReadResult {
            var buffer: ByteArray? = null
            var accepted = false
            try {
                if (!expectedSha256.matches(Regex("[0-9a-f]{64}"))) return ReadResult(null, Failure.CHANGED)
                file.inputStream().use { input ->
                    // Size belongs to this open descriptor; replacement of the path cannot swap its contents.
                    val size = input.channel.size()
                    if (size > MAX_ENCODED_BYTES) return ReadResult(null, Failure.TOO_LARGE)
                    if (size <= 0) return ReadResult(null, Failure.UNREADABLE)
                    val bytes = ByteArray(size.toInt())
                    buffer = bytes
                    var offset = 0
                    while (offset < bytes.size) {
                        val n = input.read(bytes, offset, bytes.size - offset)
                        if (n < 0) return ReadResult(null, Failure.CHANGED)
                        offset += n
                    }
                    // Detect growth as well as truncation; never allocate based on an unbounded readBytes().
                    if (input.read() != -1) return ReadResult(null, Failure.CHANGED)
                    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    if (actual != expectedSha256) return ReadResult(null, Failure.CHANGED)
                    accepted = true
                    return ReadResult(VerifiedCaptureSnapshotV1(bytes, actual, bytes.size))
                }
            } catch (_: Exception) { return ReadResult(null, Failure.UNREADABLE) }
            finally { if (!accepted) buffer?.fill(0) }
        }
    }

    /** Both bounds and pixels come from the same private bytes, not a reopened file. */
    @Synchronized fun decodeBounded(): Bitmap? {
        val bytes = encoded ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DISPLAY_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
        })?.takeIf { bitmap ->
            (bitmap.width <= MAX_DISPLAY_SIDE && bitmap.height <= MAX_DISPLAY_SIDE).also { if (!it) bitmap.recycle() }
        }
    }

    /** Byte snapshot is released immediately after decode, not retained for the lifetime of the viewer. */
    @Synchronized override fun close() { encoded?.fill(0); encoded = null }
}
