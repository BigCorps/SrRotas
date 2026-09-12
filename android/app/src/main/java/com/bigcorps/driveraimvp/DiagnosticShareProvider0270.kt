package com.srrotas.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Provider mínimo, somente leitura, para compartilhar o diagnóstico como arquivo.
 * O JSON fica no cache privado e só é exposto por URI temporariamente autorizada.
 */
class DiagnosticShareProvider0270 : ContentProvider() {
    companion object {
        private const val DIRECTORY = "diagnostics"
        private const val FILE_NAME = "sr-rotas-diagnostic.json"

        fun prepare(context: Context, content: String): Uri {
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            val file = File(directory, FILE_NAME)
            file.writeText(content, Charsets.UTF_8)
            return Uri.Builder()
                .scheme("content")
                .authority("${BuildConfig.APPLICATION_ID}.diagnostics")
                .appendPath(FILE_NAME)
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/json"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Somente leitura")
        return ParcelFileDescriptor.open(
            resolve(uri),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = resolve(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(FILE_NAME)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Somente leitura")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Somente leitura")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Somente leitura")

    private fun resolve(uri: Uri): File {
        if (uri.authority != "${BuildConfig.APPLICATION_ID}.diagnostics") {
            throw FileNotFoundException("Autoridade inválida")
        }
        if (uri.lastPathSegment != FILE_NAME) {
            throw FileNotFoundException("Arquivo inválido")
        }
        val app = context ?: throw FileNotFoundException("Provider indisponível")
        val file = File(File(app.cacheDir, DIRECTORY), FILE_NAME)
        if (!file.exists()) throw FileNotFoundException(FILE_NAME)
        return file
    }
}
