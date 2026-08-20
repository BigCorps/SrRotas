package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.Instant

class CostProfileActivity : Activity() {
    private lateinit var repo: SettingsRepository
    private lateinit var store: CostProfileStore

    private lateinit var vehicleSpinner: Spinner
    private lateinit var ownershipSpinner: Spinner
    private lateinit var energySpinner: Spinner
    private lateinit var liquidFuelSpinner: Spinner

    private lateinit var liquidFuelBox: LinearLayout
    private lateinit var electricBox: LinearLayout
    private lateinit var combinationFuelBox: LinearLayout

    private lateinit var fuelPrice: EditText
    private lateinit var fuelConsumption: EditText
    private lateinit var electricityPrice: EditText
    private lateinit var electricityConsumption: EditText

    private lateinit var ownershipCostLabel: TextView
    private lateinit var ownershipMonthly: EditText

    private lateinit var monthlyKmUnknown: CheckBox
    private lateinit var monthlyWorkKm: EditText
    private lateinit var estimatedMonthlyKm: EditText

    private lateinit var insuranceMonthly: EditText
    private lateinit var maintenanceMonthly: EditText
    private lateinit var tiresMonthly: EditText
    private lateinit var otherMonthly: EditText
    private lateinit var averageJourneyHours: EditText
    private lateinit var monthlyWorkHours: EditText

    private lateinit var resultText: TextView
    private lateinit var syncText: TextView

    private var currentCalculation: CostCalculation? = null

    private val vehicleLabels =
        listOf(
            "Combustão",
            "Elétrico",
            "Híbrido",
            "Híbrido plug-in",
        )
    private val vehicleValues =
        listOf(
            CostProfileValues.VEHICLE_COMBUSTION,
            CostProfileValues.VEHICLE_ELECTRIC,
            CostProfileValues.VEHICLE_HYBRID,
            CostProfileValues.VEHICLE_PLUGIN_HYBRID,
        )

    private val ownershipLabels =
        listOf(
            "Quitado",
            "Financiado",
            "Alugado",
            "Assinatura",
        )
    private val ownershipValues =
        listOf(
            CostProfileValues.OWNERSHIP_PAID,
            CostProfileValues.OWNERSHIP_FINANCED,
            CostProfileValues.OWNERSHIP_RENTED,
            CostProfileValues.OWNERSHIP_SUBSCRIPTION,
        )

    private val energyLabels =
        listOf(
            "Gasolina",
            "Etanol",
            "GNV",
            "Eletricidade",
            "Combinação",
        )
    private val energyValues =
        listOf(
            CostProfileValues.ENERGY_GASOLINE,
            CostProfileValues.ENERGY_ETHANOL,
            CostProfileValues.ENERGY_GNV,
            CostProfileValues.ENERGY_ELECTRICITY,
            CostProfileValues.ENERGY_COMBINATION,
        )

    private val liquidLabels =
        listOf(
            "Gasolina",
            "Etanol",
            "GNV",
        )
    private val liquidValues =
        listOf(
            CostProfileValues.ENERGY_GASOLINE,
            CostProfileValues.ENERGY_ETHANOL,
            CostProfileValues.ENERGY_GNV,
        )

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        repo = SettingsRepository(this)
        store = CostProfileStore.get(this)

        UiKit.applySystemBars(this)

        val root = buildUi()
        setContentView(root)
        UiKit.applySafeArea(root)

        val local = store.load()
        if (local != null) {
            loadProfile(local)
        } else {
            loadDefaults()
        }

        refreshDynamicFields()
        renderCurrentCalculation()

        CostProfileSync.refreshOrFlush(this) { result ->
            result.onSuccess { synced ->
                if (
                    local == null &&
                    synced.profile != null
                ) {
                    loadProfile(
                        synced.profile,
                    )
                    refreshDynamicFields()
                    renderCurrentCalculation()
                }
                syncText.text =
                    when {
                        !synced.configured ->
                            "Nuvem: perfil ainda não configurado."
                        synced.source == "push" ->
                            "Nuvem: perfil sincronizado."
                        else ->
                            "Nuvem: perfil conferido."
                    }
            }.onFailure {
                syncText.text =
                    "Sincronização adiada: ${it.message}"
            }
        }
    }

    private fun buildUi(): View {
        val scroll =
            ScrollView(this).apply {
                setFillViewport(true)
                setBackgroundColor(
                    UiKit.palette(
                        this@CostProfileActivity,
                    ).background,
                )
            }

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(30),
                )
            }

        scroll.addView(root)

        root.addView(
            UiKit.title(
                this,
                "Meus custos",
                28f,
            ),
        )
        root.addView(
            UiKit.body(
                this,
                "Configure apenas o que souber. O Sr. Rotas separa valores informados de estimativas e mostra a memória completa usada no Lucro est.*.",
                14f,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(
                        UiKit.sectionTitle(
                            this@CostProfileActivity,
                            "Configuração rápida",
                        ),
                    )

                    addView(
                        label("Veículo"),
                    )
                    vehicleSpinner =
                        spinner(vehicleLabels)
                    addView(vehicleSpinner)

                    addView(
                        label("Situação"),
                    )
                    ownershipSpinner =
                        spinner(ownershipLabels)
                    addView(ownershipSpinner)

                    addView(
                        label("Energia / combustível"),
                    )
                    energySpinner =
                        spinner(energyLabels)
                    addView(energySpinner)

                    combinationFuelBox =
                        LinearLayout(
                            this@CostProfileActivity,
                        ).apply {
                            orientation =
                                LinearLayout.VERTICAL
                            addView(
                                label(
                                    "Combustível líquido da combinação",
                                ),
                            )
                            liquidFuelSpinner =
                                spinner(
                                    liquidLabels,
                                )
                            addView(
                                liquidFuelSpinner,
                            )
                        }
                    addView(
                        combinationFuelBox,
                    )
                },
                top = 14,
            ),
        )

        liquidFuelBox =
            UiKit.card(this).apply {
                addView(
                    UiKit.sectionTitle(
                        this@CostProfileActivity,
                        "Combustível líquido",
                    ),
                )
                addView(
                    UiKit.body(
                        this@CostProfileActivity,
                        "Para gasolina/etanol use R$/L e km/L. Para GNV use R$/m³ e km/m³.",
                        11f,
                    ),
                )
                fuelPrice =
                    UiKit.input(
                        this@CostProfileActivity,
                        "Preço — R$/L ou R$/m³",
                        numeric = true,
                    )
                fuelConsumption =
                    UiKit.input(
                        this@CostProfileActivity,
                        "Consumo efetivo — km/L ou km/m³",
                        numeric = true,
                    )
                addView(
                    UiKit.margin(
                        fuelPrice,
                        top = 8,
                    ),
                )
                addView(
                    UiKit.margin(
                        fuelConsumption,
                        top = 8,
                    ),
                )
            }

        root.addView(
            UiKit.margin(
                liquidFuelBox,
                top = 10,
            ),
        )

        electricBox =
            UiKit.card(this).apply {
                addView(
                    UiKit.sectionTitle(
                        this@CostProfileActivity,
                        "Eletricidade",
                    ),
                )
                electricityPrice =
                    UiKit.input(
                        this@CostProfileActivity,
                        "Preço da energia — R$/kWh",
                        numeric = true,
                    )
                electricityConsumption =
                    UiKit.input(
                        this@CostProfileActivity,
                        "Consumo efetivo — kWh/100 km",
                        numeric = true,
                    )
                addView(
                    electricityPrice,
                )
                addView(
                    UiKit.margin(
                        electricityConsumption,
                        top = 8,
                    ),
                )
            }

        root.addView(
            UiKit.margin(
                electricBox,
                top = 10,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(
                        UiKit.sectionTitle(
                            this@CostProfileActivity,
                            "Custo fixo principal",
                        ),
                    )
                    ownershipCostLabel =
                        UiKit.body(
                            this@CostProfileActivity,
                            "",
                            12f,
                        )
                    addView(
                        ownershipCostLabel,
                    )
                    ownershipMonthly =
                        UiKit.input(
                            this@CostProfileActivity,
                            "R$/mês — 0 se não houver",
                            numeric = true,
                        )
                    addView(
                        UiKit.margin(
                            ownershipMonthly,
                            top = 7,
                        ),
                    )
                },
                top = 10,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(
                        UiKit.sectionTitle(
                            this@CostProfileActivity,
                            "Base de rateio",
                        ),
                    )
                    addView(
                        UiKit.body(
                            this@CostProfileActivity,
                            "Os custos mensais precisam ser distribuídos pelos quilômetros de trabalho do mês.",
                            12f,
                        ),
                    )

                    monthlyKmUnknown =
                        CheckBox(
                            this@CostProfileActivity,
                        ).apply {
                            text =
                                "Não sei meus km de trabalho por mês"
                            setTextColor(
                                UiKit.palette(
                                    this@CostProfileActivity,
                                ).ink,
                            )
                            setOnCheckedChangeListener {
                                    _,
                                    _,
                                ->
                                refreshKmFields()
                            }
                        }
                    addView(
                        monthlyKmUnknown,
                    )

                    monthlyWorkKm =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Km de trabalho por mês",
                            numeric = true,
                        )
                    addView(
                        UiKit.margin(
                            monthlyWorkKm,
                            top = 5,
                        ),
                    )

                    estimatedMonthlyKm =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Referência estimada de km/mês",
                            numeric = true,
                        )
                    addView(
                        UiKit.margin(
                            estimatedMonthlyKm,
                            top = 7,
                        ),
                    )

                    addView(
                        UiKit.margin(
                            UiKit.body(
                                this@CostProfileActivity,
                                "Se “Não sei” estiver marcado, essa referência é usada como estimativa e fica identificada como `estimated`. Você pode alterar a referência.",
                                11f,
                            ),
                            top = 6,
                        ),
                    )
                },
                top = 10,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(
                        UiKit.sectionTitle(
                            this@CostProfileActivity,
                            "Ajustar meus custos · opcional",
                        ),
                    )
                    addView(
                        UiKit.body(
                            this@CostProfileActivity,
                            "Informe médias mensais. Parcela, aluguel ou assinatura ficam no custo fixo principal acima, conforme sua situação.",
                            11f,
                        ),
                    )

                    insuranceMonthly =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Seguro — R$/mês",
                            numeric = true,
                        )
                    maintenanceMonthly =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Manutenção média — R$/mês",
                            numeric = true,
                        )
                    tiresMonthly =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Pneus — média R$/mês",
                            numeric = true,
                        )
                    otherMonthly =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Outros custos — R$/mês",
                            numeric = true,
                        )
                    averageJourneyHours =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Jornada média — horas",
                            numeric = true,
                        )
                    monthlyWorkHours =
                        UiKit.input(
                            this@CostProfileActivity,
                            "Horas de trabalho por mês",
                            numeric = true,
                        )

                    listOf(
                        insuranceMonthly,
                        maintenanceMonthly,
                        tiresMonthly,
                        otherMonthly,
                        averageJourneyHours,
                        monthlyWorkHours,
                    ).forEachIndexed { i, view ->
                        addView(
                            if (i == 0) {
                                UiKit.margin(
                                    view,
                                    top = 8,
                                )
                            } else {
                                UiKit.margin(
                                    view,
                                    top = 7,
                                )
                            },
                        )
                    }
                },
                top = 10,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.card(this).apply {
                    addView(
                        UiKit.sectionTitle(
                            this@CostProfileActivity,
                            "Estimativa de lucro",
                        ),
                    )
                    resultText =
                        UiKit.body(
                            this@CostProfileActivity,
                            "",
                            13f,
                        )
                    addView(resultText)

                    addView(
                        UiKit.margin(
                            UiKit.secondaryButton(
                                this@CostProfileActivity,
                                "Ver memória do cálculo",
                            ) {
                                showMemory()
                            },
                            top = 9,
                        ),
                    )

                    syncText =
                        UiKit.body(
                            this@CostProfileActivity,
                            "",
                            11f,
                        )
                    addView(
                        UiKit.margin(
                            syncText,
                            top = 8,
                        ),
                    )

                    addView(
                        UiKit.margin(
                            UiKit.body(
                                this@CostProfileActivity,
                                "Itens não informados ficam fora da estimativa e aparecem como ausentes na memória. " +
                                    "Lucro est.* não é lucro contábil.",
                                11f,
                            ),
                            top = 8,
                        ),
                    )
                },
                top = 10,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.primaryButton(
                    this,
                    "Salvar meus custos",
                ) {
                    saveProfile()
                },
                top = 14,
            ),
        )

        root.addView(
            UiKit.margin(
                UiKit.secondaryButton(
                    this,
                    "Voltar",
                ) {
                    finish()
                },
                top = 8,
            ),
        )

        attachSpinnerListeners()

        return scroll
    }

    private fun attachSpinnerListeners() {
        val listener =
            object :
                AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    refreshDynamicFields()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?,
                ) = Unit
            }

        vehicleSpinner.onItemSelectedListener =
            listener
        ownershipSpinner.onItemSelectedListener =
            listener
        energySpinner.onItemSelectedListener =
            listener
    }

    private fun refreshDynamicFields() {
        if (
            !this::energySpinner.isInitialized
        ) {
            return
        }

        val vehicle =
            vehicleValues[
                vehicleSpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        vehicleValues.lastIndex,
                    )
            ]

        if (
            vehicle ==
            CostProfileValues.VEHICLE_ELECTRIC &&
            energyValues[
                energySpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        energyValues.lastIndex,
                    )
            ] !=
            CostProfileValues.ENERGY_ELECTRICITY
        ) {
            energySpinner.setSelection(
                energyValues.indexOf(
                    CostProfileValues.ENERGY_ELECTRICITY,
                ),
            )
        }

        energySpinner.isEnabled =
            vehicle !=
                CostProfileValues.VEHICLE_ELECTRIC

        val energy =
            energyValues[
                energySpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        energyValues.lastIndex,
                    )
            ]

        val usesLiquid =
            energy in setOf(
                CostProfileValues.ENERGY_GASOLINE,
                CostProfileValues.ENERGY_ETHANOL,
                CostProfileValues.ENERGY_GNV,
                CostProfileValues.ENERGY_COMBINATION,
            )

        val usesElectric =
            energy in setOf(
                CostProfileValues.ENERGY_ELECTRICITY,
                CostProfileValues.ENERGY_COMBINATION,
            )

        liquidFuelBox.visibility =
            if (usesLiquid) {
                View.VISIBLE
            } else {
                View.GONE
            }

        electricBox.visibility =
            if (usesElectric) {
                View.VISIBLE
            } else {
                View.GONE
            }

        combinationFuelBox.visibility =
            if (
                energy ==
                CostProfileValues.ENERGY_COMBINATION
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val ownership =
            ownershipValues[
                ownershipSpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        ownershipValues.lastIndex,
                    )
            ]

        ownershipCostLabel.text =
            when (ownership) {
                CostProfileValues.OWNERSHIP_FINANCED ->
                    "Parcela do financiamento por mês."
                CostProfileValues.OWNERSHIP_RENTED ->
                    "Aluguel do veículo por mês."
                CostProfileValues.OWNERSHIP_SUBSCRIPTION ->
                    "Assinatura do veículo por mês."
                else ->
                    "Veículo quitado: use este campo somente se houver outro custo fixo principal que queira ratear."
            }

        refreshKmFields()
    }

    private fun refreshKmFields() {
        if (
            !this::monthlyWorkKm.isInitialized
        ) {
            return
        }

        val unknown =
            monthlyKmUnknown.isChecked

        monthlyWorkKm.isEnabled =
            !unknown
        monthlyWorkKm.alpha =
            if (unknown) {
                .45f
            } else {
                1f
            }

        estimatedMonthlyKm.isEnabled =
            unknown
        estimatedMonthlyKm.alpha =
            if (unknown) {
                1f
            } else {
                .55f
            }
    }

    private fun loadDefaults() {
        vehicleSpinner.setSelection(0)
        ownershipSpinner.setSelection(0)
        energySpinner.setSelection(0)
        liquidFuelSpinner.setSelection(0)

        ownershipMonthly.setText("0")
        insuranceMonthly.setText("0")
        maintenanceMonthly.setText("0")
        tiresMonthly.setText("0")
        otherMonthly.setText("0")

        monthlyKmUnknown.isChecked = true
        estimatedMonthlyKm.setText(
            format(
                CostCalculator.DEFAULT_ESTIMATED_MONTHLY_KM,
                0,
            ),
        )

        syncText.text =
            "Perfil 0.18 ainda não configurado. " +
                "O custo legado atual é R$ ${format(repo.costSnapshot().costPerKm, 2)}/km."
    }

    private fun loadProfile(
        profile: CostProfile,
    ) {
        vehicleSpinner.setSelection(
            indexOfOrZero(
                vehicleValues,
                profile.vehicleType,
            ),
        )
        ownershipSpinner.setSelection(
            indexOfOrZero(
                ownershipValues,
                profile.ownershipType,
            ),
        )
        energySpinner.setSelection(
            indexOfOrZero(
                energyValues,
                profile.energyMode,
            ),
        )
        liquidFuelSpinner.setSelection(
            indexOfOrZero(
                liquidValues,
                profile.combinationLiquidFuel,
            ),
        )

        fuelPrice.setText(
            textNumber(
                profile.fuelPricePerUnit,
            ),
        )
        fuelConsumption.setText(
            textNumber(
                profile.fuelKmPerUnit,
            ),
        )
        electricityPrice.setText(
            textNumber(
                profile.electricityPricePerKwh,
            ),
        )
        electricityConsumption.setText(
            textNumber(
                profile.electricKwhPer100Km,
            ),
        )

        ownershipMonthly.setText(
            format(
                profile.ownershipMonthly,
                2,
            ),
        )
        insuranceMonthly.setText(
            format(
                profile.insuranceMonthly,
                2,
            ),
        )
        maintenanceMonthly.setText(
            format(
                profile.maintenanceMonthly,
                2,
            ),
        )
        tiresMonthly.setText(
            format(
                profile.tiresMonthly,
                2,
            ),
        )
        otherMonthly.setText(
            format(
                profile.otherMonthly,
                2,
            ),
        )

        monthlyKmUnknown.isChecked =
            profile.monthlyWorkKmSource !=
                CostProfileValues.SOURCE_USER

        monthlyWorkKm.setText(
            textNumber(
                profile.monthlyWorkKm,
            ),
        )

        estimatedMonthlyKm.setText(
            format(
                profile.estimatedMonthlyWorkKm,
                0,
            ),
        )

        averageJourneyHours.setText(
            textNumber(
                profile.averageJourneyHours,
            ),
        )
        monthlyWorkHours.setText(
            textNumber(
                profile.monthlyWorkHours,
            ),
        )

        syncText.text =
            "Perfil salvo em ${profile.updatedAt.take(19).replace('T', ' ')}."
    }

    private fun collectProfile(): CostProfile {
        val vehicle =
            vehicleValues[
                vehicleSpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        vehicleValues.lastIndex,
                    )
            ]
        val ownership =
            ownershipValues[
                ownershipSpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        ownershipValues.lastIndex,
                    )
            ]
        val energy =
            energyValues[
                energySpinner
                    .selectedItemPosition
                    .coerceIn(
                        0,
                        energyValues.lastIndex,
                    )
            ]

        val kmUnknown =
            monthlyKmUnknown.isChecked

        val estimate =
            numberOrNull(
                estimatedMonthlyKm,
            )?.takeIf {
                it > 0.0
            } ?: CostCalculator
                .DEFAULT_ESTIMATED_MONTHLY_KM

        return CostProfile(
            vehicleType = vehicle,
            ownershipType = ownership,
            energyMode = energy,
            combinationLiquidFuel =
                liquidValues[
                    liquidFuelSpinner
                        .selectedItemPosition
                        .coerceIn(
                            0,
                            liquidValues.lastIndex,
                        )
                ],
            fuelPricePerUnit =
                numberOrNull(
                    fuelPrice,
                ),
            fuelKmPerUnit =
                numberOrNull(
                    fuelConsumption,
                ),
            electricityPricePerKwh =
                numberOrNull(
                    electricityPrice,
                ),
            electricKwhPer100Km =
                numberOrNull(
                    electricityConsumption,
                ),
            ownershipMonthly =
                number(
                    ownershipMonthly,
                ),
            insuranceMonthly =
                number(
                    insuranceMonthly,
                ),
            maintenanceMonthly =
                number(
                    maintenanceMonthly,
                ),
            tiresMonthly =
                number(
                    tiresMonthly,
                ),
            otherMonthly =
                number(
                    otherMonthly,
                ),
            monthlyWorkKm =
                if (kmUnknown) {
                    null
                } else {
                    numberOrNull(
                        monthlyWorkKm,
                    )
                },
            monthlyWorkKmSource =
                if (kmUnknown) {
                    CostProfileValues
                        .SOURCE_ESTIMATED
                } else {
                    CostProfileValues
                        .SOURCE_USER
                },
            estimatedMonthlyWorkKm =
                estimate,
            averageJourneyHours =
                numberOrNull(
                    averageJourneyHours,
                ),
            monthlyWorkHours =
                numberOrNull(
                    monthlyWorkHours,
                ),
            updatedAt =
                Instant.now()
                    .toString(),
        )
    }

    private fun saveProfile() {
        val profile =
            collectProfile()

        if (
            profile.monthlyWorkKmSource ==
            CostProfileValues.SOURCE_USER &&
            (
                profile.monthlyWorkKm == null ||
                profile.monthlyWorkKm <= 0.0
                )
        ) {
            AlertDialog.Builder(this)
                .setTitle(
                    "Base de rateio",
                )
                .setMessage(
                    "Informe seus km de trabalho por mês ou marque “Não sei” para usar uma referência estimada.",
                )
                .setPositiveButton(
                    "OK",
                    null,
                )
                .show()
            return
        }

        val calculation =
            CostCalculator.calculate(
                profile,
            )

        if (!calculation.hasUsableCost) {
            AlertDialog.Builder(this)
                .setTitle(
                    "Custos insuficientes",
                )
                .setMessage(
                    "Informe pelo menos um custo utilizável: preço + consumo de energia/combustível ou algum custo mensal.",
                )
                .setPositiveButton(
                    "OK",
                    null,
                )
                .show()
            return
        }

        store.save(
            profile,
            syncState = 0,
        )

        repo.saveCostSnapshot(
            costPerKm =
                calculation
                    .effectiveCostPerKm,
            source =
                calculation.costSource,
            version =
                calculation.version,
            profileUpdatedAt =
                profile.updatedAt,
        )

        currentCalculation =
            calculation
        renderCalculation(
            calculation,
        )

        BackendClient.syncPreferences(
            this,
        )

        val settings =
            repo.load()

        if (
            settings.deviceToken.isBlank() ||
            !ConnectivityState.isOnline(this)
        ) {
            syncText.text =
                "Salvo no aparelho · sincronização pendente."
            toast(
                "Custos salvos no aparelho.",
            )
            return
        }

        syncText.text =
            "Sincronizando perfil..."

        CostProfileSync.push(
            this,
            profile,
        ) { result ->
            result.onSuccess {
                syncText.text =
                    "Perfil sincronizado com a nuvem."
                toast(
                    "Perfil de custos salvo.",
                )
            }.onFailure {
                syncText.text =
                    "Salvo no aparelho · sincronização pendente (${it.message})."
                toast(
                    "Custos salvos; nuvem será tentada depois.",
                )
            }
        }
    }

    private fun renderCurrentCalculation() {
        val profile =
            store.load()

        if (profile == null) {
            val legacy =
                repo.costSnapshot()

            resultText.text =
                "Custo atual legado: R$ ${format(legacy.costPerKm, 2)}/km.\n" +
                    "Configure o perfil para obter memória de cálculo e distinguir valores informados de estimados."
            currentCalculation = null
            return
        }

        val calculation =
            CostCalculator.calculate(
                profile,
            )

        currentCalculation =
            calculation
        renderCalculation(
            calculation,
        )
    }

    private fun renderCalculation(
        calculation: CostCalculation,
    ) {
        val example =
            CostCalculator.estimateForOffer(
                fare = 30.0,
                totalKm = 10.0,
                calculation = calculation,
            )

        val allocation =
            if (
                calculation.allocationSource ==
                CostProfileValues.SOURCE_USER
            ) {
                "informada"
            } else {
                "estimada"
            }

        resultText.text =
            "Custo operacional estimado: R$ ${format(calculation.effectiveCostPerKm, 4)}/km\n" +
                "Variável: R$ ${format(calculation.variableCostPerKm, 4)}/km · " +
                "fixos rateados: R$ ${format(calculation.fixedCostPerKm, 4)}/km\n" +
                "Base de km/mês: $allocation\n" +
                "Exemplo 10 km / oferta R$ 30,00: custo est. R$ ${format(example.first, 2)} · " +
                "Lucro est.* R$ ${format(example.second, 2)}" +
                if (
                    calculation.completeness ==
                    "partial"
                ) {
                    "\nPerfil parcial: há custos não informados."
                } else {
                    "\nPerfil completo para os componentes escolhidos."
                }
    }

    private fun showMemory() {
        val calculation =
            currentCalculation
                ?: run {
                    AlertDialog.Builder(this)
                        .setTitle(
                            "Memória do cálculo",
                        )
                        .setMessage(
                            "Configure e salve seus custos primeiro.",
                        )
                        .setPositiveButton(
                            "OK",
                            null,
                        )
                        .show()
                    return
                }

        AlertDialog.Builder(this)
            .setTitle(
                "Memória do cálculo",
            )
            .setMessage(
                calculation.memoryText(),
            )
            .setPositiveButton(
                "OK",
                null,
            )
            .show()
    }

    private fun spinner(
        items: List<String>,
    ) =
        Spinner(this).apply {
            adapter =
                ArrayAdapter(
                    this@CostProfileActivity,
                    android.R.layout
                        .simple_spinner_dropdown_item,
                    items,
                )
        }

    private fun label(
        text: String,
    ) =
        UiKit.body(
            this,
            text,
            12f,
        ).apply {
            setPadding(
                0,
                dp(9),
                0,
                dp(4),
            )
        }

    private fun number(
        editText: EditText,
    ): Double =
        numberOrNull(
            editText,
        ) ?: 0.0

    private fun numberOrNull(
        editText: EditText,
    ): Double? =
        editText.text
            .toString()
            .trim()
            .replace(
                ',',
                '.',
            )
            .toDoubleOrNull()
            ?.takeIf {
                it.isFinite() &&
                    it >= 0.0
            }

    private fun textNumber(
        value: Double?,
    ): String =
        value?.let {
            format(
                it,
                2,
            )
        } ?: ""

    private fun format(
        value: Double,
        decimals: Int,
    ): String =
        String.format(
            java.util.Locale(
                "pt",
                "BR",
            ),
            "%.${decimals}f",
            value,
        )

    private fun indexOfOrZero(
        values: List<String>,
        value: String,
    ): Int =
        values.indexOf(
            value,
        ).takeIf {
            it >= 0
        } ?: 0

    private fun dp(
        value: Int,
    ) =
        UiKit.dp(
            this,
            value,
        )

    private fun toast(
        message: String,
    ) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT,
        ).show()
    }
}
