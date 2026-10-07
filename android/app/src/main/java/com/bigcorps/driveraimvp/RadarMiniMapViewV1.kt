package com.srrotas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mapa operacional do Radar Contextual.
 *
 * vc89 substitui o canvas abstrato por cartografia real:
 * - MapLibre Native para renderização local;
 * - estilo OpenFreeMap sem API key;
 * - destino + POIs do próprio backend Sr.Rotas;
 * - nenhuma dependência de Google Places/Maps SDK.
 *
 * Se o estilo/tile estiver indisponível, os cards do Radar continuam funcionais
 * e a navegação externa (Maps/Waze) permanece disponível.
 */
@Suppress("DEPRECATION")
class RadarMiniMapViewV1(context: Context) : FrameLayout(context) {
    data class Marker(
        val id: String,
        val lat: Double,
        val lng: Double,
        val title: String,
        val type: String,
        val potential: String,
    )

    data class State(
        val centerLat: Double,
        val centerLng: Double,
        val radiusKm: Double,
        val markers: List<Marker>,
        val selectedId: String? = null,
    )

    companion object {
        private const val STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
    }

    private val density = resources.displayMetrics.density
    private val mapView: MapView
    private val fallback: TextView
    private var map: MapLibreMap? = null
    private val renderGate = RadarMapRenderGateV1()
    private var renderDeadlineMs = 0L
    private val renderTimeout = Runnable {
        if (!released && renderGate.timeout()) {
            RadarContextualDiagnosticV1.mapEvent("map_render_timeout")
            showMapFallback()
        }
    }
    private var styleReady = false
    private var started = false
    private var released = false
    private var state: State? = null
    private val markerIds = mutableMapOf<Long, String>()

    var onMarkerSelected: ((String) -> Unit)? = null

    init {
        MapLibre.getInstance(context.applicationContext)
        RadarContextualDiagnosticV1.mapCreated()

        mapView = MapView(context)
        fallback = TextView(context).apply {
            text = "Carregando mapa do destino…"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(SrUi023.palette(context).muted)
            setBackgroundColor(SrUi023.palette(context).surface)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        addView(
            mapView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            fallback,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        minimumHeight = dp(290)
        contentDescription = "Mapa contextual do destino com oportunidades próximas"

        mapView.onCreate(null)
        mapView.addOnDidFinishLoadingMapListener {
            post { if (!released) RadarContextualDiagnosticV1.mapEvent("map_loading_finished") }
        }
        mapView.addOnDidFinishRenderingFrameListener(MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
            val eligible = styleReady && renderGate.submitted
            post {
                if (eligible && !released && renderGate.frame(renderSurfaceReady())) {
                    RadarContextualDiagnosticV1.mapEvent("map_first_frame")
                }
            }
        })
        mapView.addOnDidFinishRenderingMapListener { fully ->
            // Um evento anterior ao style/destino não vira sucesso só porque post foi executado depois.
            val eligible = styleReady && renderGate.submitted
            post {
                if (eligible && !released && renderGate.frame(renderSurfaceReady())) {
                    RadarContextualDiagnosticV1.mapEvent("map_first_frame")
                }
                if (eligible && !released && renderGate.fully(fully, renderSurfaceReady())) {
                    removeCallbacks(renderTimeout)
                    RadarContextualDiagnosticV1.mapEvent("map_fully_rendered")
                    fallback.visibility = View.GONE
                }
            }
        }
        mapView.addOnRenderErrorListener {
            post { mapError("map_render_error", "renderer_error") }
        }
        mapView.addOnDidFailLoadingMapListener { error ->
            post { mapError("map_load_error", error) }
        }
        mapView.getMapAsync { ready ->
            if (released) return@getMapAsync
            map = ready
            RadarContextualDiagnosticV1.mapReady()
            ready.setOnMarkerClickListener { marker ->
                markerIds[marker.id]?.let { id ->
                    onMarkerSelected?.invoke(id)
                    true
                } ?: false
            }
            ready.setStyle(Style.Builder().fromUri(STYLE_URI)) {
                if (released) return@setStyle
                styleReady = true
                renderGate.styleLoaded()
                RadarContextualDiagnosticV1.mapEvent("map_style_loaded")
                renderOnMap()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(300)
        val exactHeight = resolveSize(desired, heightMeasureSpec)
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(exactHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !renderGate.submitted) post { if (!released) renderOnMap() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!released && !started) {
            started = true
            mapView.onStart()
            mapView.onResume()
            armRenderTimeout()
            post { if (!released) renderOnMap() }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(renderTimeout)
        if (!released && started) {
            mapView.onPause()
            mapView.onStop()
            started = false
        }
        super.onDetachedFromWindow()
    }

    fun release(reason:String="released") {
        if (released) return
        released = true
        renderGate.release()
        removeCallbacks(renderTimeout)
        if (started) {
            mapView.onPause()
            mapView.onStop()
            started = false
        }
        mapView.onDestroy()
        RadarContextualDiagnosticV1.mapReleased(reason)
    }

    fun render(value: State) {
        state = value.copy(markers = value.markers.take(12))
        renderOnMap()
    }

    private fun renderOnMap() {
        val current = state ?: return
        val ready = map ?: return
        if (!styleReady || released || !renderSurfaceReady()) return

        renderGate.submitted()
        armRenderTimeout()
        ready.clear()
        markerIds.clear()

        addRadius(ready, current)

        val destination = ready.addMarker(
            MarkerOptions()
                .position(LatLng(current.centerLat, current.centerLng))
                .title("Destino")
                .snippet("Destino da corrida atual")
                .icon(icon(Color.rgb(229, 57, 53), "D", selected = true)),
        )
        // Destino não é uma oportunidade clicável.
        markerIds.remove(destination.id)

        current.markers.forEach { poi ->
            val selected = poi.id == current.selectedId
            val annotation = ready.addMarker(
                MarkerOptions()
                    .position(LatLng(poi.lat, poi.lng))
                    .title(poi.title)
                    .snippet(poi.type)
                    .icon(icon(markerColor(poi.type, poi.potential), glyph(poi.type), selected)),
            )
            markerIds[annotation.id] = poi.id
        }

        val points = buildList {
            add(LatLng(current.centerLat, current.centerLng))
            current.markers.forEach { add(LatLng(it.lat, it.lng)) }
        }

        if (points.size == 1) {
            ready.animateCamera(
                CameraUpdateFactory.newLatLngZoom(points.first(), 13.5),
                350,
            )
        } else {
            val bounds = LatLngBounds.fromLatLngs(points)
            ready.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, dp(46)),
                350,
            )
        }
    }

    private fun renderSurfaceReady() = started && isAttachedToWindow && isShown && width > 0 && height > 0

    private fun armRenderTimeout() {
        if (!released && started && renderGate.waiting) {
            if (renderDeadlineMs == 0L) renderDeadlineMs = android.os.SystemClock.uptimeMillis() + 8_000L
            removeCallbacks(renderTimeout)
            postDelayed(renderTimeout, (renderDeadlineMs - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L))
        }
    }

    private fun mapError(event: String, error: String) {
        if (released) return
        renderGate.fail()
        removeCallbacks(renderTimeout)
        RadarContextualDiagnosticV1.mapEvent(event, error)
        showMapFallback()
    }

    private fun showMapFallback() {
        fallback.text = "Não foi possível renderizar o mapa neste aparelho.\nAs oportunidades continuam disponíveis abaixo."
        fallback.visibility = View.VISIBLE
    }

    private fun addRadius(map: MapLibreMap, value: State) {
        val radius = value.radiusKm.coerceIn(0.5, 15.0)
        val latRad = Math.toRadians(value.centerLat)
        val latScale = 1.0 / 111.0
        val lngScale = 1.0 / (111.0 * cos(latRad).coerceAtLeast(0.2))
        val options = PolylineOptions()
            .color(Color.argb(150, 25, 118, 210))
            .width(2.2f * density)

        for (i in 0..64) {
            val a = (Math.PI * 2.0 * i) / 64.0
            options.add(
                LatLng(
                    value.centerLat + sin(a) * radius * latScale,
                    value.centerLng + cos(a) * radius * lngScale,
                ),
            )
        }
        map.addPolyline(options)
    }

    private fun icon(color: Int, label: String, selected: Boolean) =
        IconFactory.getInstance(context).fromBitmap(
            Bitmap.createBitmap(
                if (selected) dp(48) else dp(40),
                if (selected) dp(48) else dp(40),
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                val size = bitmap.width.toFloat()
                val center = size / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                paint.color = Color.WHITE
                canvas.drawCircle(center, center, center - 1f, paint)

                paint.color = color
                canvas.drawCircle(center, center, center - dpF(if (selected) 4f else 3f), paint)

                if (selected) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dpF(2f)
                    paint.color = Color.rgb(17, 24, 39)
                    canvas.drawCircle(center, center, center - dpF(1.5f), paint)
                    paint.style = Paint.Style.FILL
                }

                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = size * 0.42f
                paint.isFakeBoldText = true
                val fm = paint.fontMetrics
                val y = center - (fm.ascent + fm.descent) / 2f
                canvas.drawText(label, center, y, paint)
            },
        )

    private fun markerColor(type: String, potential: String): Int {
        val normalized = type.lowercase()
        return when {
            normalized.contains("hospital") || normalized.contains("health") ||
                normalized.contains("saude") || normalized.contains("saúde") ->
                Color.rgb(239, 68, 68)

            normalized.contains("hotel") || normalized.contains("theater") ||
                normalized.contains("teatro") || normalized.contains("event") ->
                Color.rgb(124, 58, 237)

            normalized.contains("mall") || normalized.contains("shop") ||
                normalized.contains("shopping") || normalized.contains("retail") ->
                Color.rgb(245, 158, 11)

            normalized.contains("school") || normalized.contains("college") ||
                normalized.contains("univers") || normalized.contains("faculdade") ->
                Color.rgb(16, 185, 129)

            normalized.contains("station") || normalized.contains("terminal") ||
                normalized.contains("transit") || normalized.contains("metro") ->
                Color.rgb(37, 99, 235)

            potential == "high" -> Color.rgb(16, 185, 129)
            potential == "medium" -> Color.rgb(245, 158, 11)
            potential == "low" -> Color.rgb(239, 68, 68)
            else -> Color.rgb(124, 58, 237)
        }
    }

    private fun glyph(type: String): String {
        val normalized = type.lowercase()
        return when {
            normalized.contains("hospital") || normalized.contains("health") ||
                normalized.contains("saude") || normalized.contains("saúde") -> "+"
            normalized.contains("hotel") -> "H"
            normalized.contains("theater") || normalized.contains("teatro") ||
                normalized.contains("event") -> "T"
            normalized.contains("mall") || normalized.contains("shop") ||
                normalized.contains("shopping") || normalized.contains("retail") -> "L"
            normalized.contains("school") || normalized.contains("college") ||
                normalized.contains("univers") || normalized.contains("faculdade") -> "E"
            normalized.contains("station") || normalized.contains("terminal") ||
                normalized.contains("transit") || normalized.contains("metro") -> "M"
            else -> "•"
        }
    }

    private fun dp(value: Int) = (value * density).toInt()
    private fun dpF(value: Float) = value * density
}
