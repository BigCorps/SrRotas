package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.math.abs

/**
 * RC3.4 — aplica a opacidade do painel expandido sem alterar a semântica
 * histórica de bubble_opacity, que continua pertencendo ao botão.
 *
 * A janela flutuante vive fora da árvore da Activity; por isso este pequeno
 * sincronizador apenas ajusta alpha dos componentes já criados pelo controller.
 * Os Fields são resolvidos uma única vez e nenhuma captura/OCR/jornada passa
 * por aqui.
 */
object FloatingWindowOpacity027034 {
    private const val POLL_MS = 1_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var appContext: Context? = null

    private val panelField: Field? by lazy { resolveField("panel") }
    private val railField: Field? by lazy { resolveField("railHost") }

    fun ensureWatcher(context: Context) {
        appContext = context.applicationContext
        if (running) return
        running = true
        main.post(watcher)
    }

    fun applyNow(context: Context) {
        val alpha = JourneyUiPreferences(context).windowOpacityPercent() / 100f
        applyAlpha(panelField, alpha)
        applyAlpha(railField, alpha)
    }

    private fun resolveField(name: String): Field? = runCatching {
        JourneyBubbleController::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
    }.getOrNull()

    private fun applyAlpha(field: Field?, alpha: Float) {
        val view = field?.let(::readView) ?: return
        if (abs(view.alpha - alpha) > 0.001f) view.alpha = alpha
    }

    private fun readView(field: Field): View? = runCatching {
        val receiver = if (Modifier.isStatic(field.modifiers)) null else JourneyBubbleController
        field.get(receiver) as? View
    }.getOrNull()

    private val watcher = object : Runnable {
        override fun run() {
            val context = appContext
            if (!running || context == null) return
            applyNow(context)
            main.postDelayed(this, POLL_MS)
        }
    }
}
