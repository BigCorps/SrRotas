package com.srrotas.app

enum class HistoricalTimeConfidence(val wire: String) {
    METADATA_TAKEN("metadata_taken"),
    EXIF("exif"),
    FILENAME("filename"),
    LAST_MODIFIED("last_modified"),
    UNKNOWN("unknown"),
}

data class HistoricalSourceTime(
    val observedAt: String,
    val confidence: HistoricalTimeConfidence,
)

data class HistoricalImportProgress(
    val current: Int,
    val total: Int,
    val displayName: String,
    val importedOffers: Int,
    val duplicateOffers: Int,
)

data class HistoricalImportResult(
    val selectedFiles: Int,
    val processedFiles: Int,
    val skippedFiles: Int,
    val noOfferFiles: Int,
    val failedFiles: Int,
    val importedOffers: Int,
    val duplicateOffers: Int,
)
