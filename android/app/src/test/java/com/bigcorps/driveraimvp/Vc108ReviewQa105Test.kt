package com.srrotas.app

import android.app.Activity
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
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** Opt-in visual QA for vc108 (-PqaScreenshots): recovery review and gallery rescan supplement. Fixtures only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class Vc108ReviewQa105Test {
    private val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105", "vc108").apply { mkdirs() }

    private fun save(view: View, w: Int, name: String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, w, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(w, maxOf(1, view.measuredHeight), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun recoveryReview() {
        for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            Strategy021Store.saveAppTheme(activity, if (dark) "dark" else "light")
            val store = LocalStore.get(activity)
            val journey = store.startJourney(); store.endJourney(journey.id, "qa")
            val now = Instant.now()
            fun offer(id: String, fare: Double, sec: Long) = Fixtures108.offer("$id-$dark", fare, observedAt = now.plusSeconds(sec).toString(), journeyId = journey.id)
            val a = offer("qa1", 17.99, 30); val b = offer("qa2", 11.99, 600); val c = offer("qa3", 36.94, 1200)
            listOf(a, b, c).forEach { store.saveOffer(it); store.ensureOfferedOutcome(it) }
            store.updateRideOutcome(a.localId, journey.id, RideOperationalStatus.COMPLETED, "bubble_024")
            store.updateRideOutcome(c.localId, journey.id, RideOperationalStatus.NOT_COMPLETED, "journey_end_auto")
            UberDigitizationStore026.get(activity).saveRides(listOf(
                UberCompletedRide026("qa-i1-$dark", now.toString(), now.plusSeconds(900).toString(), 17.99, "uberx", null, null, 0.9),
                UberCompletedRide026("qa-i2-$dark", now.toString(), now.plusSeconds(1500).toString(), 11.99, "uberx", null, null, 0.9),
                UberCompletedRide026("qa-i4-$dark", now.toString(), now.plusSeconds(2400).toString(), 52.10, "uberx", null, null, 0.9),
            ))
            UberDigitizationStore026.get(activity).saveSession(UberSessionSummary026("qa-s-$dark", now.toString(), now.toString(),
                now.plusSeconds(4000).toString(), 641.76, 6, 112, 0.95, journey.id))
            RideRecoveryDialog108.show(activity, journey.id)
            val dialog = ShadowDialog.getLatestDialog()
            save(dialog.window!!.decorView, (411 * activity.resources.displayMetrics.density).toInt(), "recuperar-corridas-${if (dark) "escuro" else "claro"}")
            dialog.dismiss()
        }
    }
}
