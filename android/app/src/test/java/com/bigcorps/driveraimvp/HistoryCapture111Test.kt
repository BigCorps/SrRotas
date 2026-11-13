package com.srrotas.app

import android.app.Application
import android.graphics.Rect
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/**
 * vc111 P0 — Histórico Uber (fotos de campo de 05/11/2026, SM-X626B em tela dividida).
 * Layout real: card começa na tarifa ("R$ 38,13 ↑ Aumentou"), linha "Comfort · 22 min 55 segundos ·
 * 5.87" + "km" e o horário "7:35" isolado à DIREITA; depois "R$ 5,25 Preço dinâmico", mapa com
 * rótulos soltos, busca e destino (quebrados em duas linhas). A cauda do card anterior (dinâmico,
 * mapa e endereços) aparece ACIMA da tarifa no topo do quadro. Endereços anonimizados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryCapture111Test {
    private val at = Instant.parse("2026-11-05T16:30:00Z")
    private fun l(text: String, top: Int, left: Int = 40, h: Int = 34) = SpatialOcrLine(text, Rect(left, top, left + 15 * text.length, top + h))

    private val tailOfPreviousCard = listOf(
        l("qui., 5 de nov.", 70), l("R$ 4,50 Preço dinâmico", 130), l("São Paulo", 190, 330), l("BUTANTÃ", 230, 120),
        l("Rua Alfa Fictícia, Bairro Um - São Paulo - SP, 01000-100,", 300), l("Brasil", 336),
        l("R. Beta Fictícia, Bairro Dois - São Paulo - SP, 01000-200, Brasil", 380),
    )
    private val cardHeader = listOf(
        l("R$ 38,13", 460, 110), l("↑ Aumentou", 460, 260),
        l("Comfort · 22 min 55 segundos · 5.87", 506, 110), l("km", 542, 110), l("7:35", 520, 640, 30),
    )
    private val cardBody = listOf(
        l("R$ 5,25", 600), l("Preço dinâmico", 600, 170), l("Ferraz de Vasconcelos", 650, 520), l("Cotia", 700, 150), l("330", 640, 260),
        l("Av. Exemplo Gama, Bairro Três - São Paulo - SP,", 760), l("01000-300, Brasil", 796),
        l("Rua Delta Exemplo, Bairro Quatro - São Paulo - S", 850), l("01000-400, Brasil", 886),
    )
    private fun local(r: UberCompletedRide026) = Instant.parse(r.occurredAt).atZone(ZoneId.of("America/Sao_Paulo")).toLocalDateTime()

    @Test fun timeInTheRightColumnJoinsItsCardAndStatusClockDoesNot() {
        val text = UberHistoryLayout111.text(listOf(l("13:46", 4, 20, 24)) + tailOfPreviousCard + cardHeader + cardBody)
        assertTrue(text.contains("às 7:35"))
        assertTrue("status bar clock is not attached to a card", !text.contains("às 13:46"))
        // "R$ 5,25" + "Preço dinâmico" (mesma faixa visual) viram uma linha: não é tarifa.
        assertTrue(text.contains("R$ 5,25 Preço dinâmico"))
        // Distância "5.87" + "km" continua contígua (o horário entra depois da linha "km").
        assertTrue(text.contains("5.87\nkm\nàs 7:35"))
    }

    @Test fun fullFrameReadsTimeDestinationAndIgnoresMapAndPreviousTail() {
        val raw = UberHistoryLayout111.text(tailOfPreviousCard + cardHeader + cardBody)
        val rides = UberDigitizationParser026.parseRidesFrame(UberDigitizationText0265.normalize(raw), at, relaxed = true)
        val ride = rides.single()
        assertEquals(38.13, ride.fare, 0.001)
        assertEquals("comfort", ride.serviceType)
        assertEquals(7, local(ride).hour); assertEquals(35, local(ride).minute); assertEquals(5, local(ride).dayOfMonth)
        assertEquals(22 * 60 + 55, ride.durationSeconds); assertEquals(5.87, ride.distanceKm!!, 0.001)
        assertEquals(5.25, ride.surgeAmount!!, 0.001)
        assertTrue(ride.pickupLabel!!.startsWith("Av. Exemplo Gama")); assertTrue(ride.pickupLabel!!.contains("01000-300"))
        assertTrue(ride.destinationLabel!!.startsWith("Rua Delta Exemplo")); assertTrue(ride.destinationLabel!!.contains("01000-400"))
        // A cauda do card anterior (acima da tarifa) nunca vira busca/destino deste card.
        assertFalse(rides.any { it.pickupLabel?.contains("Alfa") == true || it.destinationLabel?.contains("Beta") == true })
    }

    @Test fun mlKitConcatenatedTextWithoutGeometryStillDropsTheLoneTime() {
        // Regressão da causa: sem a geometria, "7:35" sozinho na linha era descartado como relógio.
        val plain = (tailOfPreviousCard + cardHeader + cardBody).joinToString("\n") { it.text }
        val ride = UberDigitizationParser026.parseRidesFrame(UberDigitizationText0265.normalize(plain), at, relaxed = true).single()
        assertNull(ride.occurredAt)
    }

    @Test fun mapLabelsWithStreetNamesAreNotAddresses() {
        val body = listOf(l("R$ 22,40", 100), l("UberX · 15 min 2 segundos · 4.10 km", 140), l("9:12", 150, 640, 30),
            l("Av. Paulista Mapa", 220, 300), l("Marginal Exemplo", 260, 150),
            l("Rua Épsilon Teste, Bairro Cinco - São Paulo - SP, 01000-500, Brasil", 330))
        val ride = UberDigitizationParser026.parseRidesFrame(UberDigitizationText0265.normalize(UberHistoryLayout111.text(body)), at).single()
        assertTrue(ride.pickupLabel!!.startsWith("Rua Épsilon"))
        assertNull("only one real address: destination unavailable, never a map label", ride.destinationLabel)
    }

    @Test fun partialScrollCombinesTheCutCardWithTheTailOfTheNextFrame() {
        // Quadro 1: o card corta logo após o cabeçalho (só tarifa, serviço e horário visíveis).
        val frame1 = UberHistoryLayout111.text(tailOfPreviousCard + cardHeader)
        // Quadro 2 (após rolar): cabeçalho fixo da data; o topo do card ficou sob ele; vem a cauda e o próximo card.
        val next = listOf(l("R$ 19,90", 560, 110), l("UberX · 12 min 3 segundos · 3.20 km", 606, 110), l("8:02", 616, 640, 30),
            l("Rua Zeta Fictícia, Bairro Seis - São Paulo - SP, 01000-600, Brasil", 700),
            l("Av. Eta Fictícia, Bairro Sete - São Paulo - SP, 01000-700, Brasil", 760))
        val frame2 = UberHistoryLayout111.text(listOf(l("qui., 5 de nov.", 40), l("Comfort · 22 min 55 segundos · 5.87", 80, 110), l("km", 116, 110)) +
            cardBody.map { SpatialOcrLine(it.text, Rect(it.box.left, it.box.top - 480, it.box.right, it.box.bottom - 480)) } + next)
        val result = UberHistoryScanAccumulator0262.parseFrames(listOf(frame1, frame2).map { UberDigitizationText0265.normalize(it) }, at)
        val comfort = result.rides.single { it.serviceType == "comfort" }
        assertTrue(comfort.pickupLabel!!.startsWith("Av. Exemplo Gama"))
        assertTrue(comfort.destinationLabel!!.startsWith("Rua Delta Exemplo"))
        assertEquals(5.25, comfort.surgeAmount!!, 0.001)
        assertEquals(1, result.combinedFromNextFrame)
        val uberx = result.rides.single { it.serviceType == "uberx" }
        assertTrue(uberx.pickupLabel!!.startsWith("Rua Zeta")); assertTrue(uberx.destinationLabel!!.startsWith("Av. Eta"))
        assertEquals(2, result.rides.size)
    }

    @Test fun tailWithoutSharedEvidenceIsNotAttached() {
        val frame1 = UberHistoryLayout111.text(tailOfPreviousCard + cardHeader)
        // Rolagem longa: a cauda no topo não repete nenhuma linha distintiva do card cortado.
        val unrelated = listOf(l("R$ 9,10 Preço dinâmico", 60), l("Rua Teta Fictícia, Bairro Oito - São Paulo - SP, 01000-800, Brasil", 120),
            l("R. Iota Fictícia, Bairro Nove - São Paulo - SP, 01000-900, Brasil", 170), l("R$ 12,00", 260, 110),
            l("UberX · 9 min 1 segundos · 2.00 km", 300, 110), l("6:40", 310, 640, 30))
        val result = UberHistoryScanAccumulator0262.parseFrames(listOf(frame1, UberHistoryLayout111.text(unrelated)).map { UberDigitizationText0265.normalize(it) }, at)
        val comfort = result.rides.single { it.serviceType == "comfort" }
        assertNull(comfort.pickupLabel); assertNull(comfort.destinationLabel)
        assertEquals(0, result.combinedFromNextFrame)
    }

    @Test fun sameRideReadInTwoFramesIsOneRecordWithComplementaryFields() {
        // Frame A: horário + busca; frame B: horário + duração (as fotos mostram R$ 37,17 duas vezes).
        val a = "qui., 5 de nov.\nR$ 37,17\nComfort\nàs 7:10\nRua Kapa Teste, Bairro Dez - São Paulo - SP, 01001-000, Brasil"
        val b = "qui., 5 de nov.\nR$ 37,17\nComfort · 30 min\nàs 7:10"
        val result = UberHistoryScanAccumulator0262.parseFrames(listOf(a, b), at)
        val ride = result.rides.single()
        assertEquals(30 * 60, ride.durationSeconds); assertTrue(ride.pickupLabel!!.startsWith("Rua Kapa"))
    }

    @Test fun moneyOnlyLineFollowedByDynamicLabelIsNotAFare() {
        val text = "qui., 5 de nov.\nR$ 30,00\nUberX · 20 min · 6.0 km\nàs 10:01\nR$ 3,10\nPreço dinâmico\n" +
            "Rua Lambda Teste, Bairro Onze - São Paulo - SP, 01001-100, Brasil\nAv. Mi Teste, Bairro Doze - São Paulo - SP, 01001-200, Brasil"
        val ride = UberDigitizationParser026.parseRidesFrame(text, at).single()
        assertEquals(30.0, ride.fare, 0.001); assertEquals(3.10, ride.surgeAmount!!, 0.001)
        assertTrue(ride.destinationLabel!!.startsWith("Av. Mi"))
    }

    @Test fun incompleteRecordsAreNotPreselectedButCanBeChosen() {
        val complete = UberCompletedRide026("c", at.toString(), "2026-11-05T10:35:00Z", 38.13, "comfort", "Rua A, B - São Paulo - SP", "Rua C, D - São Paulo - SP", 0.74, 1375, 5.87)
        val noDest = complete.copy(sourceKey = "n", occurredAt = null, fare = 37.17, destinationLabel = null)
        val entries = HistoryReviewRules109.entries(listOf(complete, noDest), emptyList()).associateBy { it.ride.sourceKey }
        assertTrue(entries["c"]!!.selectedByDefault)
        assertEquals(HistoryReviewRules109.Confidence.MEDIA, entries["n"]!!.confidence)
        assertFalse("medium confidence alone no longer preselects an incomplete record", entries["n"]!!.selectedByDefault)
    }

    @Test fun noticesOnlyForNewReliableRidesWithRateLimit() {
        val n = UberHistoryNotice111()
        val r1 = UberCompletedRide026("k1", at.toString(), "2026-11-05T10:35:00Z", 25.0, "comfort", null, null, 0.8)
        assertEquals("Comfort R$ 25,00 capturada", n.onRide(r1, 1_000))
        assertNull("same ride in the next frame", n.onRide(r1.copy(sourceKey = "k1b"), 1_800))
        assertNull("unknown service / no time is not announced", n.onRide(r1.copy(sourceKey = "x", serviceType = "unknown", occurredAt = null), 5_000))
        val r2 = r1.copy(sourceKey = "k2", occurredAt = "2026-11-05T10:05:00Z", fare = 18.0)
        val r3 = r1.copy(sourceKey = "k3", occurredAt = "2026-11-05T09:40:00Z", fare = 12.0)
        assertNull(n.onRide(r2, 2_000)); assertNull(n.onRide(r3, 2_100))
        assertEquals("2 corridas capturadas", n.flush(3_600))
        assertEquals("Corrida R$ 25,00 capturada", UberHistoryNotice111.message(r1.copy(serviceType = "unknown")))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryReview111Test {
    private fun texts(v: android.view.View): List<android.widget.TextView> = when (v) {
        is android.widget.TextView -> listOf(v); is android.view.ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList()
    }

    @Test fun totalsSurviveSavingEverythingAndViewCapturedOpensDigitizedHistory() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        HistoryReviewStore109.clear(app)
        val rides = (1..3).map { i ->
            UberCompletedRide026("t$i", "2026-11-05T10:00:00Z", "2026-11-03T1$i:00:00Z", 20.0 + i, "comfort",
                "Rua Teste $i, Bairro - São Paulo - SP", if (i == 3) null else "Av. Teste $i, Bairro - São Paulo - SP", 0.9, 600, 4.0)
        }
        HistoryReviewStore109.save(app, rides, 9, 7)
        val controller = org.robolectric.Robolectric.buildActivity(HistoryReviewActivity109::class.java,
            android.content.Intent(app, HistoryReviewActivity109::class.java).putExtra(HistoryReviewActivity109.EXTRA_FROM_SCAN, true)).setup()
        val a = controller.get()
        texts(a.window.decorView).first { it.text == "Ver ofertas capturadas" }.performClick()
        // Seleciona explicitamente o incompleto e confirma tudo.
        (texts(a.window.decorView).first { it.text.startsWith("Incluir nesta confirmação") }.parent as View).performClick()
        texts(a.window.decorView).first { it.text.startsWith("Confirmar 3") }.performClick()
        val all = texts(a.window.decorView).filter { it.isShown }.map { it.text.toString() }
        assertTrue(all.toString(), all.contains("Registros capturados: 3"))
        assertTrue(all.toString(), all.contains("Incompletos: 1 · Duplicados: 0 · Canceladas: 0"))
        assertTrue(all.toString(), all.contains("Importados para Histórico › Digitalizados: 3"))
        assertTrue(all.toString(), all.contains("Aguardando conferência: 0"))
        assertFalse("no empty pending list after everything was saved", all.any { it.startsWith("Selecione registros") || it.startsWith("Todos (0)") })
        texts(a.window.decorView).first { it.text == "Ver ofertas capturadas" }.performClick()
        val next = org.robolectric.Shadows.shadowOf(a).nextStartedActivity
        assertEquals(MainActivity::class.java.name, next.component!!.className)
        assertEquals(MainActivity.BUBBLE_ACTION_HISTORY_DIGITIZED, next.getStringExtra(MainActivity.EXTRA_BUBBLE_ACTION))
    }

    @Test fun filterPillsUseTwoByTwoGridInNarrowWindowsWithoutWrapping() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        HistoryReviewStore109.save(app, listOf(UberCompletedRide026("p1", "2026-11-05T10:00:00Z", null, 21.0, "comfort", null, null, 0.7)), 2, 1)
        val a = org.robolectric.Robolectric.buildActivity(HistoryReviewActivity109::class.java).setup().get()
        val pills = texts(a.window.decorView).filter { it.text.matches(Regex("(Todos|Incompletos|Duplicados|Canceladas) \\(\\d+\\)")) }
        assertEquals(4, pills.size)
        assertTrue(pills.all { it.maxLines == 1 })
        // Narrow (phone/floating window): two rows of two.
        val rows = pills.map { it.parent }.distinct()
        assertEquals(2, rows.size)
        HistoryReviewStore109.clear(app)
    }
}
