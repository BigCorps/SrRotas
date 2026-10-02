package com.srrotas.app
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
class DestinationRadarRuntimeV1Test {
 private val now=Instant.parse("2026-10-01T20:00:00Z").toEpochMilli()
 @Test fun cadence(){
  assertEquals(300_000L,DestinationRadarRuntimeV1.refreshDelayMs("2026-10-01T20:40:00Z",now))
  assertEquals(180_000L,DestinationRadarRuntimeV1.refreshDelayMs("2026-10-01T20:25:00Z",now))
  assertEquals(90_000L,DestinationRadarRuntimeV1.refreshDelayMs("2026-10-01T20:12:00Z",now))
  assertEquals(60_000L,DestinationRadarRuntimeV1.refreshDelayMs("2026-10-01T20:05:00Z",now))
 }
}
