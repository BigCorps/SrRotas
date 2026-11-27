package com.srrotas.app.r3lab

/**
 * BR-10: Only an observed candidate on an approved Uber/99 window may be
 * retained after a failed interpretation. No invented RideOffer, no OCR text
 * and no fallback association. Consent is checked again on the I/O thread.
 */
internal object R3UnparsedEvidenceGate {
    fun saveCandidateFrame(
        primaryReader: Boolean,
        cardDetected: Boolean,
        parsedCandidates: Int,
        imagesAllowed: Boolean,
    ): Boolean = primaryReader && cardDetected && parsedCandidates == 0 && imagesAllowed
}

/**
 * BR-08: Unexpected synchronous tick exceptions must not kill the Reader
 * scheduler. A run of errors is reported to the existing M1 fallback;
 * an isolated exception is not proof that the Reader has stopped.
 */
internal class R3TickFailureGate(private val threshold: Int = 3) {
    init { require(threshold > 0) }
    private var consecutive = 0
    fun onSuccess() { consecutive = 0 }
    fun onFailure(): Boolean {
        if (consecutive < threshold) consecutive++
        return consecutive >= threshold
    }
    fun reset() { consecutive = 0 }
}
