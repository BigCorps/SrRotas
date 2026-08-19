package com.srrotas.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper

class JourneyLocationService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.srrotas.app.location.START"
        const val ACTION_PAUSE = "com.srrotas.app.location.PAUSE"
        const val ACTION_RESUME = "com.srrotas.app.location.RESUME"
        const val ACTION_STOP = "com.srrotas.app.location.STOP"
        const val ACTION_OFFER_OBSERVED = "com.srrotas.app.location.OFFER"
        const val ACTION_RIDE_STARTED = "com.srrotas.app.location.RIDE_STARTED"
        const val ACTION_RIDE_FINISHED = "com.srrotas.app.location.RIDE_FINISHED"
        private const val EXTRA_OFFER_ID = "offer_id"
        private const val CHANNEL_ID = "sr_rotas_journey_location"
        private const val NOTIFICATION_ID = 15150

        fun dispatch(context: Context, action: String, offerId: String? = null) {
            val intent = Intent(context, JourneyLocationService::class.java).apply {
                this.action = action
                if (!offerId.isNullOrBlank()) putExtra(EXTRA_OFFER_ID, offerId)
            }
            runCatching {
                if (action == ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure { LocalLog.append(context, "Serviço regional $action falhou: ${it.message}") }
        }
    }

    private lateinit var locationManager: LocationManager
    private lateinit var tracker: RegionalExposureTracker
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        tracker = RegionalExposureTracker(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification())
                JourneyBubbleController.show(this)
                refreshTrackingForState()
            }
            ACTION_PAUSE -> {
                stopLocationUpdates()
                tracker.onPause()
                updateNotification()
                JourneyBubbleController.refresh(this)
            }
            ACTION_RESUME -> {
                updateNotification()
                refreshTrackingForState()
                JourneyBubbleController.refresh(this)
            }
            ACTION_OFFER_OBSERVED -> intent.getStringExtra(EXTRA_OFFER_ID)?.let(tracker::onOfferObserved)
            ACTION_RIDE_STARTED -> {
                stopLocationUpdates()
                tracker.onRideStarted()
                updateNotification()
                JourneyBubbleController.refresh(this)
            }
            ACTION_RIDE_FINISHED -> {
                updateNotification()
                refreshTrackingForState()
                tracker.onRideFinished()
                JourneyBubbleController.refresh(this)
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                tracker.onEnd()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        tracker.onLocation(location)
    }

    @Deprecated("Compatibilidade com APIs antigas")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) {
        if (listening) {
            stopLocationUpdates()
            refreshTrackingForState()
        }
    }

    private fun refreshTrackingForState() {
        val snapshot = JourneyCoordinator.snapshot(this)
        if (snapshot.journeyState != JourneyOperationalState.ACTIVE || snapshot.isDoingRide) {
            stopLocationUpdates()
            return
        }
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (listening) return
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        val provider = providers.firstOrNull()
        if (provider == null) {
            tracker.onLocationUnavailable()
            LocalLog.append(this, "Nenhum provedor de localização disponível para exposição regional.")
            return
        }
        runCatching {
            locationManager.requestLocationUpdates(provider, 30_000L, 120f, this, Looper.getMainLooper())
            listening = true
            locationManager.getLastKnownLocation(provider)?.let(tracker::onLocation)
        }.onFailure {
            listening = false
            tracker.onLocationUnavailable()
            LocalLog.append(this, "Localização regional indisponível: ${it.message}")
        }
    }

    private fun stopLocationUpdates() {
        if (!listening) return
        runCatching { locationManager.removeUpdates(this) }
        listening = false
    }

    private fun notification(): Notification {
        val snapshot = JourneyCoordinator.snapshot(this)
        val paused = snapshot.journeyState == JourneyOperationalState.PAUSED
        val doing = snapshot.isDoingRide
        val title = when {
            doing -> "Sr. Rotas · corrida em andamento"
            paused -> "Sr. Rotas · jornada pausada"
            else -> "Sr. Rotas · jornada ativa"
        }
        val body = when {
            doing -> "Exposição regional pausada enquanto você realiza a corrida."
            paused -> "Toque em Retomar quando quiser voltar a registrar disponibilidade regional."
            else -> "Disponibilidade registrada por região, sem salvar um rastro de GPS segundo a segundo."
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (doing) {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_save, "Finalizar", JourneyActionReceiver.pendingIntent(this, JourneyActionReceiver.ACTION_COMPLETE_RIDE, 15151)).build())
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", JourneyActionReceiver.pendingIntent(this, JourneyActionReceiver.ACTION_CANCEL_RIDE, 15152)).build())
        } else if (paused) {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_play, "Retomar", JourneyActionReceiver.pendingIntent(this, JourneyActionReceiver.ACTION_RESUME, 15153)).build())
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar", JourneyActionReceiver.pendingIntent(this, JourneyActionReceiver.ACTION_END, 15154)).build())
        } else {
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pausar", JourneyActionReceiver.pendingIntent(this, JourneyActionReceiver.ACTION_PAUSE, 15155)).build())
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar", JourneyActionReceiver.pendingIntent(this, JourneyActionReceiver.ACTION_END, 15156)).build())
        }
        return builder.build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jornada e localização regional", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controles da jornada e registro agregado de disponibilidade por região."
                },
            )
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
