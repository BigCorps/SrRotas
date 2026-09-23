package com.srrotas.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Diagnóstico canônico com M1/M2 legado, gates e Reader 2.0 paralelo 0.31. */
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
                    "Abrir screenshots na Galeria não aciona o AccessibilityService do Uber; M2 legado é medido com Uber real em primeiro plano.",
                )
            },
        )
        base.put("offer_integrity_028", OfferIntegrityGuard028.toJson(context))
        base.put("offer_admission_029", OfferAdmissionGate029.toJson(context))
        base.put("offer_admission_030", OfferAdmissionGate030.toJson(context))
        base.put("reader2_shadow_030", Reader2Shadow030.toJson(context))
        base.put("reader2_money_shadow_030_field2", Reader2MoneyShadow030.toJson())
        base.put("reader2_parallel_031", Reader2Parallel031.toJson())
        return base.toString(2)
    }

    fun share(context: Context) {
        val uri = DiagnosticShareProvider0270.prepare(context, build(context))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Sr. Rotas ${BuildConfig.VERSION_NAME} · Core Reader")
            putExtra(
                Intent.EXTRA_TEXT,
                "Diagnóstico técnico do Sr. Rotas com M1 oficial e Reader 2.0 paralelo 0.31 em anexo.",
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Diagnóstico Sr. Rotas Core Reader", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Compartilhar diagnóstico do Sr. Rotas")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
