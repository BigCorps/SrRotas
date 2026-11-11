package com.srrotas.app.r3lab

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.srrotas.app.SrUi023
import com.srrotas.app.UiKit
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Ver imagem utilizada": abre EXATAMENTE os quadros vinculados à oferta do laboratório
 * (frameId/observationId gravados no commit), com SHA-256 conferido. Não executa OCR, não altera
 * a oferta e nunca abre outra imagem "parecida" quando a original não existe.
 */
class R3EvidenceViewerActivity : Activity() {
    companion object {
        const val EXTRA_OFFER = "lab_offer_id"
        /** vc112: `local_offers.local_id` de uma oferta OFICIAL gravada a partir do R3. */
        const val EXTRA_OFFICIAL = "official_offer_local_id"
        fun hasOfficialEvidence(context: android.content.Context, offerLocalId: String) =
            runCatching { R3LabDb.get(context).let { db -> db.officialLink(offerLocalId)?.let { db.frame(it.frameId)?.status == R3EvidenceStatus.READY } == true } }.getOrDefault(false)
    }
    private var shown: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@R3EvidenceViewerActivity, 14), UiKit.dp(this@R3EvidenceViewerActivity, 16), UiKit.dp(this@R3EvidenceViewerActivity, 14), UiKit.dp(this@R3EvidenceViewerActivity, 26))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(SrUi023.palette(this@R3EvidenceViewerActivity).background); addView(body) }
        setContentView(scroll); UiKit.applySafeArea(scroll)
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "← Voltar") { finish() }, top = 2))
        body.addView(SrUi023.title(this, "Imagem utilizada", 20f))
        intent.getStringExtra(EXTRA_OFFICIAL)?.let { renderOfficial(body, it); return }
        val offerId = intent.getStringExtra(EXTRA_OFFER).orEmpty()
        val db = R3LabDb.get(this)
        val offer = db.offers(1000).firstOrNull { it.labOfferId == offerId }
        val links = db.links(offerId)
        if (offer == null || links.isEmpty()) { body.addView(SrUi023.body(this, "Oferta do laboratório sem vínculo de evidência.", 12f)); return }
        val fields = runCatching { JSONObject(offer.fieldsJson) }.getOrDefault(JSONObject())
        body.addView(SrUi023.body(this, "Oferta ${offer.labOfferId.take(8)} · ${offer.platform.uppercase()} · ${if (offer.accepted) "ACEITA" else "REJEITADA (${offer.rejection})"}", 11f))
        val store = R3EvidenceStore(this, db)
        links.forEach { link ->
            val frame = db.frame(link.frameId)
            body.addView(UiKit.margin(SrUi023.title(this, if (link.role == R3EvidenceRole.PRIMARY) "Quadro principal" else "Quadro suplementar", 14f), top = 12))
            if (frame == null) { body.addView(SrUi023.body(this, "Quadro ausente do banco.", 11f)); return@forEach }
            val kind = if (frame.sourceKind == R3SourceKind.WINDOW.name) "Captura da janela ${platformName(frame.sourcePackage)} (imagem da janela, não a tela inteira)"
                else "Recorte da tela na área da janela ${platformName(frame.sourcePackage)} (não é a tela inteira)"
            body.addView(SrUi023.body(this, kind, 11f))
            body.addView(SrUi023.body(this, "frame ${frame.frameId.take(8)} · observação ${link.observationId.take(8)} · perfil ${frame.profileId} g${frame.profileGeneration} · " +
                SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(frame.capturedAtMs)), 10f))
            val origins = runCatching { JSONObject(link.fieldOrigins ?: "{}") }.getOrDefault(JSONObject())
            if (origins.length() > 0) body.addView(SrUi023.body(this, "Campos lidos neste quadro: " + origins.keys().asSequence().joinToString { "$it (${origins.getString(it).lowercase()})" }, 10f))
            val (file, status) = store.verifiedFile(frame.frameId)
            body.addView(SrUi023.body(this, "Integridade: " + when (status) { "sha256_ok" -> "SHA-256 conferido (${frame.sha256?.take(12)}…)"; else -> "imagem indisponível — $status" }, 10.5f))
            if (file != null) {
                val bmp = BitmapFactory.decodeFile(file.path)?.let { overlay(it, fields, link.observationId) }
                if (bmp != null) {
                    shown = bmp
                    body.addView(UiKit.margin(ImageView(this).apply { adjustViewBounds = true; setImageBitmap(bmp); contentDescription = "Imagem usada pela leitura" }, top = 6))
                }
            }
        }
        body.addView(UiKit.margin(SrUi023.body(this, "Visualização apenas. Para reler o texto use \"Reprocessar OCR\" na tela do laboratório.", 10f), top = 10))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { finish() }, top = 8))
    }

    /** "Ver captura original" do histórico: SÓ o quadro vinculado ao ID gravado, ou indisponível. Sem OCR. */
    private fun renderOfficial(body: LinearLayout, offerLocalId: String) {
        val db = R3LabDb.get(this)
        val link = db.officialLink(offerLocalId)
        if (link == null) { body.addView(SrUi023.body(this, "Captura original indisponível para esta oferta.", 12f)); body.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { finish() }, top = 8)); return }
        val frame = db.frame(link.frameId)
        body.addView(SrUi023.body(this, "Oferta ${offerLocalId.take(8)} · quadro ${link.frameId.take(8)} · observação ${link.observationId.take(8)}", 10.5f))
        frame?.let { body.addView(SrUi023.body(this, (if (it.sourceKind == R3SourceKind.WINDOW.name) "Captura da janela " else "Recorte da tela na área da janela ") +
            platformName(it.sourcePackage) + " · " + SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(it.capturedAtMs)), 10.5f)) }
        val (file, status) = R3EvidenceStore(this, db).verifiedFile(link.frameId)
        body.addView(SrUi023.body(this, if (file != null) "Integridade: SHA-256 conferido" else "Imagem indisponível — $status", 10.5f))
        file?.let { BitmapFactory.decodeFile(it.path) }?.let { bmp ->
            shown = bmp
            body.addView(UiKit.margin(ImageView(this).apply { adjustViewBounds = true; setImageBitmap(bmp); contentDescription = "Captura original da oferta" }, top = 6))
        }
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { finish() }, top = 8))
    }

    /** Desenha as caixas dos campos extraídos DESTA observação (prova visual da origem do valor). */
    private fun overlay(src: Bitmap, fields: JSONObject, observationId: String): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        if (out !== src) src.recycle()
        val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.MAGENTA }
        val canvas = Canvas(out)
        fields.keys().forEach { k ->
            val f = fields.optJSONObject(k) ?: return@forEach
            if (f.optString("observation_id") != observationId) return@forEach
            val b = f.optString("bounds").split(',').mapNotNull { it.trim().toIntOrNull() }
            if (b.size == 4) canvas.drawRect(b[0].toFloat(), b[1].toFloat(), b[2].toFloat(), b[3].toFloat(), paint)
        }
        return out
    }

    private fun platformName(pkg: String) = when (R3AllowedPackages.platform(pkg)) { "uber" -> "Uber"; "99" -> "99"; else -> pkg }

    override fun onDestroy() { shown?.recycle(); shown = null; super.onDestroy() }
}
