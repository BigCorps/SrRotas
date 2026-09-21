package com.srrotas.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Diagnóstico padrão com M1/M2, saúde do AccessibilityService e integridade 0.28. */
object ReaderLabCombinedDiagnostic0270361 {
    fun build(context: Context): String {
        val base = runCatching { JSONObject(DiagnosticBundle.build(context, includeRawOcr = false)) }
            .getOrElse { JSONObject().put("diagnostic_base_error", it.message ?: "unknown") }
        val lab = ReaderLab027036.snapshot(context)
        base.put(
            "reader_lab_0270361",
            JSONObject().apply {
                put("mode", lab.mode)
                put("m1_seen", lab.m1Seen)
                put("m2_seen", lab.m2Seen)
                put("matched", lab.matched)
                put("m1_only", lab.m1Only)
                put("m2_only", lab.m2Only)
                put("m1_complete", lab.m1Complete)
                put("m2_complete", lab.m2Complete)
                put("m1_core_points", lab.m1CorePoints)
                put("m2_core_points", lab.m2CorePoints)
                put("latest", lab.latest)
                put("accessibility_enabled", lab.accessibilityEnabled)
                put("m2_health", ReaderLabTelemetry0270361.toJson(context))
                put(
                    "gallery_note",
                    "Abrir screenshots na Galeria não aciona o AccessibilityService do Uber; M2 é medido com Uber real em primeiro plano.",
                )
            },
        )
        base.put("offer_integrity_028", OfferIntegrityGuard028.toJson(context))
        return base.toString(2)
    }

    fun share(context: Context) {
        val uri = DiagnosticShareProvider0270.prepare(context, build(context))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Sr. Rotas ${BuildConfig.VERSION_NAME} · M1 M2")
            putExtra(Intent.EXTRA_TEXT, "Diagnóstico técnico do Sr. Rotas com Reader Lab M1/M2 e integridade 0.28 em anexo.")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Diagnóstico Sr. Rotas M1 M2", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Compartilhar diagnóstico do Sr. Rotas")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
