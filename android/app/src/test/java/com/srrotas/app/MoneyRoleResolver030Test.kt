package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyRoleResolver030Test {
    private val turboElectric = """
        Electric
        R$ 34,15
        R$ 3,75 / km aprox.
        4,86
        Verificado
        Turbo Mais
        R$ 6,57
        5 min (1,2 km)
        Rua de Embarque
        33 min (7,9 km)
        Rua de Destino
        Aceitar
    """.trimIndent()

    @Test fun turboMaisNeverBecomesPrimaryFare() {
        assertEquals(34.15, MoneyRoleResolver030.primaryFare(turboElectric)!!, 0.001)
        val roles = MoneyRoleResolver030.inspectText(turboElectric)
        assertTrue(roles.any { it.value == 6.57 && it.role == MoneyRoleResolver030.Role.PROMOTION_BONUS })
    }

    @Test fun shadowIndependentlySelectsMainFare() {
        assertEquals(34.15, MoneyRoleResolver030.shadowPrimaryFare(turboElectric)!!, 0.001)
    }

    @Test fun promotionOnSameLineIsSecondary() {
        val text = """
            Comfort
            R$ 28,90
            R$ 3,20 / km aprox.
            Turbo Mais R$ 5,40
            4 min (0,8 km)
            22 min (8,2 km)
            Aceitar
        """.trimIndent()
        assertEquals(28.90, MoneyRoleResolver030.primaryFare(text)!!, 0.001)
        assertTrue(
            MoneyRoleResolver030.inspectText(text)
                .any { it.value == 5.40 && it.role == MoneyRoleResolver030.Role.PROMOTION_BONUS },
        )
    }

    @Test fun plusMoneyIsNeverPrimary() {
        val text = """
            UberX
            R$ 25,00
            + R$ 4,00
            3 min (0,5 km)
            20 min (7,0 km)
            Aceitar
        """.trimIndent()
        assertEquals(25.0, MoneyRoleResolver030.primaryFare(text)!!, 0.001)
        assertTrue(
            MoneyRoleResolver030.inspectText(text)
                .any { it.value == 4.0 && it.role == MoneyRoleResolver030.Role.SECONDARY_MONEY },
        )
    }

    @Test fun ordinarySingleFareStillWorks() {
        val text = """
            Comfort
            R$ 19,80
            R$ 3,30 / km aprox.
            3 min (0,6 km)
            18 min (5,4 km)
            Aceitar
        """.trimIndent()
        assertEquals(19.80, MoneyRoleResolver030.primaryFare(text)!!, 0.001)
    }

    @Test fun advertisedPerKmIsNeverPrimary() {
        val roles = MoneyRoleResolver030.inspectText(turboElectric)
        assertTrue(roles.any { it.value == 3.75 && it.role == MoneyRoleResolver030.Role.ADVERTISED_PER_KM })
    }
}
