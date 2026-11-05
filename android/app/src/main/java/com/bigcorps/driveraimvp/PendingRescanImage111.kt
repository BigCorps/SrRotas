package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * vc111 — imagem escolhida para releitura que ainda não pôde ser processada (corrida em andamento,
 * leitura oficial ocupada). Fica só no armazenamento privado do app e é apagada ao iniciar a
 * releitura ou ao cancelar a fila; nunca vai para a galeria, backend ou diagnóstico.
 */
object PendingRescanImage111 {
    private const val NAME = "rescan_pending_111.jpg"

    fun file(context: Context) = File(context.filesDir, NAME)

    fun save(context: Context, bitmap: Bitmap) = runCatching {
        if (bitmap.isRecycled) return@runCatching
        val tmp = File(context.filesDir, "$NAME.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(file(context))
    }

    fun clear(context: Context) { runCatching { file(context).delete() } }
}
