package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Opt-in visual QA for vc107 (-PqaScreenshots): shared header on every screen and the map-less Radar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class Vc107VisualQa105Test {
    private val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105", "vc107").apply { mkdirs() }

    private fun save(view: View, w: Int, name: String, h: Int? = null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            if (h == null) View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED) else View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(w, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun headersOfAllScreens() {
        for ((qualifiers, tag) in listOf("w411dp-h914dp-420dpi" to "phone411", "w360dp-h780dp-xhdpi" to "phone360", "w800dp-h1280dp-xhdpi" to "smx626b")) for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            val activity = Robolectric.buildActivity(MainActivity::class.java).get()
            Strategy021Store.saveAppTheme(activity, if (dark) "dark" else "light")
            val stack = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(SrUi023.palette(activity).background)
                for (title in listOf("Estatísticas", "IA do Sr. Rotas", "Agora", "Radar", "Histórico", "Configurações", "Usuário")) {
                    addView(SrAppHeader023(activity, title, ""))
                    addView(View(activity).apply { setBackgroundColor(0xFFE11D48.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2))
                }
            }
            save(stack, (activity.resources.configuration.screenWidthDp * activity.resources.displayMetrics.density).toInt(), "cabecalhos-$tag-${if (dark) "escuro" else "claro"}")
        }
    }

    @Test fun radarWithoutMap() {
        for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            Strategy021Store.saveAppTheme(activity, if (dark) "dark" else "light")
            val main = Robolectric.buildActivity(MainActivity::class.java).get()
            val panel = RadarContextualPanelV1(main)
            activity.setContentView(panel)
            panel.setSurfaceResumed(true)
            val w = (411 * activity.resources.displayMetrics.density).toInt()
            save(panel, w, "radar-resumo-sem-corrida-${if (dark) "escuro" else "claro"}")
            panel.openDemo()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            save(panel, w, "radar-resumo-demo-${if (dark) "escuro" else "claro"}")
        }
    }
}
