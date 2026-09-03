package com.srrotas.app

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class UberDigitizationCaptureService026 : Service() {
    companion object { const val EXTRA_RESULT_CODE="result_code"; const val EXTRA_RESULT_DATA="result_data"; private const val CHANNEL="sr_uber_digitization"; private const val NOTIF=2607 }
    private var projection:MediaProjection?=null; private var reader:ImageReader?=null; private var done=false
    override fun onBind(i:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        startForegroundCompat()
        val code=intent?.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
        val data=parcelableIntent(intent)
        if(code!=Activity.RESULT_OK||data==null){finish("");return START_NOT_STICKY}
        capture(code,data)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun parcelableIntent(source: Intent?): Intent? =
        if(Build.VERSION.SDK_INT>=33) source?.getParcelableExtra(EXTRA_RESULT_DATA,Intent::class.java)
        else source?.getParcelableExtra(EXTRA_RESULT_DATA)
    private fun capture(code:Int,data:Intent){ val mgr=getSystemService(MediaProjectionManager::class.java); projection=mgr.getMediaProjection(code,data); projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){cleanup()}},Handler(Looper.getMainLooper())); val dm=resources.displayMetrics; val w=dm.widthPixels; val h=dm.heightPixels; reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2); reader?.setOnImageAvailableListener({r->if(done)return@setOnImageAvailableListener; val image=r.acquireLatestImage()?:return@setOnImageAvailableListener; done=true; val plane=image.planes[0]; val buffer=plane.buffer; val pixelStride=plane.pixelStride; val rowStride=plane.rowStride; val rowPadding=rowStride-pixelStride*w; val padded=Bitmap.createBitmap(w+rowPadding/pixelStride,h,Bitmap.Config.ARGB_8888); padded.copyPixelsFromBuffer(buffer); image.close(); val bitmap=Bitmap.createBitmap(padded,0,0,w,h); if(bitmap!==padded)padded.recycle(); ocr(bitmap)},Handler(Looper.getMainLooper())); projection?.createVirtualDisplay("SrRotasUberScan",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null); Handler(Looper.getMainLooper()).postDelayed({if(!done)finish("")},5000) }
    private fun ocr(bitmap:Bitmap){val rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); rec.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{finish(it.text)}.addOnFailureListener{finish("")}.addOnCompleteListener{bitmap.recycle();rec.close()} }
    private fun finish(text:String){sendBroadcast(Intent(UberDigitizationActivity026.ACTION_RESULT).setPackage(packageName).putExtra(UberDigitizationActivity026.EXTRA_TEXT,text));cleanup();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    private fun cleanup(){
        reader?.close(); reader=null
        val current=projection; projection=null
        runCatching { current?.stop() }
    }
    private fun startForegroundCompat(){val nm=getSystemService(NotificationManager::class.java); if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(CHANNEL,"Digitalização da Uber",NotificationManager.IMPORTANCE_LOW)); val n=Notification.Builder(this,if(Build.VERSION.SDK_INT>=26)CHANNEL else "").setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Sr. Rotas").setContentText("Lendo uma tela da Uber…").setOngoing(true).build(); if(Build.VERSION.SDK_INT>=29)startForeground(NOTIF,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(NOTIF,n)}
}
