package com.srrotas.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.InputStream

object ScreenshotRescanImageV1 {
    const val MAX_BYTES = 16 * 1024 * 1024
    fun decode(stream: InputStream): Bitmap {
        val bytes = stream.use { it.readBytesBounded() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong()*bounds.outHeight <= 40_000_000L) { "Imagem inválida ou grande demais (máximo 40 MP)." }
        var sample = 1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample > 1800) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
        val matrix = Matrix()
        when(orientation) {
            2 -> matrix.setScale(-1f,1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f,-1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }
            8 -> matrix.setRotate(-90f)
        }
        if(matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also { if(it !== bitmap) bitmap.recycle() }
    }
    private fun InputStream.readBytesBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while(true) {
            val n = read(buffer)
            if(n < 0) break
            require(out.size()+n <= MAX_BYTES) { "Imagem excede 16 MB." }
            out.write(buffer,0,n)
        }
        return out.toByteArray()
    }
}
