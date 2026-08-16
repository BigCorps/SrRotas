package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

object PrivateScreenshotStore {
    private const val MAX_FILES=30
    private fun dir(context:Context)=File(context.filesDir,"private-offer-captures").apply{mkdirs()}
    fun save(context:Context,bitmap:Bitmap,offer:RideOffer){runCatching{
        val folder=dir(context);val safe=Instant.now().toString().replace(':','-')
        FileOutputStream(File(folder,"${safe}_${offer.localId.take(8)}.jpg")).use{bitmap.compress(Bitmap.CompressFormat.JPEG,82,it)}
        folder.listFiles()?.sortedByDescending{it.lastModified()}?.drop(MAX_FILES)?.forEach(File::delete)
    }.onFailure{LocalLog.append(context,"Falha ao salvar captura privada: ${it.message}")}}
    fun count(context:Context)=dir(context).listFiles()?.count{it.isFile}?:0
    fun clear(context:Context){dir(context).listFiles()?.forEach(File::delete)}
}
