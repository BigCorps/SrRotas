package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test

class OfferParserTest {
    private val s=DriverSettings(costPerKm=0.85)
    private fun parse(text:String)=OfferParser.parse(text,"com.ubercab.driver","fixture",s)

    @Test fun parsesExclusive3098(){
        val o=parse("""UberX
Exclusivo
R$ 30,98
R$ 1,72/km aprox.
4,97 (2978)
Verificado
2 min (0.7 km)
Rua Galeno de Castro
34 minutos (17.3 km)
Rua Frederico Bartholdi
Aceitar""")!!
        assertEquals(30.98,o.fare,0.01);assertEquals(18.0,o.totalKm!!,0.01);assertEquals(36,o.totalMinutes);assertEquals(1.72,o.perKm!!,0.01);assertEquals(51.63,o.perHour!!,0.02);assertEquals(4.97,o.passengerRating!!,0.01);assertEquals("exclusive",o.offerType);assertEquals("sr-rotas-v0.5.2",o.parserVersion)
    }

    @Test fun parsesExclusive1520(){
        val o=parse("""UberX
Exclusivo
R$ 15,20
R$ 2,17/km aprox.
4,95 (337)
Verificado
6 min (2.1 km)
Rua Tenente José Maria Pinto
15 minutos (4.9 km)
Rua Comendador Joaquim Gomes de Oliveira
Aceitar""")!!
        assertEquals(7.0,o.totalKm!!,0.01);assertEquals(21,o.totalMinutes);assertEquals(2.17,o.perKm!!,0.01);assertEquals(43.43,o.perHour!!,0.02)
    }

    @Test fun parsesAccessibility618(){
        val o=parse("Selecionar\nR$ 6,18\nR$ 1,44/km aprox.\nUberX\n4,95 (2418)\nVerificado\n7 min (2.7 km)\nR. Ptolomeu\n5 minutos (1.6 km)\nEstação Socorro")!!
        assertEquals(4.3,o.totalKm!!,0.01);assertEquals(12,o.totalMinutes);assertEquals(30.90,o.perHour!!,0.02);assertEquals("radar",o.offerType)
    }

    @Test fun priorityBonusIsNotFare(){
        val o=parse("""Priority
Exclusivo
R$ 11,99
+R$ 1,83 incluído para esta viagem
R$ 1,82/km aprox.
4,91 (512)
7 min (1.7 km)
12 minutos (4.9 km)
Aceitar""")!!
        assertEquals(11.99,o.fare,0.01);assertEquals(6.6,o.totalKm!!,0.01);assertEquals("priority",o.serviceType)
    }

    @Test fun parsesRadar999(){
        val o=parse("""Radar de Viagens 3
UberX
R$ 9,99
R$ 2,04/km aprox.
4,88 (921)
6 min (1.2 km)
11 minutos (3.7 km)
Selecionar""")!!
        assertEquals(4.9,o.totalKm!!,0.01);assertEquals(17,o.totalMinutes);assertEquals(2.04,o.perKm!!,0.01);assertEquals("radar",o.offerType)
    }

    @Test fun parsesPriority917(){
        val o=parse("""Priority
Exclusivo
R$ 9,17
+R$ 2,35 incluído para esta viagem
R$ 2,55/km aprox.
4,92 (748)
3 min (0.8 km)
8 minutos (2.8 km)
Aceitar""")!!
        assertEquals(9.17,o.fare,0.01);assertEquals(3.6,o.totalKm!!,0.01);assertEquals(11,o.totalMinutes);assertEquals(2.55,o.perKm!!,0.01)
    }

    @Test fun acceptsBareDollarFromRealOcr(){
        val o=parse("""Black
Exclusivo
$ 17,99
4,69 (221)
1 min (0.7 km)
10 minutos (2.0 km)
Aceitar""")!!
        assertEquals(17.99,o.fare,0.01)
        assertEquals(11,o.totalMinutes)
    }

    @Test fun rejectsHomeScreen260(){
        val text="""Página inicial
Tendências de ganhos
Você está online
Registro de viagens
R$ 260,76
Confira as tendências de ganhos
+R$ 1,25
1-6 min
1-4 min
1-2 min"""
        assertNull(parse(text))
    }

    @Test fun rejectsMapSurge125(){
        val text="""+R$ 1,25
1-4 min
1-5 min
SANTO AMARO
Você está online
Procurando viagens"""
        assertNull(parse(text))
    }

    @Test fun rejectsRadarCrossCardTimeMixLikeReal6131(){
        val text="""Radar de Viagens 3
Comfort
R$ 61,31
R$ 4,20/km aprox.
4,95 (840)
2 min (1.0 km)
13 minutos (13.6 km)
Selecionar"""
        assertNull(parse(text))
    }

    @Test fun rejectsRadarCrossCardTimeMixLikeReal7387(){
        val text="""Radar de Viagens 3
Black
R$ 73,87
R$ 5,31/km aprox.
4,91 (602)
1 min (0.5 km)
8 minutos (13.4 km)
Selecionar"""
        assertNull(parse(text))
    }

    @Test fun rejectsIncompleteExclusive1799WhenAdvertisedPerKmDisagrees(){
        val text="""Black
Exclusivo
R$ 17,99
R$ 6,66/km aprox.
4,69 (221)
10 minutos (2.0 km)
Aceitar"""
        assertNull(parse(text))
    }

    @Test fun rejectsIncomplete2364WhenPickupWasLost(){
        val text="""Exclusivo
R$ 23,64
R$ 4,46/km aprox.
4,94 (901)
24 minutos (4.1 km)
Aceitar"""
        assertNull(parse(text))
    }

    @Test fun acceptsComplete1799AfterNextFrame(){
        val o=parse("""Black
Exclusivo
R$ 17,99
R$ 6,66/km aprox.
4,69 (221)
4 min (0.7 km)
10 minutos (2.0 km)
Aceitar""")!!
        assertEquals(2.7,o.totalKm!!,0.01)
        assertEquals(14,o.totalMinutes)
        assertEquals(6.66,o.perKm!!,0.01)
        assertEquals(77.10,o.perHour!!,0.02)
    }
}
