package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Native decoder proves displayed pixels come from the exact hashed bytes even if the path changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerifiedCaptureSnapshotTest {
    private fun png(color: Int, width: Int = 8, height: Int = 8): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { out ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); bitmap.recycle(); out.toByteArray()
        }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun file() = File.createTempFile("view-snapshot-", ".png", RuntimeEnvironment.getApplication<Application>().cacheDir)

    @Test fun replacementAfterVerificationCannotChangeDecodedPixelsOrDigest() {
        val original = png(Color.RED); val replacement = png(Color.BLUE)
        val path = file().apply { writeBytes(original) }
        val snapshot = VerifiedCaptureSnapshotV1.read(path, sha(original)).snapshot!!
        try {
            path.delete(); path.writeBytes(replacement)
            snapshot.use {
                val image = it.decodeBounded()!!
                assertEquals(Color.RED, image.getPixel(0, 0)); assertEquals(sha(original), it.sha256)
                assertNotEquals(sha(replacement), it.sha256); image.recycle()
            }
            assertNull(snapshot.decodeBounded()) // released immediately; no retained copy for later viewing
        } finally { snapshot.close(); path.delete() }
    }
    @Test fun substitutionInTheGapBeforeSnapshotIsRehashedAndRejected() {
        val original = png(Color.RED)
        val path = file().apply { writeBytes(original) }
        val previouslyVerifiedDigest = sha(original)
        path.writeBytes(png(Color.BLUE))
        try {
            val result = VerifiedCaptureSnapshotV1.read(path, previouslyVerifiedDigest)
            assertNull(result.snapshot); assertEquals(VerifiedCaptureSnapshotV1.Failure.CHANGED, result.failure)
        } finally { path.delete() }
    }
    @Test fun deletionAfterSnapshotDoesNotReopenOrFallBackToAnotherFile() {
        val original = png(Color.GREEN); val path = file().apply { writeBytes(original) }
        val snapshot = VerifiedCaptureSnapshotV1.read(path, sha(original)).snapshot!!
        path.delete()
        snapshot.use { val image = it.decodeBounded()!!; assertEquals(Color.GREEN, image.getPixel(0, 0)); image.recycle() }
        assertNull(VerifiedCaptureSnapshotV1.read(path, sha(original)).snapshot)
    }
    @Test fun encodedLimitAndMissingDigestFailClosedBeforeLargeAllocation() {
        val path = file()
        try {
            RandomAccessFile(path, "rw").use { it.setLength(VerifiedCaptureSnapshotV1.MAX_ENCODED_BYTES.toLong() + 1) }
            assertEquals(VerifiedCaptureSnapshotV1.Failure.TOO_LARGE, VerifiedCaptureSnapshotV1.read(path, "a".repeat(64)).failure)
            assertNull(VerifiedCaptureSnapshotV1.read(path, "").snapshot)
        } finally { path.delete() }
    }
    @Test fun decodedImageStaysBoundedAndNoSourceBytesOrBitmapAreWritten() {
        val original = png(Color.RED, 4096, 512); val path = file().apply { writeBytes(original) }
        val before = path.parentFile!!.list()!!.toSet()
        try {
            VerifiedCaptureSnapshotV1.read(path, sha(original)).snapshot!!.use {
                val image = it.decodeBounded()!!
                assertTrue(image.width <= 2048 && image.height <= 2048); image.recycle()
            }
            assertArrayEquals(original, path.readBytes()); assertEquals(before, path.parentFile!!.list()!!.toSet())
        } finally { path.delete() }
    }
}
