package com.srrotas.app

import java.util.Locale
import kotlin.math.round

object CostCalculator {
    const val VERSION = "sr-cost-v0.18.0"
    const val DEFAULT_ESTIMATED_MONTHLY_KM = 3000.0

    fun calculate(profile: CostProfile): CostCalculation {
        val usesLiquid = profile.energyMode in setOf(
            CostProfileValues.ENERGY_GASOLINE,
            CostProfileValues.ENERGY_ETHANOL,
            CostProfileValues.ENERGY_GNV,
            CostProfileValues.ENERGY_COMBINATION,
        )
        val usesElectric = profile.energyMode in setOf(
            CostProfileValues.ENERGY_ELECTRICITY,
            CostProfileValues.ENERGY_COMBINATION,
        )

        val liquidPrice =
            profile.fuelPricePerUnit
                ?.takeIf { it > 0.0 }
        val liquidConsumption =
            profile.fuelKmPerUnit
                ?.takeIf { it > 0.0 }
        val electricPrice =
            profile.electricityPricePerKwh
                ?.takeIf { it > 0.0 }
        val electricConsumption =
            profile.electricKwhPer100Km
                ?.takeIf { it > 0.0 }

        val liquidCostPerKm =
            if (
                usesLiquid &&
                liquidPrice != null &&
                liquidConsumption != null
            ) {
                liquidPrice / liquidConsumption
            } else {
                0.0
            }

        val electricCostPerKm =
            if (
                usesElectric &&
                electricPrice != null &&
                electricConsumption != null
            ) {
                electricPrice *
                    electricConsumption /
                    100.0
            } else {
                0.0
            }

        val variableCostPerKm =
            liquidCostPerKm +
                electricCostPerKm

        val fixedMonthlyTotal =
            nonNegative(profile.ownershipMonthly) +
                nonNegative(profile.insuranceMonthly) +
                nonNegative(profile.maintenanceMonthly) +
                nonNegative(profile.tiresMonthly) +
                nonNegative(profile.otherMonthly)

        val userKm =
            profile.monthlyWorkKm
                ?.takeIf {
                    it > 0.0 &&
                        profile.monthlyWorkKmSource ==
                        CostProfileValues.SOURCE_USER
                }

        val estimatedKm =
            profile.estimatedMonthlyWorkKm
                .takeIf { it > 0.0 }
                ?: DEFAULT_ESTIMATED_MONTHLY_KM

        val allocationKm =
            userKm ?: estimatedKm

        val allocationSource =
            if (userKm != null) {
                CostProfileValues.SOURCE_USER
            } else {
                CostProfileValues.SOURCE_ESTIMATED
            }

        val fixedCostPerKm =
            if (allocationKm > 0.0) {
                fixedMonthlyTotal /
                    allocationKm
            } else {
                0.0
            }

        val missing = mutableListOf<String>()
        if (
            usesLiquid &&
            liquidPrice == null
        ) {
            missing +=
                "preço do combustível líquido"
        }
        if (
            usesLiquid &&
            liquidConsumption == null
        ) {
            missing +=
                "consumo do combustível em km/L ou km/m³"
        }
        if (
            usesElectric &&
            electricPrice == null
        ) {
            missing +=
                "preço da eletricidade em R$/kWh"
        }
        if (
            usesElectric &&
            electricConsumption == null
        ) {
            missing +=
                "consumo elétrico em kWh/100 km"
        }

        val completeness =
            if (missing.isEmpty()) {
                "complete"
            } else {
                "partial"
            }

        val source =
            buildString {
                append("profile_")
                append(
                    if (
                        allocationSource ==
                        CostProfileValues.SOURCE_USER
                    ) {
                        "user_allocation"
                    } else {
                        "estimated_allocation"
                    },
                )
                if (
                    completeness ==
                    "partial"
                ) {
                    append("_partial")
                }
            }

        val effective =
            variableCostPerKm +
                fixedCostPerKm

        val memory = mutableListOf<String>()

        if (usesLiquid) {
            val liquidFuel =
                if (
                    profile.energyMode ==
                    CostProfileValues.ENERGY_COMBINATION
                ) {
                    profile.combinationLiquidFuel
                } else {
                    profile.energyMode
                }

            if (
                liquidPrice != null &&
                liquidConsumption != null
            ) {
                memory +=
                    "${fuelLabel(liquidFuel)} [informado]: " +
                    "R$ ${money(liquidPrice)} ÷ " +
                    "${number(liquidConsumption)} km/unidade = " +
                    "R$ ${cost(liquidCostPerKm)}/km"
            }
        }

        if (
            usesElectric &&
            electricPrice != null &&
            electricConsumption != null
        ) {
            memory +=
                "Eletricidade [informado]: " +
                "R$ ${money(electricPrice)}/kWh × " +
                "${number(electricConsumption)} kWh/100 km ÷ 100 = " +
                "R$ ${cost(electricCostPerKm)}/km"
        }

        memory +=
            "Custo variável conhecido = " +
            "R$ ${cost(variableCostPerKm)}/km"

        memory +=
            "Custos mensais informados = " +
            "R$ ${money(fixedMonthlyTotal)}/mês"

        memory +=
            "Base de rateio [${sourceLabel(allocationSource)}] = " +
            "${number(allocationKm)} km de trabalho/mês"

        memory +=
            "Rateio dos custos mensais: " +
            "R$ ${money(fixedMonthlyTotal)} ÷ " +
            "${number(allocationKm)} km = " +
            "R$ ${cost(fixedCostPerKm)}/km"

        memory +=
            "Custo operacional estimado = " +
            "R$ ${cost(variableCostPerKm)} + " +
            "R$ ${cost(fixedCostPerKm)} = " +
            "R$ ${cost(effective)}/km"

        return CostCalculation(
            version = VERSION,
            liquidCostPerKm =
                r4(liquidCostPerKm),
            electricCostPerKm =
                r4(electricCostPerKm),
            variableCostPerKm =
                r4(variableCostPerKm),
            fixedMonthlyTotal =
                r2(fixedMonthlyTotal),
            allocationKmPerMonth =
                r2(allocationKm),
            allocationSource =
                allocationSource,
            fixedCostPerKm =
                r4(fixedCostPerKm),
            effectiveCostPerKm =
                r4(effective),
            completeness =
                completeness,
            costSource = source,
            missingInputs = missing,
            memoryLines = memory,
        )
    }

    fun estimateForOffer(
        fare: Double,
        totalKm: Double,
        calculation: CostCalculation,
    ): Pair<Double, Double> {
        val cost =
            totalKm.coerceAtLeast(0.0) *
                calculation.effectiveCostPerKm
        return r2(cost) to
            r2(fare - cost)
    }

    private fun nonNegative(value: Double) =
        value.coerceAtLeast(0.0)

    private fun sourceLabel(
        source: String,
    ) =
        if (
            source ==
            CostProfileValues.SOURCE_USER
        ) {
            "informado"
        } else {
            "estimado"
        }

    private fun fuelLabel(
        value: String,
    ) =
        when (value) {
            CostProfileValues.ENERGY_ETHANOL ->
                "Etanol"
            CostProfileValues.ENERGY_GNV ->
                "GNV"
            else ->
                "Gasolina"
        }

    private fun money(value: Double) =
        String.format(
            Locale("pt", "BR"),
            "%.2f",
            value,
        )

    private fun number(value: Double) =
        String.format(
            Locale("pt", "BR"),
            "%.1f",
            value,
        )

    private fun cost(value: Double) =
        String.format(
            Locale("pt", "BR"),
            "%.4f",
            value,
        )

    private fun r2(value: Double) =
        round(value * 100.0) / 100.0

    private fun r4(value: Double) =
        round(value * 10000.0) / 10000.0
}
