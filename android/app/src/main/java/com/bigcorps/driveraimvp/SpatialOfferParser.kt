package com.srrotas.app

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

object SpatialOfferParser {
    private data class OcrLine(val text: String, val box: Rect)

    fun parse(result: Text, sourcePackage: String, captureMethod: String, settings: DriverSettings, frameWidth: Int, frameHeight: Int): List<RideOffer> {
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box=line.boundingBox ?: return@mapNotNull null; val text=line.text.trim(); if(text.isBlank()) null else OcrLine(text,box)
        }
        if(lines.isEmpty()) return emptyList()
        val globalText=lines.sortedBy { it.box.top }.joinToString("\n") { it.text }
        val primaryFares=lines.filter { UberOfferDetector.isPrimaryFareLine(it.text) }
        if(primaryFares.isEmpty()) return OfferParser.parse(globalText,sourcePackage,captureMethod,settings,0.58,"exclusive")?.let(::listOf) ?: emptyList()

        val globalIsRadar = globalText.contains("radar de viagens", true) || globalText.contains("selecionar", true)
        if(primaryFares.size == 1 || !globalIsRadar) {
            return OfferParser.parse(globalText,sourcePackage,captureMethod,settings,estimateConfidence(globalText,false),if(globalIsRadar)"radar" else "exclusive")?.let(::listOf) ?: emptyList()
        }

        val maxVertical=(frameHeight*0.38).toInt().coerceAtLeast(300); val maxHorizontal=(frameWidth*0.46).toInt().coerceAtLeast(280)
        return primaryFares.mapNotNull { fareLine ->
            val cx=fareLine.box.centerX(); val cy=fareLine.box.centerY()
            val cluster=lines.filter { line ->
                val dx=abs(line.box.centerX()-cx); val dy=abs(line.box.centerY()-cy)
                dx<=maxHorizontal && dy<=maxVertical && (!UberOfferDetector.isPrimaryFareLine(line.text) || line===fareLine)
            }.sortedBy { it.box.top }
            val text=cluster.joinToString("\n") { it.text }
            OfferParser.parse(text,sourcePackage,captureMethod,settings,estimateConfidence(text,true),"radar")
        }.distinctBy { it.dedupeKey }
    }

    private fun estimateConfidence(text:String, clustered:Boolean):Double {
        var score=if(clustered)0.66 else 0.62
        if(text.contains("aceitar",true)||text.contains("selecionar",true))score+=0.08
        if(text.contains("uberx",true)||text.contains("priority",true))score+=0.06
        if(Regex("[0-9]+\\s*(?:min|minutos?)\\s*\\([^)]*km",RegexOption.IGNORE_CASE).findAll(text).count()>=2)score+=0.10
        if(Regex("R\\$[^\\n]*/\\s*km",RegexOption.IGNORE_CASE).containsMatchIn(text))score+=0.06
        return score.coerceAtMost(0.98)
    }
}
