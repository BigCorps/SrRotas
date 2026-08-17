package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UberScreenGateTest {
    @Test fun rejectsOwnAppDiagnostic(){
        val text="Sr. Rotas 2.0 Alpha\nVersão instalada: 0.5.0-alpha-debug\n5. Diagnóstico da leitura\nCompartilhar diagnóstico"
        assertEquals(UberScreenGate.Kind.OWN_APP,UberScreenGate.classify(text))
    }

    @Test fun acceptsRealOfferCard(){
        val text="Black\nExclusivo\nR$ 20,04\nR$ 5,89/km aprox.\n5 min (1.2 km)\n15 min (2.2 km)\nAceitar"
        assertEquals(UberScreenGate.Kind.OFFER_CANDIDATE,UberScreenGate.classify(text))
    }

    @Test fun classifiesHomeAsIdle(){
        val text="Você está online\nRegistro de viagens\nPróxima viagem: +R$ 1,75"
        assertEquals(UberScreenGate.Kind.IDLE_OR_HOME,UberScreenGate.classify(text))
    }
}
