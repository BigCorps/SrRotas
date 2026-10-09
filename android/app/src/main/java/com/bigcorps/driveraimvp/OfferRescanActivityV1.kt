package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** Manual, single-image, read-only diagnostic. Never calls the historical importer. */
class OfferRescanActivityV1 : Activity() {
    companion object {
        private const val PICK = 9701
        private val executor = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        fun open(context: Context, offerId: String?) {
            context.startActivity(Intent(context,OfferRescanActivityV1::class.java).apply {
                putExtra("offer_id",offerId)
                if(context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var confirm: TextView
    private lateinit var choose: TextView
    private var bitmap: Bitmap? = null
    private var original: RideOffer? = null
    private var generation = 0
    private var operation = false
    private var destroyed = false
    private var warning: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        original = intent.getStringExtra("offer_id")?.let { LocalStore.get(this).offerByLocalId(it) }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,24) }
        val scroll = ScrollView(this).apply { addView(body); isFillViewport = true }
        setContentView(scroll)
        UiKit.applySafeArea(scroll)
        body.addView(UiKit.title(this,"Foto / Rescan",24f))
        body.addView(UiKit.body(this,"Uma imagem, somente neste aparelho. Revisão diagnóstica Uber pelo parser espacial histórico, não replay exato do M1. Nenhuma oferta, corrida ou Radar será alterado.",13f))
        status = UiKit.body(this,if(original == null) "Sem oferta original selecionada; a comparação mostrará campos ausentes." else "Oferta selecionada para comparação local.",13f)
        body.addView(status)
        preview = ImageView(this).apply { adjustViewBounds = true; maxHeight = UiKit.dp(this@OfferRescanActivityV1,320); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Prévia da imagem escolhida" }
        body.addView(preview)
        confirm = UiKit.primaryButton(this,"Confirmar rescan desta imagem") { scan() }.apply { isEnabled = false }
        choose = UiKit.secondaryButton(this,"Escolher uma imagem") { pick() }
        body.addView(confirm); body.addView(choose)
        val toggle = UiKit.secondaryButton(this,if(ScreenshotRescanGateV1.enabled(this)) "Desativar rescan (rollback)" else "Ativar rescan Field") {
            ScreenshotRescanGateV1.setEnabled(this,!ScreenshotRescanGateV1.enabled(this))
            finish()
        }
        body.addView(toggle)
        body.addView(UiKit.secondaryButton(this,"Voltar") { finish() })
        if(!ScreenshotRescanGateV1.enabled(this)) {
            status.text = "Rescan desativado. Nenhum OCR será iniciado."
            choose.isEnabled = false
            return
        }
        val linked = original?.localId?.let { PrivateScreenshotIndexV1.resolve(this,it) }
        if(linked != null) load { linked.inputStream() } else if(savedInstanceState == null) pick()
    }

    private fun pick() {
        if(operation || !ScreenshotRescanGateV1.enabled(this)) return
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false)
        },PICK)
    }

    @Deprecated("Compatibilidade sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode != PICK || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if(uri.scheme != "content" || (data.clipData?.itemCount ?: 1) != 1) {
            status.text = "Escolha uma única imagem pelo seletor Android."
            return
        }
        // Temporary read grant only. No persistable grant, filesystem path or Gallery enumeration.
        val resolver = applicationContext.contentResolver
        load { requireNotNull(resolver.openInputStream(uri)) }
    }

    private fun load(open: () -> java.io.InputStream) {
        operation = true; choose.isEnabled = false; confirm.isEnabled = false
        val token = ++generation
        val weak = WeakReference(this)
        executor.execute {
            val result = runCatching { ScreenshotRescanImageV1.decode(open()) }
            main.post {
                val activity = weak.get()
                if(activity == null || activity.destroyed || activity.generation != token) {
                    result.getOrNull()?.recycle()
                } else {
                    activity.operation = false; activity.choose.isEnabled = true
                    result.onSuccess { image ->
                        activity.preview.setImageDrawable(null)
                        activity.bitmap?.recycle()
                        activity.bitmap = image
                        activity.preview.setImageBitmap(image)
                        activity.confirm.isEnabled = true
                        activity.status.text = "Confira a prévia e confirme. Nenhuma leitura foi executada ainda."
                    }.onFailure { activity.status.text = "Não foi possível abrir a imagem (limite 16 MB / 40 MP)." }
                }
            }
        }
    }

    private fun scan() {
        val image = bitmap ?: return
        if(operation || !ScreenshotRescanGateV1.acquire(this)) {
            status.text = ScreenshotRescanGateV1.BLOCK_MESSAGE
            return
        }
        operation = true; confirm.isEnabled = false; choose.isEnabled = false
        status.text = "Revisando uma imagem localmente…"
        // Transfer ownership to the worker; onDestroy must not recycle ML Kit's input.
        bitmap = null
        preview.setImageDrawable(null)
        val app = applicationContext
        val settings = SettingsRepository(app).load()
        val weak = WeakReference(this)
        warning = Runnable {
            status.text = "OCR ainda não terminou. Aguarde; novas capturas ficam bloqueadas até a tarefa terminar. Se travar, reinicie o app manualmente."
        }.also { main.postDelayed(it,20_000L) }
        executor.execute {
            val result = runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    // No timed await: a UI timeout cannot release a lease while native OCR is still running.
                    val text = Tasks.await(recognizer.process(InputImage.fromBitmap(image,0)))
                    val offers = SpatialOfferParser.parse(text,AppSignals.UBER_PACKAGE,"diagnostic-rescan",settings,image.width,image.height)
                    require(offers.size <= 1) { "multiple_cards" }
                    offers.singleOrNull()
                } finally { recognizer.close() }
            }
            image.recycle()
            ScreenshotRescanGateV1.complete()
            main.post {
                val activity = weak.get()
                if(activity != null && !activity.destroyed) {
                    activity.warning?.let(main::removeCallbacks); activity.warning = null
                    activity.operation = false; activity.choose.isEnabled = true
                    activity.status.text = result.fold(
                        onSuccess = { fresh -> ScreenshotRescanComparisonV1.describe(activity.original,fresh) +
                            "\n\nNada foi aplicado ou sincronizado. Correções confirmadas ficam para fase futura." },
                        onFailure = { "A imagem não pôde ser revisada como uma única oferta. Nenhum dado oficial mudou." },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        destroyed = true; generation++
        warning?.let(main::removeCallbacks); warning = null
        preview.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null; original = null
        // Worker owns its image/lease until task completion, independent of Activity lifecycle.
        super.onDestroy()
    }
}
