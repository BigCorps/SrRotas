package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Opt-in visual QA for vc109 (-PqaScreenshots): history review, Radar region and rescan state. Fixtures only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class Vc109VisualQa105Test {
    private val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105", "vc109").apply { mkdirs() }
    private fun save(view: View, name: String) {
        val w = (411 * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, w, view.measuredHeight)
        val b = Bitmap.createBitmap(w, maxOf(1, view.measuredHeight), Bitmap.Config.ARGB_8888); view.draw(Canvas(b))
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun ride(key: String, fare: Double, at: String?, pickup: String? = "Rua Alfa, 10 - Bairro Um", dest: String? = "Avenida Beta, 20 - Bairro Dois",
        status: String = UberCompletedRide026.STATUS_COMPLETED, confidence: Double = 0.9) =
        UberCompletedRide026(key, "2026-11-03T22:00:00Z", at, fare, "uberx", pickup, dest, confidence, 900, 6.2, rideStatus = status)

    @Test fun screens() {
        for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
            val app = RuntimeEnvironment.getApplication()
            Strategy021Store.saveAppTheme(app, if (dark) "dark" else "light")
            val tag = if (dark) "escuro" else "claro"
            HistoryReviewStore109.save(app, listOf(ride("a", 23.51, "2026-11-01T19:34:00Z"), ride("b", 10.19, "2026-11-01T14:35:00Z", dest = null),
                ride("c", 0.0, "2026-11-01T15:00:00Z", status = UberCompletedRide026.STATUS_CANCELLED), ride("d", 31.0, "2026-11-01T12:02:00Z", confidence = 0.58)), 12, 9)
            save(Robolectric.buildActivity(HistoryReviewActivity109::class.java).setup().get().window.decorView, "conferencia-historico-$tag")
            val main = Robolectric.buildActivity(MainActivity::class.java).get()
            save(RadarContextualPanelV1(main), "radar-regiao-$tag")
            save(Robolectric.buildActivity(OfferRescanActivityV1::class.java).setup().get().window.decorView, "releitura-estado-$tag")
        }
    }
}
