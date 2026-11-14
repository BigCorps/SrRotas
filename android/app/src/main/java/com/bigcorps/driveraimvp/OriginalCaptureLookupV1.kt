package com.srrotas.app

import android.content.Context
import com.srrotas.app.r3lab.R3EvidenceStore
import com.srrotas.app.r3lab.R3LabDb

/** Read existing exact evidence only. No capture, OCR, inference, new retention or retrospective hash. */
internal object OriginalCaptureLookupV1 {
    const val UNAVAILABLE = "Captura original não disponível"
    const val DIGITIZED_REASON = "A digitalização do histórico Uber guardou os campos reconhecidos, sem imagem original vinculada a este registro. Nenhuma captura será atribuída por horário ou valor."
    data class Result(val snapshot: VerifiedCaptureSnapshotV1? = null, val reason: String, val frameId: String? = null,
        val observationId: String? = null, val capturedAtMs: Long? = null, val sha256: String? = null)

    fun resolve(context: Context, fullOfferId: String): Result = try {
        if (fullOfferId.isBlank()) Result(reason = "O registro não possui identificador de evidência.")
        else {
            val db = R3LabDb.get(context)
            val link = db.officialLink(fullOfferId)
            if (link == null) {
                val offer = LocalStore.get(context).offerByLocalId(fullOfferId)
                val indexed = PrivateScreenshotIndexV1.resolve(context, fullOfferId)
                Result(reason = when {
                    indexed != null -> "Existe um arquivo local vinculado pelo ID desta oferta, mas esse vínculo anterior não registrou um hash de origem verificável. Ele não será apresentado como captura original validada."
                    offer == null -> "Registro sem vínculo de imagem original. Não buscamos imagens de outras ofertas."
                    offer.captureMethod.startsWith("r3-") -> "Nenhum quadro original foi vinculado a esta oferta. Não é possível reconstruí-lo retroativamente."
                    else -> "Não há imagem original com vínculo e integridade verificáveis para esta oferta. Arquivos antigos da galeria não são associados automaticamente."
                })
            } else {
                val frame = db.frame(link.frameId)
                val (file, status) = R3EvidenceStore(context, db).verifiedFile(link.frameId)
                // verifiedFile checks the stored anchor; re-hash the bounded snapshot itself because
                // its source may have changed after that first check. Only this snapshot can be decoded.
                val captured = file?.let { VerifiedCaptureSnapshotV1.read(it, frame?.sha256.orEmpty()) }
                val snapshot = captured?.snapshot
                Result(snapshot, if (snapshot != null) "Integridade: SHA-256 conferido" else when {
                    captured?.failure == VerifiedCaptureSnapshotV1.Failure.TOO_LARGE -> "O arquivo excede o limite seguro de 8 MiB para visualização. Nenhuma cópia foi criada."
                    captured?.failure == VerifiedCaptureSnapshotV1.Failure.CHANGED -> "O arquivo mudou durante a consulta ou sua integridade não confere. A imagem não será exibida."
                    captured?.failure == VerifiedCaptureSnapshotV1.Failure.UNREADABLE -> "O arquivo original deixou de estar disponível durante a consulta."
                    frame?.lastError == "consent_off" -> "A imagem não foi armazenada porque o consentimento de imagem estava desativado na captura."
                    frame?.lastError == "consent_revoked" -> "O consentimento de imagem foi revogado antes de armazenar o quadro."
                    status.startsWith("PURGED") -> "A imagem foi removida pela política de retenção ou limpeza. O vínculo não será trocado por outra captura."
                    status == "MISSING" -> "O arquivo original vinculado não está mais disponível neste aparelho."
                    status == "sha256_divergente" -> "A integridade do arquivo não confere com o SHA-256 registrado. A imagem não será exibida."
                    status.startsWith("FAILED") -> "A imagem original não pôde ser armazenada nesta captura."
                    status.startsWith("STAGED") -> "O armazenamento desta imagem ainda não foi concluído."
                    else -> "Não foi possível verificar o arquivo original deste registro."
                }, link.frameId, link.observationId, frame?.capturedAtMs, snapshot?.sha256)
            }
        }
    } catch (_: Exception) { Result(reason = "Não foi possível consultar a captura original. O registro do Histórico continua disponível.") }
}
