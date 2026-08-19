package com.srrotas.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class JourneyActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DOING_RIDE = "com.srrotas.app.journey.DOING_RIDE"
        const val ACTION_COMPLETE_RIDE = "com.srrotas.app.journey.COMPLETE_RIDE"
        const val ACTION_CANCEL_RIDE = "com.srrotas.app.journey.CANCEL_RIDE"
        const val ACTION_PAUSE = "com.srrotas.app.journey.PAUSE"
        const val ACTION_RESUME = "com.srrotas.app.journey.RESUME"
        const val ACTION_END = "com.srrotas.app.journey.END"
        const val EXTRA_LOCAL_OFFER_ID = "local_offer_id"

        fun pendingIntent(context: Context, action: String, requestCode: Int, localOfferId: String? = null): PendingIntent {
            val intent = Intent(context, JourneyActionReceiver::class.java).apply {
                this.action = action
                if (!localOfferId.isNullOrBlank()) putExtra(EXTRA_LOCAL_OFFER_ID, localOfferId)
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_DOING_RIDE -> intent.getStringExtra(EXTRA_LOCAL_OFFER_ID)?.let { JourneyCoordinator.markDoingRide(context, it, "notification") }
            ACTION_COMPLETE_RIDE -> JourneyCoordinator.completeCurrentRide(context, "notification")
            ACTION_CANCEL_RIDE -> JourneyCoordinator.cancelCurrentRide(context, "notification")
            ACTION_PAUSE -> JourneyCoordinator.pauseJourney(context)
            ACTION_RESUME -> JourneyCoordinator.resumeJourney(context)
            ACTION_END -> JourneyCoordinator.endJourney(context, "notification_end")
        }
    }
}
