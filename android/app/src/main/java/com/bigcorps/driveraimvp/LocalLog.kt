package com.bigcorps.driveraimvp

import android.content.Context
import java.io.File
import java.time.Instant

object LocalLog {
    private const val FILE = "diagnostic.log"

    @Synchronized
    fun append(context: Context, message: String) {
        runCatching {
            val f = File(context.filesDir, FILE)
            f.appendText("${Instant.now()} $message\n")
            if (f.length() > 500_000) {
                val lines = f.readLines().takeLast(500)
                f.writeText(lines.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun tail(context: Context, maxLines: Int = 80): String = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readLines()?.takeLast(maxLines)?.joinToString("\n") ?: ""
    }.getOrDefault("")
}
