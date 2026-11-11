package com.srrotas.app.r3lab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Preferências do laboratório: tudo OFF por padrão; consentimento de imagem é separado. */
object R3LabSettings {
    private const val PREFS = "r3_lab_settings"
    const val MODE_C = "C" // captura R3 + parser oficial
    const val MODE_D = "D" // captura R3 + perfil R3 (validado pelo parser oficial)
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun consent(c: Context) = R3LabConsent(p(c).getBoolean("enabled", false), p(c).getBoolean("disclosure", false), p(c).getBoolean("images", false))
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()
    fun setDisclosure(c: Context, v: Boolean) = p(c).edit().putBoolean("disclosure", v).apply()
    fun setImages(c: Context, v: Boolean) = p(c).edit().putBoolean("images", v).apply()
    fun mode(c: Context) = p(c).getString("mode", MODE_C).takeIf { it == MODE_C || it == MODE_D } ?: MODE_C
    fun setMode(c: Context, v: String) = p(c).edit().putString("mode", if (v == MODE_D) MODE_D else MODE_C).apply()
    /** Confirmação de protocolo: leitura M1 do app operacional encerrada (vale [PROTOCOL_TTL_MS]). */
    const val PROTOCOL_TTL_MS = 12L * 3_600_000L
    fun confirmProtocol(c: Context, confirmed: Boolean) = p(c).edit().putLong("protocol_at", if (confirmed) System.currentTimeMillis() else 0L).apply()
    fun protocolConfirmed(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        val at = p(c).getLong("protocol_at", 0L)
        return at > 0L && now - at in 0..PROTOCOL_TTL_MS
    }
    fun retentionDays(c: Context) = p(c).getInt("retention_days", 7).coerceIn(1, 30)
    const val QUOTA_BYTES = 300L * 1024 * 1024
}

/** Banco EXPERIMENTAL isolado (`r3_lab.db`). Nunca escreve em `local_offers`, histórico ou backend. */
class R3LabDb private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, 2) {
    companion object {
        const val NAME = "r3_lab.db"
        @Volatile private var instance: R3LabDb? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: R3LabDb(context).also { instance = it } }
        internal fun resetForTest() { instance?.close(); instance = null }
    }

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE r3_frame (frame_id TEXT PRIMARY KEY, captured_at_ms INTEGER NOT NULL, source_package TEXT NOT NULL,
            window_id INTEGER, source_kind TEXT NOT NULL, bounds TEXT, profile_id TEXT NOT NULL, profile_generation INTEGER NOT NULL,
            profile_sha TEXT NOT NULL, operation_token INTEGER NOT NULL, service_generation INTEGER NOT NULL, width INTEGER NOT NULL,
            height INTEGER NOT NULL, storage_status TEXT NOT NULL, file_name TEXT, sha256 TEXT, last_error TEXT)""")
        db.execSQL("""CREATE TABLE r3_observation (observation_id TEXT PRIMARY KEY, frame_id TEXT NOT NULL REFERENCES r3_frame(frame_id),
            mode TEXT NOT NULL, line_count INTEGER NOT NULL, candidate_count INTEGER NOT NULL, ocr_ms INTEGER NOT NULL, created_at_ms INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE r3_lab_offer (lab_offer_id TEXT PRIMARY KEY, candidate_id TEXT NOT NULL UNIQUE, platform TEXT NOT NULL,
            mode TEXT NOT NULL, status TEXT NOT NULL CHECK (status IN ('ACCEPTED','REJECTED')), rejection TEXT, fields_json TEXT NOT NULL,
            fare REAL, created_at_ms INTEGER NOT NULL, label TEXT CHECK (label IS NULL OR label IN ('TP','FP')))""")
        db.execSQL("""CREATE TABLE r3_offer_evidence (lab_offer_id TEXT NOT NULL REFERENCES r3_lab_offer(lab_offer_id),
            frame_id TEXT NOT NULL REFERENCES r3_frame(frame_id), observation_id TEXT NOT NULL REFERENCES r3_observation(observation_id),
            role TEXT NOT NULL CHECK (role IN ('PRIMARY','SUPPLEMENTAL')), field_origins_json TEXT, PRIMARY KEY (lab_offer_id, frame_id))""")
        db.execSQL("CREATE TABLE r3_ground_truth (id TEXT PRIMARY KEY, kind TEXT NOT NULL, platform TEXT, created_at_ms INTEGER NOT NULL, note TEXT)")
        db.execSQL("CREATE INDEX r3_evidence_frame ON r3_offer_evidence(frame_id)")
        createOfficialLink(db)
    }

    /** vc112: vínculo da oferta OFICIAL gravada (`local_offers.local_id`) ao quadro/observação exatos do R3. */
    private fun createOfficialLink(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS r3_official_link (offer_local_id TEXT PRIMARY KEY,
            frame_id TEXT NOT NULL REFERENCES r3_frame(frame_id), observation_id TEXT NOT NULL REFERENCES r3_observation(observation_id),
            created_at_ms INTEGER NOT NULL)""")
    }

    /** Migração aditiva (lab v1 → v2): nada é apagado. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) createOfficialLink(db) }

    /** Grava o vínculo oficial; recusa observação que não pertence ao quadro (FK + checagem). */
    fun linkOfficial(offerLocalId: String, frameId: String, observationId: String): Boolean {
        val owner = readableDatabase.rawQuery("SELECT frame_id FROM r3_observation WHERE observation_id=?", arrayOf(observationId)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null }
        if (owner != frameId) return false
        return writableDatabase.insertWithOnConflict("r3_official_link", null, ContentValues().apply {
            put("offer_local_id", offerLocalId); put("frame_id", frameId); put("observation_id", observationId); put("created_at_ms", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    data class OfficialLink(val offerLocalId: String, val frameId: String, val observationId: String)
    fun officialLink(offerLocalId: String): OfficialLink? = readableDatabase.rawQuery(
        "SELECT offer_local_id, frame_id, observation_id FROM r3_official_link WHERE offer_local_id=?", arrayOf(offerLocalId)).use { c ->
        if (c.moveToFirst()) OfficialLink(c.getString(0), c.getString(1), c.getString(2)) else null }

    data class FrameRow(val frameId: String, val capturedAtMs: Long, val sourcePackage: String, val sourceKind: String, val bounds: String?,
                        val profileId: String, val profileGeneration: Long, val width: Int, val height: Int, val status: R3EvidenceStatus,
                        val fileName: String?, val sha256: String?, val lastError: String?)
    data class OfferRow(val labOfferId: String, val candidateId: String, val platform: String, val mode: String, val accepted: Boolean,
                        val rejection: String?, val fieldsJson: String, val fare: Double?, val createdAtMs: Long, val label: String?)
    data class LinkRow(val labOfferId: String, val frameId: String, val observationId: String, val role: R3EvidenceRole, val fieldOrigins: String?)

    fun insertFrame(e: R3CaptureEnvelope, width: Int, height: Int, status: R3EvidenceStatus, error: String?) {
        writableDatabase.insertOrThrow("r3_frame", null, ContentValues().apply {
            put("frame_id", e.frameId); put("captured_at_ms", e.capturedAtWallMs); put("source_package", e.sourcePackage)
            put("window_id", e.windowId); put("source_kind", e.sourceKind.name); put("bounds", e.bounds?.joinToString(","))
            put("profile_id", e.profile.id); put("profile_generation", e.profile.generation); put("profile_sha", e.profile.sha256)
            put("operation_token", e.operationToken); put("service_generation", e.serviceGeneration)
            put("width", width); put("height", height); put("storage_status", status.name); put("last_error", error)
        })
    }

    /** Transições válidas do ledger; READY exige SHA-256 dos bytes guardados. */
    @Synchronized fun updateImage(frameId: String, status: R3EvidenceStatus, fileName: String?, sha256: String?, error: String?): Boolean {
        val current = frame(frameId)?.status ?: return false
        if (status !in R3LabEvidenceLedger.TRANSITIONS.getValue(current)) return false
        if (status == R3EvidenceStatus.READY && (sha256?.matches(R3LabEvidenceLedger.SHA256) != true || fileName == null)) return false
        writableDatabase.update("r3_frame", ContentValues().apply {
            put("storage_status", status.name); put("file_name", if (status == R3EvidenceStatus.READY) fileName else null)
            put("sha256", if (status == R3EvidenceStatus.READY) sha256 else null); put("last_error", error)
        }, "frame_id=?", arrayOf(frameId))
        return true
    }

    /** Quadro + observação na MESMA transação (nunca um sem o outro). */
    fun insertFrameWithObservation(e: R3CaptureEnvelope, width: Int, height: Int, status: R3EvidenceStatus, error: String?,
                                   observationId: String, mode: String, lines: Int, candidates: Int, ocrMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertFrame(e, width, height, status, error)
            insertObservation(observationId, e.frameId, mode, lines, candidates, ocrMs)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun insertObservation(observationId: String, frameId: String, mode: String, lines: Int, candidates: Int, ocrMs: Long) {
        writableDatabase.insertOrThrow("r3_observation", null, ContentValues().apply {
            put("observation_id", observationId); put("frame_id", frameId); put("mode", mode); put("line_count", lines)
            put("candidate_count", candidates); put("ocr_ms", ocrMs); put("created_at_ms", System.currentTimeMillis())
        })
    }

    /**
     * Commit atômico: oferta do laboratório + vínculos exatos (frameId, observationId) das observações
     * que de fato a compuseram. Recusa vínculo a frame PURGED ou de outro pacote. Retorna o ID gravado.
     */
    fun commitOffer(stable: R3Stabilizer.Stable, mode: String): String? {
        val db = writableDatabase
        val id = UUID.randomUUID().toString()
        db.beginTransaction()
        try {
            val packages = mutableSetOf<String>()
            for (seen in stable.observations) {
                val f = frame(seen.frameId) ?: return null
                if (f.status == R3EvidenceStatus.PURGED) return null
                packages += f.sourcePackage
            }
            if (packages.size != 1) return null
            val fields = JSONObject().apply {
                stable.fields.forEach { (k, v) -> put(k, JSONObject().put("value", v.value).put("origin", v.origin.name)
                    .put("frame_id", v.originFrameId).put("observation_id", v.observationId).put("confidence", v.confidence)
                    .put("bounds", v.bounds?.joinToString(","))) }
            }
            db.insertOrThrow("r3_lab_offer", null, ContentValues().apply {
                put("lab_offer_id", id); put("candidate_id", stable.candidateId); put("platform", stable.platform); put("mode", mode)
                put("status", if (stable.accepted) "ACCEPTED" else "REJECTED"); put("rejection", stable.rejection)
                put("fields_json", fields.toString()); put("fare", stable.fields["fare"]?.value?.replace(',', '.')?.toDoubleOrNull())
                put("created_at_ms", System.currentTimeMillis())
            })
            stable.observations.forEachIndexed { i, seen ->
                val origins = JSONObject().apply { stable.fields.filter { it.value.observationId == seen.observationId }.forEach { (k, v) -> put(k, v.origin.name) } }
                db.insertWithOnConflict("r3_offer_evidence", null, ContentValues().apply {
                    put("lab_offer_id", id); put("frame_id", seen.frameId); put("observation_id", seen.observationId)
                    put("role", if (i == 0) "PRIMARY" else "SUPPLEMENTAL"); put("field_origins_json", origins.toString())
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun frame(frameId: String): FrameRow? = readableDatabase.rawQuery("SELECT * FROM r3_frame WHERE frame_id=?", arrayOf(frameId)).use { c ->
        if (!c.moveToFirst()) null else FrameRow(c.getString(c.getColumnIndexOrThrow("frame_id")), c.getLong(c.getColumnIndexOrThrow("captured_at_ms")),
            c.getString(c.getColumnIndexOrThrow("source_package")), c.getString(c.getColumnIndexOrThrow("source_kind")),
            c.getString(c.getColumnIndexOrThrow("bounds")), c.getString(c.getColumnIndexOrThrow("profile_id")),
            c.getLong(c.getColumnIndexOrThrow("profile_generation")), c.getInt(c.getColumnIndexOrThrow("width")), c.getInt(c.getColumnIndexOrThrow("height")),
            R3EvidenceStatus.valueOf(c.getString(c.getColumnIndexOrThrow("storage_status"))), c.getString(c.getColumnIndexOrThrow("file_name")),
            c.getString(c.getColumnIndexOrThrow("sha256")), c.getString(c.getColumnIndexOrThrow("last_error")))
    }

    fun offers(limit: Int = 100): List<OfferRow> = readableDatabase.rawQuery("SELECT * FROM r3_lab_offer ORDER BY created_at_ms DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
        buildList { while (c.moveToNext()) add(OfferRow(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4) == "ACCEPTED",
            c.getString(5), c.getString(6), if (c.isNull(7)) null else c.getDouble(7), c.getLong(8), c.getString(9))) }
    }

    fun links(labOfferId: String): List<LinkRow> = readableDatabase.rawQuery(
        "SELECT lab_offer_id, frame_id, observation_id, role, field_origins_json FROM r3_offer_evidence WHERE lab_offer_id=? ORDER BY role, frame_id", arrayOf(labOfferId)).use { c ->
        buildList { while (c.moveToNext()) add(LinkRow(c.getString(0), c.getString(1), c.getString(2), R3EvidenceRole.valueOf(c.getString(3)), c.getString(4))) }
    }

    fun setLabel(labOfferId: String, label: String?) {
        writableDatabase.update("r3_lab_offer", ContentValues().apply { put("label", label) }, "lab_offer_id=?", arrayOf(labOfferId))
    }

    fun addMissedOffer(platform: String?) {
        writableDatabase.insert("r3_ground_truth", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("kind", "FN"); put("platform", platform); put("created_at_ms", System.currentTimeMillis())
        })
    }

    data class Labels(val tp: Int, val fp: Int, val fn: Int, val unlabeled: Int)
    fun labels(): Labels = readableDatabase.rawQuery("""SELECT
        (SELECT COUNT(*) FROM r3_lab_offer WHERE status='ACCEPTED' AND label='TP'),
        (SELECT COUNT(*) FROM r3_lab_offer WHERE status='ACCEPTED' AND label='FP'),
        (SELECT COUNT(*) FROM r3_ground_truth WHERE kind='FN'),
        (SELECT COUNT(*) FROM r3_lab_offer WHERE status='ACCEPTED' AND label IS NULL)""", null).use { c ->
        c.moveToFirst(); Labels(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3))
    }

    data class EvidenceCoverage(val acceptedOffers: Int, val withReadyPrimary: Int, val framesReady: Int, val framesFailed: Int, val framesPurged: Int)
    fun coverage(): EvidenceCoverage = readableDatabase.rawQuery("""SELECT
        (SELECT COUNT(*) FROM r3_lab_offer WHERE status='ACCEPTED'),
        (SELECT COUNT(*) FROM r3_lab_offer o JOIN r3_offer_evidence e ON e.lab_offer_id=o.lab_offer_id AND e.role='PRIMARY'
            JOIN r3_frame f ON f.frame_id=e.frame_id WHERE o.status='ACCEPTED' AND f.storage_status='READY'),
        (SELECT COUNT(*) FROM r3_frame WHERE storage_status='READY'),
        (SELECT COUNT(*) FROM r3_frame WHERE storage_status='FAILED'),
        (SELECT COUNT(*) FROM r3_frame WHERE storage_status='PURGED')""", null).use { c ->
        c.moveToFirst(); EvidenceCoverage(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4))
    }

    /** Frames com imagem que nunca foram vinculados a uma oferta do laboratório (candidatos descartados). */
    fun unlinkedFramesBefore(cutoffMs: Long): List<String> = readableDatabase.rawQuery(
        "SELECT frame_id FROM r3_frame f WHERE captured_at_ms < ? AND storage_status IN ('STAGED','READY') AND NOT EXISTS (SELECT 1 FROM r3_offer_evidence e WHERE e.frame_id=f.frame_id) AND NOT EXISTS (SELECT 1 FROM r3_official_link o WHERE o.frame_id=f.frame_id)",
        arrayOf(cutoffMs.toString())).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun readyFramesOldestFirst(): List<Pair<String, Long>> = readableDatabase.rawQuery(
        "SELECT frame_id, captured_at_ms FROM r3_frame WHERE storage_status='READY' ORDER BY captured_at_ms", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }
}

/**
 * Evidência visual privada: bytes sem perda (WEBP lossless, API 30+) em `files/r3lab/frames`,
 * escrita em `.tmp` + fsync + rename, SHA-256 dos BYTES GUARDADOS. Falha de arquivo nunca derruba
 * a oferta nem o OCR. Sem consentimento de imagem nada é gravado em disco.
 */
class R3EvidenceStore(private val context: Context, private val db: R3LabDb = R3LabDb.get(context)) {
    val dir: File get() = File(context.filesDir, "r3lab/frames").apply { mkdirs() }

    fun persist(frameId: String, bitmap: Bitmap): Boolean {
        val target = File(dir, "$frameId.webp")
        val tmp = File(dir, "$frameId.webp.tmp")
        return try {
            require(target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "path" }
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(tmp).use { fos ->
                DigestOutputStream(fos, digest).use { out ->
                    @Suppress("DEPRECATION")
                    val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.PNG
                    check(bitmap.compress(format, 100, out)) { "compress" }
                    out.flush(); fos.fd.sync()
                }
            }
            check(tmp.renameTo(target)) { "rename" }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            db.updateImage(frameId, R3EvidenceStatus.READY, target.name, sha, null)
        } catch (t: Throwable) {
            tmp.delete()
            db.updateImage(frameId, R3EvidenceStatus.FAILED, null, null, "write_${t.javaClass.simpleName}")
            false
        }
    }

    /** Arquivo exato do frame, somente se READY e com SHA-256 conferido; ausência vira MISSING. */
    fun verifiedFile(frameId: String): Pair<File?, String> {
        val row = db.frame(frameId) ?: return null to "frame_desconhecido"
        if (row.status != R3EvidenceStatus.READY) return null to row.status.name + (row.lastError?.let { " · $it" } ?: "")
        val file = File(dir, row.fileName ?: return null to "sem_arquivo")
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) return null to "caminho_invalido"
        if (!file.isFile) { db.updateImage(frameId, R3EvidenceStatus.MISSING, null, null, "file_missing"); return null to "MISSING" }
        val sha = MessageDigest.getInstance("SHA-256").let { md -> file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }; md.digest().joinToString("") { "%02x".format(it) } }
        return if (sha == row.sha256) file to "sha256_ok" else null to "sha256_divergente"
    }

    fun purge(frameId: String, reason: String) {
        db.frame(frameId)?.fileName?.let { File(dir, it).delete() }
        db.updateImage(frameId, R3EvidenceStatus.PURGED, null, null, reason)
    }

    /** Retenção: descarta não vinculados (2 min), mais antigos que N dias e excesso de quota. */
    fun enforceRetention(nowMs: Long = System.currentTimeMillis()) {
        db.unlinkedFramesBefore(nowMs - 600_000L).forEach { purge(it, "unlinked") }
        val cutoff = nowMs - R3LabSettings.retentionDays(context) * 86_400_000L
        val ready = db.readyFramesOldestFirst()
        ready.filter { it.second < cutoff }.forEach { purge(it.first, "retention_days") }
        var total = dir.listFiles()?.filter { it.name.endsWith(".webp") }?.sumOf { it.length() } ?: 0L
        for ((frameId, _) in db.readyFramesOldestFirst()) {
            if (total <= R3LabSettings.QUOTA_BYTES) break
            val size = db.frame(frameId)?.fileName?.let { File(dir, it).length() } ?: 0L
            purge(frameId, "quota"); total -= size
        }
        dir.listFiles()?.filter { it.name.endsWith(".tmp") && nowMs - it.lastModified() > 60_000L }?.forEach { it.delete() }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        db.readyFramesOldestFirst().forEach { db.updateImage(it.first, R3EvidenceStatus.PURGED, null, null, "user_cleared") }
    }
}
