package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
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
import java.time.Instant

/**
 * Opt-in visual QA vc114 (-PqaScreenshots): Radar de duas abas dentro do shell real (cabeçalho e rodapé
 * canônicos), claro/escuro, tablet/celular/janela estreita. Dados FICTÍCIOS de teste, nunca de produção.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class Vc114RadarVisualQa105Test {
    private val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105", "vc114-radar").apply { mkdirs() }

    private fun capture(root: View, wDp: Int, hDp: Int, name: String) {
        val d = root.resources.displayMetrics.density
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); root.draw(Canvas(b))
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    private fun opp(id: String, title: String, type: String, km: Double, potential: String, ctx: RadarContextualContextV1? = null) =
        RadarContextualOpportunityV1(id, "poi-$id", title, null, type, -23.56, -46.65, km, "2099-01-01T00:00:00Z",
            null, null, null, 0.8, 0.5, potential, "", null, null, ctx, emptyList())

    @Test fun screens() {
        val soon = Instant.now().plusSeconds(28 * 60).toString()
        val egressA = Instant.now().plusSeconds(5 * 3600).toString(); val egressB = Instant.now().plusSeconds(5 * 3600 + 2700).toString()
        val spec = RadarDestinationSpecV1(-23.56, -46.65, soon, "Av. Exemplo, 1000 · Bairro Fictício", "offer-qa")
        val result = RadarContextualResultV1(Instant.now().toString(), -23.56, -46.65, soon, spec.label, "cell",
            RadarContextualBaselineV1(null, 0, "insufficient", "insufficient"), listOf(
                opp("1", "Centro de Convenções Exemplo", "convention_center", 2.4, "insufficient",
                    RadarContextualContextV1("e1", "Congresso Fictício", "fair_convention", egressA, egressB, 0.9)),
                opp("2", "Shopping Exemplo", "shopping_mall", 3.1, "high"),
                opp("3", "Hospital Exemplo", "hospital", 4.6, "insufficient"),
                opp("4", "Hotel Exemplo", "hotel", 5.2, "medium"),
            ), RadarContextualAssistantV1(false, "none", null, null, null, 0, 0))
        fun ev(id: String, name: String, type: String, km: Double, hours: Long) = Instant.now().plusSeconds(hours * 3600).let {
            EventRadarOpportunity026(id, type, name, "Local Exemplo", "Rua Exemplo, $id", it.toString(), it.plusSeconds(3 * 3600).toString(),
                it.plusSeconds(3 * 3600).toString(), it.plusSeconds(4 * 3600).toString(), km, "fixture", 0.9, null)
        }
        val events = EventRadarResult026(listOf(ev("1", "Show Fictício", "music", 1.8, 6), ev("2", "Jogo Fictício", "sports", 2.3, 5),
            ev("3", "Feira Fictícia", "fair_convention", 3.1, 20), ev("4", "Peça Fictícia", "theatre", 4.2, 7)), "ok", Instant.now().toString())

        for ((size, wh) in listOf("tablet" to (680 to 1100), "celular" to (411 to 914), "estreita" to (330 to 760))) for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers("w${wh.first}dp-h${wh.second}dp-xhdpi")
            val app = RuntimeEnvironment.getApplication()
            Strategy021Store.saveAppTheme(app, if (dark) "dark" else "light")
            app.getSharedPreferences("sr_radar_v114", 0).edit().clear().commit()
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            ConsolidatedMainActivity027037::class.java.getDeclaredMethod("navigate", SrBottomNav023.Route::class.java)
                .apply { isAccessible = true }.invoke(activity, SrBottomNav023.Route.USER)
            ShadowLooper.idleMainLooper()
            val root = activity.window.decorView
            val panel = find(root) { it is RadarPanelV114 } as RadarPanelV114
            val tag = "$size-${if (dark) "escuro" else "claro"}"
            capture(root, wh.first, wh.second, "pre-$tag")
            panel.showDestinationForTest(spec, result); capture(root, wh.first, wh.second, "proximas-do-destino-$tag")
            panel.showEventsForTest(RadarV114.Reference.YOU, events); capture(root, wh.first, wh.second, "eventos-em-geral-$tag")
            if (size == "celular" && !dark) {
                panel.showDestinationForTest(null, null); capture(root, wh.first, wh.second, "estado-sem-corrida-$tag")
                panel.showEventsForTest(RadarV114.Reference.YOU, null, error = RadarRegion109.failure("Unable to resolve host")); capture(root, wh.first, wh.second, "estado-falha-rede-$tag")
                panel.showEventsForTest(RadarV114.Reference.YOU, EventRadarResult026(emptyList(), "ok", Instant.now().toString())); capture(root, wh.first, wh.second, "estado-sem-eventos-$tag")
                panel.showEventsForTest(RadarV114.Reference.NONE, null); capture(root, wh.first, wh.second, "estado-sem-localizacao-$tag")
            }
        }
    }
}
