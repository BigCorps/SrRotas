package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.time.Instant

/** Prévia RC3.5 usa o OverlayController real, com uma oferta fictícia. */
object HudLivePreview027035 {
    fun show(context:Context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context,"Autorize o HUD para visualizar a prévia real.",Toast.LENGTH_SHORT).show()
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val offer=RideOffer(
            observedAt=Instant.now().toString(), sourcePackage="preview.srrotas", captureMethod="preview", rawText="",
            fare=28.40, pickupKm=1.8, tripKm=8.7, totalKm=10.5, pickupMinutes=5, tripMinutes=22, totalMinutes=27,
            perKm=2.70, perHour=63.11, perMinute=1.05, estimatedCost=8.93, estimatedProfit=19.47,
            profitPerHour=43.27, profitPercent=68.5, passengerRating=4.93, advertisedPerKm=null,
            serviceType="uberx", verdict="boa", confidence=0.99, offerType="exclusive",
            context=OfferContext(pickupLabel="Av. Paulista, 1000", destinationLabel="Vila Mariana", contextConfidence=0.99),
            parserVersion="preview-rc3.5", dedupeKey="preview-rc35-${System.currentTimeMillis()}",
        )
        OverlayController(context).show(offer, 15_000L)
        Toast.makeText(context,"Prévia real exibida por 15 segundos. Arraste e avalie no próprio aparelho.",Toast.LENGTH_LONG).show()
    }
}
