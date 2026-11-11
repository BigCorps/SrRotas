package com.srrotas.app.r3lab

import java.security.MessageDigest
import java.util.Collections

/**
 * Reader R3-Lab — núcleo puro (JVM). Origem: fundação Fase 0/1 preparada a partir do dossiê R3
 * (10/11/2026), revisada na integração. Nada aqui toca M1, HUD, LocalStore oficial ou backend.
 */
object R3AllowedPackages {
    const val UBER = "com.ubercab.driver"
    const val NINETY_NINE = "com.app99.driver"
    val ALL: Set<String> = setOf(UBER, NINETY_NINE)
    /** Pacotes do Sr. Rotas operacional (para detectar coexistência, nunca para capturar). */
    val OPERATIONAL: Set<String> = setOf("com.srrotas.app", "com.srrotas.app.debug")
    fun platform(pkg: String): String? = when (pkg) { UBER -> "uber"; NINETY_NINE -> "99"; else -> null }
}

data class R3LabConsent(
    val labEnabled: Boolean = false,
    val accessibilityDisclosureAccepted: Boolean = false,
    val imageRetentionConsent: Boolean = false,
)

data class R3LabEnvironment(
    val apiLevel: Int,
    /** BuildConfig.R3_LAB: verdadeiro somente no APK experimental `r3lab`. */
    val labBuild: Boolean,
    val accessibilityServiceEnabled: Boolean,
    val screenOn: Boolean,
    val officialM1CaptureActive: Boolean,
    val operationalAppVisible: Boolean,
    val anotherOcrRunning: Boolean,
    val targetPackage: String?,
    /**
     * Protocolo de campo (issue #6): a detecção automática do app operacional NÃO é confiável
     * (a Acessibilidade do lab só enxerga Uber/99). O motorista confirma que a leitura M1 do app
     * operacional está encerrada; a confirmação vale por tempo limitado.
     */
    val operationalM1StopConfirmed: Boolean = false,
)

enum class R3LabBlock {
    LAB_DISABLED, NOT_LAB_BUILD, DISCLOSURE_REQUIRED, ACCESSIBILITY_DISABLED, API_UNSUPPORTED,
    SCREEN_OFF, OFFICIAL_READER_ACTIVE, OPERATIONAL_M1_UNCONFIRMED, JOURNEY_INACTIVE, OPERATIONAL_APP_ACTIVE, OCR_BUSY, TARGET_UNVERIFIED,
}

object R3LabGate {
    /** Fail-closed. Não substitui as checagens de permissão/pacote do Android. */
    fun canCapture(consent: R3LabConsent, env: R3LabEnvironment): R3LabBlock? = when {
        !env.labBuild -> R3LabBlock.NOT_LAB_BUILD
        !consent.labEnabled -> R3LabBlock.LAB_DISABLED
        !consent.accessibilityDisclosureAccepted -> R3LabBlock.DISCLOSURE_REQUIRED
        !env.accessibilityServiceEnabled -> R3LabBlock.ACCESSIBILITY_DISABLED
        env.apiLevel < 30 -> R3LabBlock.API_UNSUPPORTED
        !env.screenOn -> R3LabBlock.SCREEN_OFF
        env.officialM1CaptureActive -> R3LabBlock.OFFICIAL_READER_ACTIVE
        !env.operationalM1StopConfirmed -> R3LabBlock.OPERATIONAL_M1_UNCONFIRMED
        env.operationalAppVisible -> R3LabBlock.OPERATIONAL_APP_ACTIVE
        env.anotherOcrRunning -> R3LabBlock.OCR_BUSY
        env.targetPackage !in R3AllowedPackages.ALL -> R3LabBlock.TARGET_UNVERIFIED
        else -> null
    }

    /** Gravação adiada (I/O): só se o serviço que capturou ainda é o vigente e o laboratório segue ativo. */
    fun mayPersistObservation(captureGeneration: Long, currentGeneration: Long, consentNow: R3LabConsent): Boolean =
        captureGeneration == currentGeneration && consentNow.labEnabled && consentNow.accessibilityDisclosureAccepted

    fun mayRetainImage(consent: R3LabConsent): Boolean =
        consent.labEnabled && consent.accessibilityDisclosureAccepted && consent.imageRetentionConsent
}

/** Regra de extração: regex com EXATAMENTE `captures.size` grupos, um por campo nomeado. */
data class R3ExtractionRule(val name: String, val expression: String, val captures: List<String>)

/** Imutável por construção; validado antes de existir. */
class R3ProfileBundle private constructor(
    val id: String,
    val generation: Long,
    val sha256: String,
    val targetPackage: String,
    val requiredFields: Set<String>,
    val rules: Map<String, Pair<Regex, List<String>>>,
) {
    val platform: String get() = R3AllowedPackages.platform(targetPackage)!!

    /** Aplica uma regra a UMA linha (entrada limitada a [MAX_INPUT] caracteres). */
    fun match(rule: String, line: String): Map<String, String>? {
        val (regex, captures) = rules[rule] ?: return null
        val m = regex.find(line.take(MAX_INPUT)) ?: return null
        val out = LinkedHashMap<String, String>()
        captures.forEachIndexed { i, name -> m.groups[i + 1]?.value?.takeIf { it.isNotBlank() }?.let { out[name] = it } }
        // Grupo esperado ausente = regra não casou de verdade (nunca usar o match inteiro como valor).
        return out.takeIf { it.size == captures.size }
    }

    companion object {
        const val MAX_INPUT = 240
        val LEGAL_FIELDS = setOf("fare", "pickup_km", "pickup_min", "trip_km", "trip_min", "total_km", "total_min",
            "pickup_address", "destination_address", "service_type", "advertised_per_km", "rating", "leg_h", "leg_min", "leg_km", "action")

        fun create(id: String, generation: Long, targetPackage: String, requiredFields: Set<String>,
                   rules: List<R3ExtractionRule>): R3ProfileBundle {
            require(id.isNotBlank() && generation > 0L) { "id/generation" }
            require(targetPackage in R3AllowedPackages.ALL) { "package" }
            require(rules.isNotEmpty() && rules.map { it.name }.toSet().size == rules.size) { "rules" }
            val captured = rules.flatMap { it.captures }.toSet()
            require(requiredFields.isNotEmpty() && requiredFields.all { it in LEGAL_FIELDS && it in captured }) { "required fields" }
            val compiled = rules.associate { rule ->
                require(rule.name.matches(Regex("[a-z][a-z0-9_]{1,40}"))) { "rule name" }
                require(rule.expression.length in 1..512) { "regex length invalid" }
                require(rule.captures.isNotEmpty() && rule.captures.all { it in LEGAL_FIELDS }) { "capture names" }
                val regex = Regex(rule.expression)
                require(regex.toPattern().matcher("").groupCount() == rule.captures.size) { "group count != captures for ${rule.name}" }
                rule.name to (regex to rule.captures.toList())
            }
            val canonical = buildString {
                append(id).append('\n').append(generation).append('\n').append(targetPackage).append('\n')
                requiredFields.sorted().forEach { append("required:").append(it).append('\n') }
                rules.sortedBy { it.name }.forEach { append(it.name).append('=').append(it.expression).append(" -> ").append(it.captures.joinToString(",")).append('\n') }
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            return R3ProfileBundle(id, generation, sha, targetPackage, Collections.unmodifiableSet(requiredFields.toSet()),
                Collections.unmodifiableMap(compiled))
        }
    }
}

/** validate → compile → troca atômica; capturas em andamento mantêm o bundle original (imutável). */
class R3ProfileManager {
    private val active = linkedMapOf<String, R3ProfileBundle>()
    private val previous = linkedMapOf<String, R3ProfileBundle>()
    private val lastIssuedGeneration = linkedMapOf<String, Long>()

    @Synchronized fun install(id: String, targetPackage: String, requiredFields: Set<String>, rules: List<R3ExtractionRule>): R3ProfileBundle {
        val nextGeneration = (lastIssuedGeneration[targetPackage] ?: 0L) + 1L
        val next = R3ProfileBundle.create(id, nextGeneration, targetPackage, requiredFields, rules) // lança antes de expor
        lastIssuedGeneration[targetPackage] = nextGeneration // nunca reutiliza geração, nem após rollback
        active[targetPackage]?.let { previous[targetPackage] = it }
        active[targetPackage] = next
        return next
    }

    @Synchronized fun activeFor(targetPackage: String): R3ProfileBundle? = active[targetPackage]

    @Synchronized fun rollback(targetPackage: String): Boolean {
        val backup = previous.remove(targetPackage) ?: return false
        active[targetPackage] = backup
        return true
    }
}

enum class R3SourceKind { WINDOW, DISPLAY_CROP }
enum class R3EvidenceStatus { STAGED, READY, FAILED, MISSING, PURGED }
enum class R3FieldOrigin { OBSERVED, RECOVERED, INFERRED, MISSING }
enum class R3EvidenceRole { PRIMARY, SUPPLEMENTAL }

/** Envelope congelado da operação: perfil, geração do serviço e token viajam juntos até o fim. */
data class R3CaptureEnvelope(
    val frameId: String,
    val operationToken: Long,
    val serviceGeneration: Long,
    val profile: R3ProfileBundle,
    val sourcePackage: String,
    val windowId: Int?,
    val capturedAtElapsedMs: Long,
    val capturedAtWallMs: Long,
    val sourceKind: R3SourceKind,
    val bounds: IntArray?,
) {
    init {
        require(frameId.isNotBlank() && operationToken > 0L && serviceGeneration > 0L)
        require(sourcePackage == profile.targetPackage)
        require(capturedAtElapsedMs >= 0L)
    }
}

data class R3FieldEvidence(
    val name: String,
    val originFrameId: String,
    val observationId: String,
    val value: String?,
    val origin: R3FieldOrigin,
    val confidence: Double,
    val bounds: IntArray? = null,
) {
    init {
        require(originFrameId.isNotBlank() && observationId.isNotBlank())
        require(confidence in 0.0..1.0)
        if (origin == R3FieldOrigin.MISSING) require(value == null)
    }
}

data class R3Observation(val observationId: String, val frameId: String, val bundleGeneration: Long) {
    init { require(observationId.isNotBlank() && frameId.isNotBlank() && bundleGeneration > 0) }
}

/** Oferta do LABORATÓRIO (aceita ou rejeitada com motivo); nunca vai a `local_offers`. */
data class R3LabOffer(val labOfferId: String, val candidateId: String, val accepted: Boolean) {
    init { require(labOfferId.isNotBlank() && candidateId.isNotBlank()) }
}

data class R3ExactEvidenceLink(val labOfferId: String, val frameId: String, val observationId: String, val role: R3EvidenceRole)

/** Um screenshot em voo. Callback de token vencido não libera token novo. */
class R3CaptureLease(private val clockMs: () -> Long, private val maxDurationMs: Long = 5_000L) {
    init { require(maxDurationMs > 0) }
    data class Token(val id: Long, val serviceGeneration: Long, val profileGeneration: Long, val deadlineMs: Long)
    private var serial = 0L
    private var current: Token? = null
    @Synchronized fun start(serviceGeneration: Long, profileGeneration: Long): Token? {
        require(serviceGeneration > 0 && profileGeneration > 0)
        if (current != null) return null // expiração é explícita (watchdog), nunca sobreposição implícita
        return Token(++serial, serviceGeneration, profileGeneration, clockMs() + maxDurationMs).also { current = it }
    }
    @Synchronized fun matches(token: Token): Boolean = current == token && clockMs() < token.deadlineMs
    @Synchronized fun expire(): Token? = current?.takeIf { clockMs() >= it.deadlineMs }?.also { current = null }
    @Synchronized fun finish(token: Token): Boolean { if (current != token) return false; current = null; return true }
    @Synchronized fun cancelService(serviceGeneration: Long) { if (current?.serviceGeneration == serviceGeneration) current = null }
    @Synchronized fun isBusy(): Boolean = current != null
}

/**
 * Contrato de vínculo exato em memória (verificador). A implementação durável é `R3LabDb`, que
 * segue as mesmas regras. Nunca usa tarifa/horário para vincular evidência.
 */
class R3LabEvidenceLedger {
    data class Frame(val frameId: String, val targetPackage: String, val status: R3EvidenceStatus, val bytesSha256: String?)
    private val frames = linkedMapOf<String, Frame>()
    private val observations = linkedMapOf<String, R3Observation>()
    private val offers = linkedMapOf<String, R3LabOffer>()
    private val links = linkedSetOf<R3ExactEvidenceLink>()

    companion object {
        val TRANSITIONS: Map<R3EvidenceStatus, Set<R3EvidenceStatus>> = mapOf(
            R3EvidenceStatus.STAGED to setOf(R3EvidenceStatus.READY, R3EvidenceStatus.FAILED, R3EvidenceStatus.MISSING, R3EvidenceStatus.PURGED),
            R3EvidenceStatus.READY to setOf(R3EvidenceStatus.FAILED, R3EvidenceStatus.MISSING, R3EvidenceStatus.PURGED),
            R3EvidenceStatus.FAILED to setOf(R3EvidenceStatus.MISSING, R3EvidenceStatus.PURGED),
            R3EvidenceStatus.MISSING to setOf(R3EvidenceStatus.PURGED),
            R3EvidenceStatus.PURGED to emptySet(),
        )
        val SHA256 = Regex("[0-9a-f]{64}")
    }

    @Synchronized fun stage(frameId: String, targetPackage: String) {
        require(frameId.isNotBlank() && frameId !in frames)
        require(targetPackage in R3AllowedPackages.ALL)
        frames[frameId] = Frame(frameId, targetPackage, R3EvidenceStatus.STAGED, null)
    }
    @Synchronized fun recordImage(frameId: String, sha256: String?, status: R3EvidenceStatus) {
        val old = requireNotNull(frames[frameId])
        require(status in TRANSITIONS.getValue(old.status)) { "invalid evidence status transition" }
        if (status == R3EvidenceStatus.READY) require(sha256?.matches(SHA256) == true)
        frames[frameId] = old.copy(status = status, bytesSha256 = if (status == R3EvidenceStatus.READY) sha256 else null)
    }
    @Synchronized fun observe(observation: R3Observation) {
        require(observation.observationId !in observations)
        require(observation.frameId in frames)
        observations[observation.observationId] = observation
    }
    /** Só após persistência bem-sucedida no banco ISOLADO do laboratório; nunca `saveOffer` oficial. */
    @Synchronized fun commitPersistedLabOffer(labOffer: R3LabOffer, evidence: List<Pair<String, R3EvidenceRole>>, persistedSuccessfully: Boolean): Boolean {
        if (!persistedSuccessfully) return false
        require(labOffer.labOfferId !in offers)
        require(evidence.isNotEmpty())
        require(evidence.map { it.first }.toSet().size == evidence.size)
        require(evidence.count { it.second == R3EvidenceRole.PRIMARY } == 1)
        val resolved = evidence.map { (observationId, role) ->
            val obs = requireNotNull(observations[observationId]) { "unknown observation" }
            val frame = requireNotNull(frames[obs.frameId])
            require(frame.status != R3EvidenceStatus.PURGED) { "purged frame cannot be newly linked" }
            frame to R3ExactEvidenceLink(labOffer.labOfferId, obs.frameId, obs.observationId, role)
        }
        require(resolved.map { it.first.targetPackage }.toSet().size == 1) { "evidence from different packages" }
        offers[labOffer.labOfferId] = labOffer
        links.addAll(resolved.map { it.second })
        return true
    }
    @Synchronized fun linksFor(labOfferId: String): List<R3ExactEvidenceLink> = links.filter { it.labOfferId == labOfferId }
    @Synchronized fun frameStatus(frameId: String): R3EvidenceStatus? = frames[frameId]?.status
    @Synchronized fun resolvedImage(labOfferId: String, observationId: String): Frame? {
        val exact = links.find { it.labOfferId == labOfferId && it.observationId == observationId } ?: return null
        return frames[exact.frameId]?.takeIf { it.status == R3EvidenceStatus.READY }
    }
}
