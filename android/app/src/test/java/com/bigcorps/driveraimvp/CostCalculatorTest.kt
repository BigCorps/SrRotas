package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostCalculatorTest {
    @Test
    fun combustionAndFixedCostsUseUserMonthlyKm() {
        val profile =
            CostProfile(
                vehicleType =
                    CostProfileValues.VEHICLE_COMBUSTION,
                energyMode =
                    CostProfileValues.ENERGY_GASOLINE,
                fuelPricePerUnit = 6.0,
                fuelKmPerUnit = 12.0,
                ownershipMonthly = 1000.0,
                insuranceMonthly = 200.0,
                maintenanceMonthly = 200.0,
                tiresMonthly = 100.0,
                monthlyWorkKm = 3000.0,
                monthlyWorkKmSource =
                    CostProfileValues.SOURCE_USER,
            )

        val result =
            CostCalculator.calculate(profile)

        assertEquals(
            0.5,
            result.liquidCostPerKm,
            0.0001,
        )
        assertEquals(
            1500.0,
            result.fixedMonthlyTotal,
            0.001,
        )
        assertEquals(
            0.5,
            result.fixedCostPerKm,
            0.0001,
        )
        assertEquals(
            1.0,
            result.effectiveCostPerKm,
            0.0001,
        )
        assertEquals(
            CostProfileValues.SOURCE_USER,
            result.allocationSource,
        )
        assertEquals(
            "complete",
            result.completeness,
        )
    }

    @Test
    fun unknownMonthlyKmUsesEstimatedReference() {
        val result =
            CostCalculator.calculate(
                CostProfile(
                    energyMode =
                        CostProfileValues.ENERGY_GASOLINE,
                    fuelPricePerUnit = 6.0,
                    fuelKmPerUnit = 12.0,
                    ownershipMonthly = 900.0,
                    monthlyWorkKm = null,
                    monthlyWorkKmSource =
                        CostProfileValues.SOURCE_ESTIMATED,
                    estimatedMonthlyWorkKm = 1800.0,
                ),
            )

        assertEquals(
            CostProfileValues.SOURCE_ESTIMATED,
            result.allocationSource,
        )
        assertEquals(
            1800.0,
            result.allocationKmPerMonth,
            0.001,
        )
        assertEquals(
            1.0,
            result.effectiveCostPerKm,
            0.0001,
        )
        assertTrue(
            result.costSource.contains(
                "estimated_allocation",
            ),
        )
    }

    @Test
    fun electricFormulaUsesKwhPer100Km() {
        val result =
            CostCalculator.calculate(
                CostProfile(
                    vehicleType =
                        CostProfileValues.VEHICLE_ELECTRIC,
                    energyMode =
                        CostProfileValues.ENERGY_ELECTRICITY,
                    electricityPricePerKwh = 1.0,
                    electricKwhPer100Km = 20.0,
                    monthlyWorkKmSource =
                        CostProfileValues.SOURCE_ESTIMATED,
                ),
            )

        assertEquals(
            0.2,
            result.electricCostPerKm,
            0.0001,
        )
        assertEquals(
            0.2,
            result.effectiveCostPerKm,
            0.0001,
        )
    }

    @Test
    fun missingEnergyInputMarksProfilePartial() {
        val result =
            CostCalculator.calculate(
                CostProfile(
                    energyMode =
                        CostProfileValues.ENERGY_GASOLINE,
                    fuelPricePerUnit = 6.0,
                    fuelKmPerUnit = null,
                    ownershipMonthly = 300.0,
                    estimatedMonthlyWorkKm = 3000.0,
                ),
            )

        assertEquals(
            "partial",
            result.completeness,
        )
        assertTrue(
            "fuel consumption must be listed as missing",
            result.missingInputs.any {
                it.contains(
                    "consumo",
                    ignoreCase = true,
                )
            },
        )
        assertEquals(
            0.1,
            result.effectiveCostPerKm,
            0.0001,
        )
    }

    @Test
    fun offerMemoryUsesEffectiveCostPerKm() {
        val calculation =
            CostCalculator.calculate(
                CostProfile(
                    energyMode =
                        CostProfileValues.ENERGY_GASOLINE,
                    fuelPricePerUnit = 6.0,
                    fuelKmPerUnit = 12.0,
                    ownershipMonthly = 1500.0,
                    monthlyWorkKm = 3000.0,
                    monthlyWorkKmSource =
                        CostProfileValues.SOURCE_USER,
                ),
            )

        val estimate =
            CostCalculator.estimateForOffer(
                fare = 30.0,
                totalKm = 10.0,
                calculation = calculation,
            )

        assertEquals(
            10.0,
            estimate.first,
            0.001,
        )
        assertEquals(
            20.0,
            estimate.second,
            0.001,
        )
    }
}
