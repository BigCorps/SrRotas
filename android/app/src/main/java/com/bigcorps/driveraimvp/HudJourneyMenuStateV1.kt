package com.srrotas.app

internal class HudJourneyMenuStateV1 {
    var expanded = false; private set
    fun toggle() { expanded = !expanded }
    fun close() { expanded = false }
}
