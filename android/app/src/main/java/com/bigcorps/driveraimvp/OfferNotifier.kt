package com.srrotas.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

object OfferNotifier {
    private const val CHANNEL="sr_rotas_offers"
    private var tts:TextToSpeech?=null
    private var ttsReady=false

    fun notify(context:Context,offer:RideOffer){
        val s=SettingsRepository(context).load()
        if(s.textNotificationEnabled)post(context,offer)
        if(s.voiceNotificationEnabled)speak(context,offer)
    }

    private fun post(context:Context,o:RideOffer){
        val nm=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)nm.createNotificationChannel(NotificationChannel(CHANNEL,"Ofertas Sr. Rotas",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Resumo opcional das ofertas reconhecidas."})
        val pending=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Sr. Rotas • ${when(o.verdict){"boa"->"BOA";"ruim"->"RUIM";else->"ATENÇÃO"}}").setContentText(OfferParser.humanSummary(o).replace('\n',' ')).setStyle(Notification.BigTextStyle().bigText(OfferParser.humanSummary(o))).setAutoCancel(true).setContentIntent(pending).build()
        runCatching { nm.notify((o.localId.hashCode() and 0x7fffffff)%100000+3000,n) }
            .onFailure { LocalLog.append(context, "Notificação de oferta falhou: ${it.message}") }
    }

    private fun speak(context:Context,o:RideOffer){
        val text=buildString{append(when(o.verdict){"boa"->"Oferta boa. ";"ruim"->"Oferta abaixo da meta. ";else->"Oferta atenção. "});o.perKm?.let{append(String.format(Locale("pt","BR"),"%.2f reais por quilômetro. ",it))};o.perHour?.let{append(String.format(Locale("pt","BR"),"%.0f reais por hora. ",it))}}
        val current=tts
        if(current!=null&&ttsReady){current.speak(text,TextToSpeech.QUEUE_FLUSH,null,"sr-rotas-offer");return}
        tts=TextToSpeech(context.applicationContext){status->ttsReady=status==TextToSpeech.SUCCESS;if(ttsReady){tts?.language=Locale("pt","BR");tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"sr-rotas-offer")}}
    }
}
