package com.srrotas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AiMcpPanel(context: Context) : LinearLayout(context) {
    private val repo = SettingsRepository(context)
    private val period = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Hoje", "7 dias", "30 dias", "90 dias"))
        setSelection(1)
    }
    private val question = UiKit.input(context, "Pergunte sobre suas ofertas observadas...", multiline = true)
    private val answer = UiKit.body(context, "Escolha uma pergunta sugerida ou escreva a sua.", 15f)
    private val answerMeta = UiKit.body(context, "", 11f)
    private val mcpStatus = UiKit.body(context, "Carregando integrações...", 13f)
    private val tokenName = UiKit.input(context, "Nome da integração — ex.: ChatGPT")
    private val activeTokens = LinearLayout(context).apply { orientation = VERTICAL }
    private val secretCard = UiKit.card(context).apply { visibility = View.GONE }
    private val secretText = UiKit.body(context, "", 12f).apply { setTextIsSelectable(true) }
    private var lastSecret: String = ""
    private var lastEndpoint: String = ""

    init {
        orientation = VERTICAL
        addView(buildAiSection())
        addView(UiKit.margin(buildMcpSection(), top = 14))
        refreshMcp()
    }

    private fun buildAiSection(): View = UiKit.card(context).apply {
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(UiKit.sectionTitle(context, "IA do Sr. Rotas"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiKit.pill(context, "IA PRÓPRIA", "primary"))
        addView(header)
        addView(UiKit.body(context, "Use a IA quando quiser interpretação em linguagem natural. Os cálculos, histórico e gráficos continuam sem IA."))
        addView(caption("Período analisado")); addView(period)

        addView(UiKit.margin(UiKit.body(context, "Perguntas rápidas", 13f).apply { setTypeface(typeface, Typeface.BOLD) }, top = 10))
        listOf(
            "Quais horários tiveram as melhores ofertas?",
            "O que mais está derrubando a minha estratégia?",
            "Quais categorias estão aparecendo com melhor R$/km?",
            "Resuma meu período e diga o que vale observar no próximo turno.",
        ).forEach { prompt ->
            addView(UiKit.margin(UiKit.secondaryButton(context, prompt) {
                question.setText(prompt)
                ask()
            }, top = 6))
        }

        addView(UiKit.margin(question, top = 10))
        addView(UiKit.margin(UiKit.primaryButton(context, "Perguntar à IA") { ask() }, top = 9))
        addView(UiKit.margin(UiKit.card(context, 12).apply {
            addView(answer)
            addView(UiKit.margin(answerMeta, top = 7))
        }, top = 10))
        addView(UiKit.margin(UiKit.body(context, "Nesta fase Alpha a IA ainda não desconta créditos. A fase 0.10 adicionará assinatura e carteira de créditos usando o consumo real medido agora.", 11f), top = 8))
    }

    private fun buildMcpSection(): View = UiKit.card(context).apply {
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(UiKit.sectionTitle(context, "Use a IA que você já usa"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiKit.pill(context, "MCP", "good"))
        addView(header)
        addView(UiKit.body(context, "Conecte ChatGPT, Claude, Cursor ou outro cliente MCP compatível aos seus dados do Sr. Rotas. O cliente externo usa a própria IA; consultar ferramentas MCP não chama a IA paga do Sr. Rotas."))

        addView(UiKit.margin(UiKit.card(context, 12).apply {
            addView(UiKit.body(context, "1. Gere uma chave para o aplicativo desejado.\n2. Copie o endpoint e a chave Bearer.\n3. Cadastre como servidor MCP remoto no cliente compatível.\n4. A chave só permite consultar os dados deste motorista.", 13f))
        }, top = 10))

        addView(UiKit.margin(mcpStatus, top = 10))
        addView(UiKit.margin(tokenName, top = 8))
        addView(UiKit.margin(UiKit.primaryButton(context, "Gerar nova chave MCP") { generateMcp() }, top = 8))

        secretCard.addView(UiKit.pill(context, "MOSTRADA UMA ÚNICA VEZ", "warn"))
        secretCard.addView(UiKit.margin(secretText, top = 9))
        secretCard.addView(UiKit.margin(UiKit.secondaryButton(context, "Copiar endpoint MCP") { copy("Endpoint MCP", lastEndpoint) }, top = 8))
        secretCard.addView(UiKit.margin(UiKit.secondaryButton(context, "Copiar chave Bearer") { copy("Chave MCP", lastSecret) }, top = 6))
        addView(UiKit.margin(secretCard, top = 10))

        addView(UiKit.margin(UiKit.body(context, "Chaves ativas", 13f).apply { setTypeface(typeface, Typeface.BOLD) }, top = 12))
        addView(activeTokens)
        addView(UiKit.margin(UiKit.secondaryButton(context, "Atualizar lista") { refreshMcp() }, top = 8))
        addView(UiKit.margin(UiKit.body(context, "Segurança: o servidor guarda apenas o hash da chave. Se você perder a chave, gere outra e revogue a antiga.", 11f), top = 8))
    }

    private fun ask() {
        val text = question.text.toString().trim()
        if (text.length < 3) {
            answer.text = "Digite uma pergunta com pelo menos 3 caracteres."
            return
        }
        if (repo.load().deviceToken.isBlank()) {
            answer.text = "Conecte sua conta/aparelho antes de usar a IA do Sr. Rotas."
            return
        }
        val days = listOf(1, 7, 30, 90)[period.selectedItemPosition]
        answer.text = "Analisando seus dados..."
        answerMeta.text = ""
        BackendClient.askEnhanced(context, text, days) { result ->
            result.onSuccess { response ->
                answer.text = response.answer
                answerMeta.text = buildString {
                    append("${response.offerCount} oferta(s) analisada(s) · ${response.model}")
                    if (response.totalTokens != null) append(" · ${response.totalTokens} tokens")
                    append("\nOs registros são ofertas observadas, não comprovam corridas realizadas.")
                }
            }.onFailure {
                answer.text = "Não foi possível consultar a IA: ${friendlyError(it.message)}"
            }
        }
    }

    private fun generateMcp() {
        if (repo.load().deviceToken.isBlank()) {
            mcpStatus.text = "Conecte sua conta/aparelho antes de gerar uma chave MCP."
            return
        }
        val name = tokenName.text.toString().trim().ifBlank { "Minha integração" }
        mcpStatus.text = "Gerando chave..."
        BackendClient.createMcpToken(context, name) { result ->
            result.onSuccess { created ->
                lastSecret = created.token
                lastEndpoint = created.endpoint
                secretText.text = "Endpoint:\n${created.endpoint}\n\nBearer token:\n${created.token}"
                secretCard.visibility = View.VISIBLE
                tokenName.setText("")
                mcpStatus.text = "Chave criada. Copie agora; o segredo completo não poderá ser consultado novamente."
                refreshMcp()
            }.onFailure { mcpStatus.text = "Falha ao gerar chave: ${friendlyError(it.message)}" }
        }
    }

    private fun refreshMcp() {
        if (repo.load().deviceToken.isBlank()) {
            mcpStatus.text = "Conta/aparelho ainda não conectado."
            activeTokens.removeAllViews()
            return
        }
        BackendClient.listMcpTokens(context) { result ->
            result.onSuccess { tokens ->
                activeTokens.removeAllViews()
                if (tokens.isEmpty()) {
                    activeTokens.addView(UiKit.body(context, "Nenhuma chave ativa.", 12f))
                } else {
                    tokens.forEach { token -> activeTokens.addView(UiKit.margin(tokenRow(token), top = 6)) }
                }
                mcpStatus.text = "${tokens.size} chave(s) MCP ativa(s)."
            }.onFailure { mcpStatus.text = "Não foi possível carregar chaves: ${friendlyError(it.message)}" }
        }
    }

    private fun tokenRow(token: BackendClient.McpTokenInfo): View = UiKit.card(context, 10).apply {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(UiKit.body(context, token.name, 14f).apply { setTypeface(typeface, Typeface.BOLD) })
            addView(UiKit.body(context, "${token.prefix}•••• · ${token.lastUsedAt?.let(::dateTime) ?: "ainda não usada"}", 11f))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiKit.pill(context, "Revogar", "bad").apply {
            setOnClickListener {
                BackendClient.revokeMcpToken(context, token.id) { result ->
                    result.onSuccess { refreshMcp() }.onFailure { mcpStatus.text = "Falha ao revogar: ${friendlyError(it.message)}" }
                }
            }
        })
        addView(row)
    }

    private fun caption(text: String) = UiKit.body(context, text, 12f).apply { setPadding(0, UiKit.dp(context, 8), 0, UiKit.dp(context, 3)) }

    private fun copy(label: String, text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        mcpStatus.text = "$label copiado."
    }

    private fun dateTime(value: String): String = runCatching {
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value))
    }.getOrDefault(value.take(16))

    private fun friendlyError(value: String?): String = when (value) {
        "openai_not_configured" -> "IA do Sr. Rotas ainda não configurada no servidor."
        "unauthorized" -> "sessão expirada; conecte o aparelho novamente."
        else -> value ?: "erro inesperado"
    }
}
