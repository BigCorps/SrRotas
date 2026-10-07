package com.srrotas.app

/** Ready/style não provam cartografia: somente fully-rendered após envio do destino. */
internal class RadarMapRenderGateV1 {
    var styleLoaded = false; private set
    var submitted = false; private set
    var firstFrame = false; private set
    var fullyRendered = false; private set
    var failed = false; private set
    var released = false; private set
    val fallbackVisible get() = !fullyRendered || failed || released
    val waiting get() = !fullyRendered && !failed && !released
    fun styleLoaded() { if (!released && !failed) styleLoaded = true }
    fun submitted() { if (styleLoaded && waiting) submitted = true }
    fun frame(surfaceReady: Boolean): Boolean {
        if (!surfaceReady || !styleLoaded || !submitted || !waiting || firstFrame) return false
        firstFrame = true
        return true
    }
    fun fully(fully: Boolean, surfaceReady: Boolean): Boolean {
        if (!fully || !surfaceReady || !styleLoaded || !submitted || !waiting) return false
        fullyRendered = true
        return true
    }
    fun fail(): Boolean {
        if (released || failed) return false
        failed = true
        return true
    }
    fun timeout(): Boolean = if (waiting) fail() else false
    fun release() { released = true }
}
