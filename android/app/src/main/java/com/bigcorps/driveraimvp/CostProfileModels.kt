package com.srrotas.app

import org.json.JSONObject
import java.time.Instant

object CostProfileValues {
    const val VEHICLE_COMBUSTION = "combustion"
    const val VEHICLE_ELECTRIC = "electric"
    const val VEHICLE_HYBRID = "hybrid"
    const val VEHICLE_PLUGIN_HYBRID = "plugin_hybrid"

    const val OWNERSHIP_PAID = "paid"
    const val OWNERSHIP_FINANCED = "financed"
    const val OWNERSHIP_RENTED = "rented"
    const val OWNERSHIP_SUBSCRIPTION = "subscription"

    const val ENERGY_GASOLINE = "gasoline"
    const val ENERGY_ETHANOL = "ethanol"
    const val ENERGY_GNV = "gnv"
    const val ENERGY_ELECTRICITY = "electricity"
    const val ENERGY_COMBINATION = "combination"

    const val SOURCE_USER = "userProvided"
    const val SOURCE_ESTIMATED = "estimated"

    val vehicleTypes = setOf(
        VEHICLE_COMBUSTION,
        VEHICLE_ELECTRIC,
        VEHICLE_HYBRID,
        VEHICLE_PLUGIN_HYBRID,
    )
    val ownershipTypes = setOf(
        OWNERSHIP_PAID,
        OWNERSHIP_FINANCED,
        OWNERSHIP_RENTED,
        OWNERSHIP_SUBSCRIPTION,
    )
    val energyModes = setOf(
        ENERGY_GASOLINE,
        ENERGY_ETHANOL,
        ENERGY_GNV,
        ENERGY_ELECTRICITY,
        ENERGY_COMBINATION,
    )
    val liquidFuelTypes = setOf(
        ENERGY_GASOLINE,
        ENERGY_ETHANOL,
        ENERGY_GNV,
    )
}

data class CostProfile(
    val vehicleType: String = CostProfileValues.VEHICLE_COMBUSTION,
    val ownershipType: String = CostProfileValues.OWNERSHIP_PAID,
    val energyMode: String = CostProfileValues.ENERGY_GASOLINE,
    val combinationLiquidFuel: String = CostProfileValues.ENERGY_GASOLINE,

    val fuelPricePerUnit: Double? = null,
    val fuelKmPerUnit: Double? = null,
    val electricityPricePerKwh: Double? = null,
    val electricKwhPer100Km: Double? = null,

    val ownershipMonthly: Double = 0.0,
    val insuranceMonthly: Double = 0.0,
    val maintenanceMonthly: Double = 0.0,
    val tiresMonthly: Double = 0.0,
    val otherMonthly: Double = 0.0,

    val monthlyWorkKm: Double? = null,
    val monthlyWorkKmSource: String = CostProfileValues.SOURCE_ESTIMATED,
    val estimatedMonthlyWorkKm: Double = CostCalculator.DEFAULT_ESTIMATED_MONTHLY_KM,
    val averageJourneyHours: Double? = null,
    val monthlyWorkHours: Double? = null,

    val updatedAt: String = Instant.now().toString(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("vehicle_type", vehicleType)
        put("ownership_type", ownershipType)
        put("energy_mode", energyMode)
        put("combination_liquid_fuel", combinationLiquidFuel)
        putNullable("fuel_price_per_unit", fuelPricePerUnit)
        putNullable("fuel_km_per_unit", fuelKmPerUnit)
        putNullable("electricity_price_per_kwh", electricityPricePerKwh)
        putNullable("electric_kwh_per_100_km", electricKwhPer100Km)
        put("ownership_monthly", ownershipMonthly)
        put("insurance_monthly", insuranceMonthly)
        put("maintenance_monthly", maintenanceMonthly)
        put("tires_monthly", tiresMonthly)
        put("other_monthly", otherMonthly)
        putNullable("monthly_work_km", monthlyWorkKm)
        put("monthly_work_km_source", monthlyWorkKmSource)
        put("estimated_monthly_work_km", estimatedMonthlyWorkKm)
        putNullable("average_journey_hours", averageJourneyHours)
        putNullable("monthly_work_hours", monthlyWorkHours)
        put("client_updated_at", updatedAt)
        put("profile_version", CostCalculator.VERSION)
    }

    companion object {
        fun fromJson(o: JSONObject): CostProfile = CostProfile(
            vehicleType = o.optString(
                "vehicle_type",
                CostProfileValues.VEHICLE_COMBUSTION,
            ),
            ownershipType = o.optString(
                "ownership_type",
                CostProfileValues.OWNERSHIP_PAID,
            ),
            energyMode = o.optString(
                "energy_mode",
                CostProfileValues.ENERGY_GASOLINE,
            ),
            combinationLiquidFuel = o.optString(
                "combination_liquid_fuel",
                CostProfileValues.ENERGY_GASOLINE,
            ),
            fuelPricePerUnit = o.numberOrNull("fuel_price_per_unit"),
            fuelKmPerUnit = o.numberOrNull("fuel_km_per_unit"),
            electricityPricePerKwh =
                o.numberOrNull("electricity_price_per_kwh"),
            electricKwhPer100Km =
                o.numberOrNull("electric_kwh_per_100_km"),
            ownershipMonthly =
                o.numberOrNull("ownership_monthly") ?: 0.0,
            insuranceMonthly =
                o.numberOrNull("insurance_monthly") ?: 0.0,
            maintenanceMonthly =
                o.numberOrNull("maintenance_monthly") ?: 0.0,
            tiresMonthly =
                o.numberOrNull("tires_monthly") ?: 0.0,
            otherMonthly =
                o.numberOrNull("other_monthly") ?: 0.0,
            monthlyWorkKm = o.numberOrNull("monthly_work_km"),
            monthlyWorkKmSource = o.optString(
                "monthly_work_km_source",
                CostProfileValues.SOURCE_ESTIMATED,
            ),
            estimatedMonthlyWorkKm =
                o.numberOrNull("estimated_monthly_work_km")
                    ?: CostCalculator.DEFAULT_ESTIMATED_MONTHLY_KM,
            averageJourneyHours =
                o.numberOrNull("average_journey_hours"),
            monthlyWorkHours =
                o.numberOrNull("monthly_work_hours"),
            updatedAt = o.optString(
                "client_updated_at",
                o.optString(
                    "updated_at",
                    Instant.now().toString(),
                ),
            ),
        )

        private fun JSONObject.numberOrNull(
            key: String,
        ): Double? =
            if (!has(key) || isNull(key)) {
                null
            } else {
                optDouble(key).takeIf {
                    it.isFinite()
                }
            }
    }
}

data class CostCalculation(
    val version: String,
    val liquidCostPerKm: Double,
    val electricCostPerKm: Double,
    val variableCostPerKm: Double,
    val fixedMonthlyTotal: Double,
    val allocationKmPerMonth: Double,
    val allocationSource: String,
    val fixedCostPerKm: Double,
    val effectiveCostPerKm: Double,
    val completeness: String,
    val costSource: String,
    val missingInputs: List<String>,
    val memoryLines: List<String>,
) {
    val hasUsableCost: Boolean
        get() = effectiveCostPerKm > 0.0

    fun memoryText(): String =
        buildString {
            append("Memória do cálculo · $version\n\n")
            memoryLines.forEach {
                append("• ")
                append(it)
                append('\n')
            }
            if (missingInputs.isNotEmpty()) {
                append("\nNão incluído por falta de informação:\n")
                missingInputs.forEach {
                    append("• ")
                    append(it)
                    append('\n')
                }
            }
            append(
                "\n* Lucro est. é uma estimativa operacional. " +
                    "Não representa lucro contábil e muda quando os dados de custo mudam.",
            )
        }
}
