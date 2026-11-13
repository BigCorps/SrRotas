package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Opt-in visual QA vc114 (-PqaScreenshots): Conferência do Histórico com seleção pelo card (borda azul). Fixtures fictícias. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class Vc114HistoryVisualQa105Test {
    private val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105", "vc114-historico").apply { mkdirs() }
    private fun save(view: View, wDp: Int, name: String) {
        val w = (wDp * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, w, view.measuredHeight)
        val b = Bitmap.createBitmap(w, maxOf(1, view.measuredHeight), Bitmap.Config.ARGB_8888); view.draw(Canvas(b))
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }
    private fun ride(key: String, fare: Double, at: String?, dest: String? = "Avenida Beta, 20 - Bairro Dois", status: String = UberCompletedRide026.STATUS_COMPLETED, confidence: Double = 0.9) =
        UberCompletedRide026(key, "2026-11-13T22:00:00Z", at, fare, "uberx", "Rua Alfa, 10 - Bairro Um", dest, confidence, 900, 6.2, rideStatus = status)

    @Test fun screens() {
        for ((size, wDp) in listOf("celular" to 411, "tablet" to 680)) for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w${wDp}dp-h914dp-xhdpi")
            val app = RuntimeEnvironment.getApplication()
            Strategy021Store.saveAppTheme(app, if (dark) "dark" else "light")
            HistoryReviewStore109.save(app, listOf(ride("a", 23.51, "2026-11-12T19:34:00Z"), ride("b", 10.19, "2026-11-12T14:35:00Z", dest = null),
                ride("c", 0.0, "2026-11-12T15:00:00Z", status = UberCompletedRide026.STATUS_CANCELLED), ride("d", 31.0, "2026-11-12T12:02:00Z", confidence = 0.58)), 12, 9)
            val a = Robolectric.buildActivity(HistoryReviewActivity109::class.java).setup().get()
            // Toca no CARD do incompleto (seleção explícita) para mostrar as duas bordas.
            texts(a.window.decorView).firstOrNull { it.text.startsWith("Incluir nesta confirmação") }?.let { (it.parent as View).performClick() }
            // Primeira passada de layout dispara a re-renderização dos filtros pela largura real (post); depois captura.
            save(a.window.decorView, wDp, "conferencia-$size-${if (dark) "escuro" else "claro"}")
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            save(a.window.decorView, wDp, "conferencia-$size-${if (dark) "escuro" else "claro"}")
        }
    }
}
