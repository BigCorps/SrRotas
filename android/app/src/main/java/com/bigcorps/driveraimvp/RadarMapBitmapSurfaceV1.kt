package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.math.ln
import kotlin.math.roundToInt

/** Camada visual da cartografia MapLibre, somente memória. O MapView continua recebendo gestos. */
internal class RadarMapBitmapSurfaceV1(
    context: Context,
    private val styleUri: String,
    private val ready: () -> Boolean,
    private val event: (String) -> Unit,
    private val visible: () -> Unit,
    private val unavailable: () -> Unit,
) : ImageView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var liveAllowedCamera = false
    private var generation = 0L
    private var livePending = false
    private var released = false
    private var snapshotter: MapSnapshotter? = null
    private var debounce: Runnable? = null
    private var timeout: Runnable? = null
    private var image: Bitmap? = null
    init {
        scaleType = ScaleType.FIT_XY
        isClickable = false
        isFocusable = false
        visibility = INVISIBLE // permanece medido antes do primeiro snapshot
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun request(map: MapLibreMap?, state: RadarMiniMapViewV1.State, liveAllowed: Boolean = true) {
        if (released) return
        cancelWork()
        liveAllowedCamera = liveAllowed
        val token = generation
        debounce = Runnable { if (valid(token)) {
            if (map != null && liveAllowed) live(map, state, token)
            else { event("snapshot_error"); independent(map, state, token) }
        } }.also { handler.postDelayed(it, 450L) }
    }
    private fun valid(token: Long) = !released && token == generation && ready() &&
        isAttachedToWindow && width > 0 && height > 0
    private fun healthy(bitmap: Bitmap?) = bitmap != null && !bitmap.isRecycled && runCatching {
        RadarMapBitmapHealthV1.nonblank(bitmap.width, bitmap.height, bitmap::getPixel)
    }.getOrDefault(false)
    private fun live(map: MapLibreMap, state: RadarMiniMapViewV1.State, token: Long) {
        livePending = true
        event("live_snapshot_requested")
        armTimeout(3_000L) {
            if (valid(token) && livePending) {
                livePending = false
                event("snapshot_timeout")
                independent(map, state, token)
            }
        }
        runCatching {
            map.snapshot { bitmap ->
                handler.post {
                    if (!valid(token) || !livePending) return@post
                    livePending = false
                    cancelTimeout()
                    if (healthy(bitmap)) {
                        event("live_snapshot_success")
                        showBitmap(bitmap)
                    } else {
                        event("live_snapshot_blank")
                        event("snapshot_blank")
                        independent(map, state, token)
                    }
                }
            }
        }.onFailure {
            if (valid(token) && livePending) {
                livePending = false; cancelTimeout(); event("snapshot_error")
                independent(map, state, token)
            }
        }
    }
    private fun independent(map: MapLibreMap?, state: RadarMiniMapViewV1.State, token: Long) {
        if (!valid(token)) return
        snapshotter?.cancel(); snapshotter = null
        event("snapshotter_requested")
        val factor = minOf(1.0, 1024.0 / maxOf(width, height).coerceAtLeast(1))
        val w = (width * factor).roundToInt().coerceAtLeast(1)
        val h = (height * factor).roundToInt().coerceAtLeast(1)
        // Mesmo enquadramento em resolução limitada: zoom compensa a redução de pixels.
        val camera = if (map != null && liveAllowedCamera) CameraPosition.Builder(map.cameraPosition)
            .zoom(map.cameraPosition.zoom + ln(factor) / ln(2.0)).build()
        else CameraPosition.Builder().target(LatLng(state.centerLat, state.centerLng))
            .zoom(13.5 + ln(factor) / ln(2.0)).build()
        runCatching {
            val task = MapSnapshotter(context.applicationContext, MapSnapshotter.Options(w, h)
                .withStyle(styleUri).withCameraPosition(camera).withPixelRatio(1f))
            snapshotter = task
            armTimeout(12_000L) { if (valid(token) && snapshotter === task) failure("snapshotter_timeout") }
            task.start({ snapshot -> handler.post {
                if (!valid(token) || snapshotter !== task) return@post
                cancelTimeout(); snapshotter = null
                if (!healthy(snapshot.bitmap)) { task.cancel(); failure("snapshot_blank"); return@post }
                val decorated = runCatching { decorate(snapshot, state) }.getOrNull()
                task.cancel()
                if (decorated == null) { failure("snapshotter_error"); return@post }
                event("snapshotter_success")
                showBitmap(decorated)
            } }, { _ -> handler.post { if (valid(token) && snapshotter === task) failure("snapshotter_error") } })
        }.onFailure { if (valid(token)) failure("snapshotter_error") }
    }
    private fun decorate(snapshot: MapSnapshot, state: RadarMiniMapViewV1.State): Bitmap {
        val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun dot(lat: Double, lng: Double, color: Int, size: Float) {
            val point = snapshot.pixelForLatLng(LatLng(lat, lng))
            paint.color = Color.WHITE; canvas.drawCircle(point.x, point.y, size + 2, paint)
            paint.color = color; canvas.drawCircle(point.x, point.y, size, paint)
        }
        dot(state.centerLat, state.centerLng, Color.rgb(229,57,53), 9f)
        state.markers.forEach { dot(it.lat, it.lng, Color.rgb(37,99,235), if(it.id == state.selectedId) 9f else 6f) }
        return bitmap
    }
    private fun showBitmap(bitmap: Bitmap) {
        // Não reciclar bitmap entregue pelo SDK; ImageView substitui a única referência retida.
        val factor = minOf(1.0, 1024.0 / maxOf(bitmap.width, bitmap.height))
        val display = if (factor < 1.0) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * factor).roundToInt().coerceAtLeast(1),
            (bitmap.height * factor).roundToInt().coerceAtLeast(1), true) else bitmap
        image = display
        setImageBitmap(display)
        visibility = VISIBLE
        event("visible_bitmap_shown")
        visible()
    }
    private fun failure(reason: String) {
        cancelTimeout(); snapshotter?.cancel(); snapshotter = null
        event("snapshotter_failed"); event(reason)
        setImageDrawable(null); image = null; visibility = INVISIBLE
        event("fallback_text_shown"); unavailable()
    }
    private fun armTimeout(delay: Long, action: () -> Unit) {
        cancelTimeout()
        timeout = Runnable(action).also { handler.postDelayed(it, delay) }
    }
    private fun cancelTimeout() { timeout?.let(handler::removeCallbacks); timeout = null }
    fun cancelWork() {
        generation++
        livePending = false
        debounce?.let(handler::removeCallbacks); debounce = null
        cancelTimeout(); snapshotter?.cancel(); snapshotter = null
    }
    fun release() {
        released = true; cancelWork()
        setImageDrawable(null); image = null; visibility = GONE
    }
    override fun onDetachedFromWindow() { cancelWork(); super.onDetachedFromWindow() }
}
