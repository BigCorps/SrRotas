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

/** Manual screenshot review in :rescan process, with recalculated preview only.
 * Never overwrites an official offer or calls the historical importer. */
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
    private var notice = "Selecione uma imagem da oferta para conferir."
    private lateinit var actions: LinearLayout
    private var queued = false
    private var resumed = false
    private val recheck = Runnable { recheckQueue() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        original = intent.getStringExtra("offer_id")?.let { LocalStore.get(this).offerByLocalId(it) }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@OfferRescanActivityV1,16),UiKit.dp(this@OfferRescanActivityV1,16),
                UiKit.dp(this@OfferRescanActivityV1,16),UiKit.dp(this@OfferRescanActivityV1,24))
            setBackgroundColor(SrUi023.palette(this@OfferRescanActivityV1).background)
        }
        val scroll = ScrollView(this).apply { addView(body); isFillViewport = true }
        setContentView(scroll)
        UiKit.applySafeArea(scroll)
        body.addView(SrUi023.title(this,"Revisar captura",22f))
        body.addView(SrUi023.body(this,"Selecione um print. A revisão recalcula os indicadores sem alterar os dados oficiais. Encerre a jornada antes de revisar.",12f))
        choose = UiKit.primaryButton(this,"Escolher imagem da galeria") { pick() }
        body.addView(UiKit.margin(choose,top=12))
        preview = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = UiKit.dp(this@OfferRescanActivityV1,320)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Prévia da imagem escolhida"
        }
        body.addView(UiKit.margin(preview,top=10))
        confirm = UiKit.secondaryButton(this,"Revisar leitura") { scan() }.apply { isEnabled = false }
        body.addView(UiKit.margin(confirm,top=8))
        status = SrUi023.body(this,"",11f)
        body.addView(UiKit.margin(status,top=8))
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(actions)
        val technical=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=android.view.View.GONE
            addView(SrUi023.body(this@OfferRescanActivityV1,
                ScreenshotRescanComparisonV1.original(original),11f))
            addView(UiKit.secondaryButton(this@OfferRescanActivityV1,
                if(ScreenshotRescanGateV1.enabled(this@OfferRescanActivityV1))
                    "Desativar rescan (rollback)" else "Ativar rescan Field") {
                ScreenshotRescanGateV1.setEnabled(this@OfferRescanActivityV1,
                    !ScreenshotRescanGateV1.enabled(this@OfferRescanActivityV1))
                finish()
            })
        }
        body.addView(UiKit.secondaryButton(this,"Detalhes e opções técnicas") {
            technical.visibility=if(technical.visibility==android.view.View.VISIBLE)
                android.view.View.GONE else android.view.View.VISIBLE
        })
        body.addView(technical)
        body.addView(UiKit.secondaryButton(this,"Voltar") { finish() })
        val block = refreshSafety()
        val linked = original?.localId?.let { PrivateScreenshotIndexV1.resolve(this,it) }
        if(linked != null) load { linked.inputStream() }
        else if(ScreenshotRescanGateV1.shouldAutoPick(block, false, savedInstanceState != null)) pick()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!operation) refreshSafety()
    }

    override fun onPause() {
        resumed = false
        main.removeCallbacks(recheck)
        super.onPause()
    }

    private fun refreshSafety(): ScreenshotRescanBlockV1? {
        // vc108: este processo sobrevive ao fim da jornada; sem recarregar, o bloqueio antigo
        // ("Jornada ativa") ficava preso na tela até o processo morrer.
        RescanProcessStateV108.reload(this)
        val block = ScreenshotRescanGateV1.precheck(this)
        confirm.isEnabled = !operation && bitmap != null && block == null
        choose.isEnabled = !operation
        status.text = notice + if(block == null) "\nA releitura só começa após tocar em Revisar leitura." else
            "\nReleitura indisponível agora: " + block.message +
                if (queued) "\nNa fila: a releitura começa sozinha quando for seguro (esta tela aberta)." else ""
        renderQueue(block)
        return block
    }

    /** Fila controlada: só com imagem escolhida e pedido explícito; nunca concorre com o Reader. */
    private fun renderQueue(block: ScreenshotRescanBlockV1?) {
        actions.findViewWithTag<android.view.View>("queue")?.let(actions::removeView)
        main.removeCallbacks(recheck)
        val waitable = block == ScreenshotRescanBlockV1.JOURNEY || block == ScreenshotRescanBlockV1.CAPTURE ||
            block == ScreenshotRescanBlockV1.OTHER_OCR
        if (operation || bitmap == null || !waitable) { if (block == null) queued = queued && bitmap != null; return }
        actions.addView(UiKit.margin(UiKit.secondaryButton(this, if (queued) "Cancelar fila" else "Revisar quando for seguro") {
            queued = !queued; refreshSafety()
        }.apply { tag = "queue" }, top = 8))
        if (resumed) main.postDelayed(recheck, 4_000L)
    }

    private fun recheckQueue() {
        if (destroyed || operation) return
        val block = refreshSafety()
        if (block == null && queued && bitmap != null) { queued = false; scan() }
    }

    private fun pick() {
        if(operation) return
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
        clearSupplement()
        status.text = "Abrindo imagem…"
        val token = ++generation
        val weak = WeakReference(this)
        try { executor.execute {
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
                        activity.notice = "Imagem aberta para comparação manual. Nenhuma leitura foi executada."
                    }.onFailure { activity.notice = "Não foi possível abrir a imagem (limite 16 MB / 40 MP)." }
                    activity.refreshSafety()
                }
            }
        } } catch (_: Exception) {
            operation = false
            notice = "Não foi possível agendar a abertura da imagem. Tente novamente."
            refreshSafety()
        }
    }

    private fun scan() {
        val image = bitmap ?: return
        if(operation) return
        val app = applicationContext
        val weak = WeakReference(this)
        val blocked = ScreenshotRescanExecutionV1.submit(
            acquire = { ScreenshotRescanGateV1.acquire(this) },
            release = { ScreenshotRescanGateV1.complete() },
            prepare = { SettingsRepository(app).load() },
            transfer = {
                // Transfer first: destruction must never recycle the worker's input.
                bitmap = null
                preview.setImageDrawable(null)
                operation = true; confirm.isEnabled = false; choose.isEnabled = false
                status.text = "Revisando uma imagem localmente…"
                warning = Runnable {
                    status.text = "OCR ainda não terminou. Aguarde; novas capturas ficam bloqueadas até a tarefa terminar. Se travar, force a parada do app nas configurações Android e reabra."
                }.also { main.postDelayed(it,20_000L) }
            },
            executor = executor,
            work = { settings ->
                val print = RescanSupplementV108.fingerprint(image)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val task = recognizer.process(InputImage.fromBitmap(image,0))
                    val text = ScreenshotRescanExecutionV1.awaitCompletion { Tasks.await(task) }
                    val offers = SpatialOfferParser.parse(text,AppSignals.UBER_PACKAGE,"diagnostic-rescan",settings,image.width,image.height)
                    require(offers.size <= 1) { "multiple_cards" }
                    offers.singleOrNull() to print
                } finally { recognizer.close() }
            },
            dispose = { image.recycle() },
            deliver = { result -> main.post {
                val activity = weak.get()
                if(activity != null && !activity.destroyed) {
                    activity.warning?.let(main::removeCallbacks); activity.warning = null
                    activity.operation = false
                    activity.notice = result.fold(
                        onSuccess = { (fresh, print) ->
                            if (fresh == null) "Releitura concluída: nenhuma oferta reconhecida nesta imagem. Nenhum dado oficial mudou."
                            else ScreenshotRescanComparisonV1.describe(activity.original,fresh) +
                                "\n\nPrévia recalculada; nenhum dado oficial foi sobrescrito ou sincronizado." +
                                activity.offerSupplement(fresh, print) },
                        onFailure = { error -> ScreenshotRescanFailureV108.message(error) },
                    )
                    activity.refreshSafety()
                }
            }; Unit },
        )
        if(blocked != null) refreshSafety()
    }

    /** Campos ausentes recuperados: só gravados no registro original após confirmação explícita. */
    private fun offerSupplement(fresh: RideOffer, print: String?): String {
        clearSupplement()
        val offerId = original?.localId ?: return "\n\nSem registro original vinculado: nada a completar."
        val proposals = RescanSupplementV108.proposals(original, fresh)
        if (proposals.isEmpty()) return "\n\nNenhum campo ausente pode ser completado com segurança por esta leitura."
        val confidence = fresh.context?.contextConfidence ?: 0.0
        val text = proposals.joinToString("\n") { "${it.label}: — → ${it.value}" }
        actions.addView(UiKit.margin(UiKit.primaryButton(this, "Completar ${proposals.size} campo(s) ausente(s)") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Completar registro original?")
                .setMessage("$text\n\nSomente campos vazios serão preenchidos. Valores existentes, tempos, distâncias e tarifas não mudam; o histórico guarda o antes/depois.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Completar") { _, _ ->
                    val sent = runCatching { RescanSupplementV108.request(applicationContext, offerId, proposals, print, confidence) }.getOrDefault(false)
                    clearSupplement()
                    notice = if (sent) "Complemento enviado ao registro original:\n$text\nDados anteriores preservados no histórico de auditoria."
                        else "Não foi possível registrar o complemento. Nenhum dado mudou."
                    refreshSafety()
                }.show()
        }.apply { tag = "supplement" }, top = 8))
        return "\n\nCampos ausentes que esta leitura pode completar (requer confirmação):\n$text"
    }

    private fun clearSupplement() {
        if (::actions.isInitialized) actions.findViewWithTag<android.view.View>("supplement")?.let(actions::removeView)
    }

    override fun onDestroy() {
        main.removeCallbacks(recheck)
        destroyed = true; generation++
        warning?.let(main::removeCallbacks); warning = null
        preview.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null; original = null
        // Worker owns its image/lease until task completion, independent of Activity lifecycle.
        super.onDestroy()
    }
}
