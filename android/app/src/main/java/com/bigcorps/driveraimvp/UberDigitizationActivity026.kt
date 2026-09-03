package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class UberDigitizationActivity026 : Activity() {
    companion object {
        const val EXTRA_MODE="mode"; const val MODE_CHOOSER="chooser"; const val ACTION_RESULT="com.srrotas.app.UBER_DIGITIZATION_RESULT"; const val EXTRA_TEXT="text"; private const val REQ=6207
        fun open(context: Context, mode:String=MODE_CHOOSER){ context.startActivity(Intent(context,UberDigitizationActivity026::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_MODE,mode)) }
    }
    private var mode=MODE_CHOOSER
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){ val text=i?.getStringExtra(EXTRA_TEXT).orEmpty(); if(text.isBlank()){toast("Não foi possível ler a tela.");finish();return}; preview(text) }}
    override fun onCreate(b:Bundle?){super.onCreate(b); mode=intent.getStringExtra(EXTRA_MODE)?:MODE_CHOOSER; registerReceiverCompat(); if(mode==MODE_CHOOSER) choose() else requestCapture()}
    override fun onDestroy(){runCatching{unregisterReceiver(receiver)};super.onDestroy()}
    private fun choose(){AlertDialog.Builder(this).setTitle("Digitalizar Uber").setItems(arrayOf("Resumo da sessão / offline","Histórico de corridas concluídas")){_,w->mode=if(w==0)UberDigitizationParser026.MODE_SESSION else UberDigitizationParser026.MODE_HISTORY;requestCapture()}.setNegativeButton("Cancelar"){_,_->finish()}.setOnCancelListener{finish()}.show()}
    private fun requestCapture(){val mgr=getSystemService(MediaProjectionManager::class.java); @Suppress("DEPRECATION") startActivityForResult(mgr.createScreenCaptureIntent(),REQ)}
    @Deprecated("Compatibilidade sem AndroidX") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d); if(r!=REQ)return; if(c!=RESULT_OK||d==null){finish();return}; val s=Intent(this,UberDigitizationCaptureService026::class.java).putExtra(UberDigitizationCaptureService026.EXTRA_RESULT_CODE,c).putExtra(UberDigitizationCaptureService026.EXTRA_RESULT_DATA,d).putExtra(EXTRA_MODE,mode); if(Build.VERSION.SDK_INT>=26)startForegroundService(s) else startService(s)}
    private fun preview(raw:String){ val parsed=runCatching{UberDigitizationParser026.parse(mode,raw)}.getOrElse{toast(it.message?:"Tela não reconhecida.");finish();return}; val message=when(parsed){is UberDigitizationResult026.Session->{val v=parsed.value;"Ganhos: ${v.earnings?.let{"R$ %.2f".format(it)}?:"—"}\nCorridas concluídas: ${v.completedTrips?:"—"}\nOfertas/solicitações: ${v.offeredTrips?:"—"}\nConfiança: ${(v.confidence*100).toInt()}%"}; is UberDigitizationResult026.Rides->parsed.values.joinToString("\n"){"${it.serviceType} · R$ %.2f".format(it.fare)}+"\n\n${parsed.values.size} corrida(s) identificada(s)."}; AlertDialog.Builder(this).setTitle("Confirmar digitalização").setMessage(message).setPositiveButton("Salvar"){_,_->save(parsed)}.setNegativeButton("Cancelar"){_,_->finish()}.setOnCancelListener{finish()}.show() }
    private fun save(parsed:UberDigitizationResult026){val store=UberDigitizationStore026.get(this); val text=when(parsed){is UberDigitizationResult026.Session->{if(store.saveSession(parsed.value))"Resumo salvo." else "Este resumo já havia sido salvo."}; is UberDigitizationResult026.Rides->{val(r,d)=store.saveRides(parsed.values);"$r corrida(s) salva(s) · $d duplicada(s)."}}; UberDigitizationClient026.sync(this,parsed); toast(text);finish()}
    @Suppress("DEPRECATION")
    private fun registerReceiverCompat(){
        val f=IntentFilter(ACTION_RESULT)
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver,f)
    }
    private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
}
