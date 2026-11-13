package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/** Native Views + canonical navigation; synthetic data stays outside production source sets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class RadarTwoTabsQa105Test {
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }
    @Test fun nativeMatrix() {
        val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105", "radar-two-tabs").apply { mkdirs() }
        for ((w, h, name) in listOf(Triple(800, 1280, "tablet"), Triple(390, 844, "celular"), Triple(360, 800, "split")))
            for (dark in listOf(false, true)) for (tab in RadarTwoTabsRulesV1.Tab.values()) {
                RuntimeEnvironment.setQualifiers("w${w}dp-h${h}dp-mdpi")
                val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
                Strategy021Store.saveAppTheme(activity, if (dark) "dark" else "light")
                val panel = RadarTwoTabsPanelV1(activity, RadarTwoTabsFixtures.Data()) { RadarTwoTabsFixtures.now }
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL; setBackgroundColor(SrUi023.palette(activity).background)
                    addView(TextView(activity).apply { text = "PRÉVIA DE TESTE · DADOS SINTÉTICOS"; textSize = 12f; setTextColor(SrUi023.palette(activity).navy) })
                    addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                    addView(SrBottomNav023(activity, SrBottomNav023.Route.USER) {}, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 84))
                }
                activity.setContentView(root)
                fun layout() {
                    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                }
                layout(); panel.setSurfaceResumed(true); ShadowLooper.idleMainLooper()
                if (tab == RadarTwoTabsRulesV1.Tab.EVENTS) texts(panel).first { it.text == tab.label }.performClick()
                layout(); ShadowLooper.idleMainLooper(); layout()
                val selected = texts(panel).first { it.text == tab.label }
                assertTrue(selected.isSelected); assertTrue("tab touch target", selected.height >= 48)
                for (text in texts(panel).filter { it.isShown && it.layout != null })
                    for (line in 0 until text.layout.lineCount) assertEquals("clipped: ${text.text}", 0, text.layout.getEllipsisCount(line))
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); root.draw(Canvas(bitmap))
                FileOutputStream(File(out, "$name-${if (dark) "escuro" else "claro"}-${tab.name.lowercase()}.png")).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            }
    }
}
