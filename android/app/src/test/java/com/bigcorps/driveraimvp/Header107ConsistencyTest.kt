package com.srrotas.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** vc107: one canonical header (vc103 composition + theme control) on every main screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Header107ConsistencyTest {
    private val titles = listOf("Estatísticas", "IA do Sr. Rotas", "Agora", "Radar", "Histórico", "Configurações", "Usuário")

    private fun measure(activity: MainActivity, title: String, widthDp: Int): SrAppHeader023 {
        val header = SrAppHeader023(activity, title, "")
        val w = (widthDp * activity.resources.displayMetrics.density).toInt()
        header.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        header.layout(0, 0, w, header.measuredHeight)
        return header
    }

    private fun pill(header: SrAppHeader023) = header.getChildAt(header.childCount - 1) as LinearLayout

    @Test fun everyScreenUsesTheSameSingleRowHeaderAndPill() {
        for (qualifiers in listOf("w360dp-h780dp-xhdpi", "w411dp-h914dp-420dpi", "w800dp-h1280dp-xhdpi")) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            for (font in listOf(1f, 1.3f)) {
                RuntimeEnvironment.setFontScale(font)
                val activity = Robolectric.buildActivity(MainActivity::class.java).get()
                val widthDp = activity.resources.configuration.screenWidthDp
                val headers = titles.map { measure(activity, it, widthDp) }
                // Logo, title and pill share one horizontal row (no stacked variant).
                headers.forEach { assertEquals(LinearLayout.HORIZONTAL, it.orientation); assertEquals(3, it.childCount) }
                assertEquals("same height on every screen ($qualifiers/$font)", 1, headers.map { it.measuredHeight }.distinct().size)
                val pills = headers.map(::pill)
                assertEquals("same pill width ($qualifiers/$font)", 1, pills.map { it.measuredWidth }.distinct().size)
                assertEquals(1, pills.map { it.left }.distinct().size)
                pills.forEach { p ->
                    val icons = (0 until p.childCount).map(p::getChildAt).filterIsInstance<ImageView>()
                    assertEquals(listOf("Aparência", "Configurações", "Usuário"), icons.map { it.contentDescription.toString().substringBefore(':') })
                    assertEquals(1, icons.map { it.layoutParams.width }.distinct().size)
                }
                val logo = headers.map { it.getChildAt(0).measuredWidth }.distinct()
                assertEquals(1, logo.size)
            }
        }
    }

    @Test fun headerOutsideTheMainShellKeepsNoPill() {
        val other = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val header = SrAppHeader023(other, "Aparência", "")
        assertEquals(2, header.childCount)
    }
}
