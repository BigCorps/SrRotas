package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UberScreenGateTest {
    @Test fun rejectsOwnAppDiagnostic(){
        val text="Sr. Rotas 2.0 Alpha\nVersão instalada: 0.5.3-alpha-debug\n5. Diagnóstico da leitura\nCompartilhar diagnóstico"
        assertEquals(UberScreenGate.Kind.OWN_APP,UberScreenGate.classify(text))
    }

    @Test fun acceptsRealOfferCard(){
        val text="Black\nExclusivo\nR$ 20,04\nR$ 5,89/km aprox.\n5 min (1.2 km)\n15 min (2.2 km)\nAceitar"
        assertEquals(UberScreenGate.Kind.OFFER_CANDIDATE,UberScreenGate.classify(text))
    }

    @Test fun acceptsElectricOfferCard(){
        val text="Electric\nExclusivo\nR$ 47,93\nR$ 2,10/km aprox.\n5 min (1.0 km)\n50 minutos (21.8 km)\nAceitar"
        assertEquals(UberScreenGate.Kind.OFFER_CANDIDATE,UberScreenGate.classify(text))
    }

    @Test fun classifiesHomeAsIdle(){
        val text="Você está online\nRegistro de viagens\nPróxima viagem: +R$ 1,75"
        assertEquals(UberScreenGate.Kind.IDLE_OR_HOME,UberScreenGate.classify(text))
    }

    @Test fun rejectsOfferScreenshotInsideRecents(){
        val text="""13:25
Sr. ROTAS
Ith, Você
Resumir 1 não lida
Mensagem
2 Electric Exclusivo
R$ 47,93
R$ 2,10/km aprox.
5 min (1.0 km)
50 minutos (21.8 km)
Close all"""
        assertEquals(UberScreenGate.Kind.FOREIGN_UI,UberScreenGate.classify(text))
    }
}
