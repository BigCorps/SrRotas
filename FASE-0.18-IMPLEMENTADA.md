# Sr. Rotas 0.18 — Custos pessoais e Lucro est.*

## Objetivo

Transformar o antigo campo isolado `cost_per_km` em um perfil de custos explicável, sem reabrir o Offer Engine v1.

## Arquitetura

```text
Perfil informado pelo motorista
        ↓
CostCalculator determinístico
        ↓
custo operacional estimado / km
        ↓
SettingsRepository.costPerKm
        ↓
Offer Engine v1 congelado
        ↓
estimated_cost / estimated_profit
        ↓
snapshot auditável da oferta
```

## Fontes

Valores digitados pelo motorista:
`userProvided`

Base de km/mês quando “Não sei”:
`estimated`

Campos faltantes:
- não são inventados;
- ficam fora do cálculo;
- aparecem em `missingInputs`;
- o perfil fica `partial`.

## Modelo de custo v1

`sr-cost-v0.18.0`

Componentes:
- combustível líquido/km;
- eletricidade/km;
- custos mensais;
- rateio mensal/km;
- custo variável/km;
- custo fixo/km;
- custo operacional efetivo/km.

## Perfis híbridos

Quando `energy_mode = combination`, o cálculo soma os componentes líquidos e elétricos informados.

Portanto os consumos digitados devem ser médias efetivas do uso real combinado do veículo.

## Histórico

Novas ofertas guardam:
- `cost_per_km_used`;
- `cost_source`;
- `cost_profile_version`;
- `cost_profile_updated_at`.

Ofertas antigas:
- têm custo reconstruído quando `estimated_cost` e `total_km` permitem;
- nunca têm verdict recalculado pela migration.

Se o perfil mudar enquanto um card ainda está no estabilizador, o dispatcher compara o snapshot atual com `estimated_cost / total_km`. Em caso de divergência, preserva a aritmética real da oferta como `runtime_reconstructed`, evitando memória falsa.

## Offline

SQLite principal sobe:
`v4 → v5`

O snapshot de custo passa a ficar em `local_offers`.

Isso garante que uma oferta feita offline seja sincronizada depois com a mesma referência de custo que estava ativa na captura.

O perfil de custos fica em armazenamento local separado:
`local_cost_profile`

## Memória

O perfil atual mostra memória detalhada no Android.

O backend mantém `driver_cost_profile_revisions`, preparando consulta histórica de qual composição estava ativa em cada período.

## Segurança

`driver_cost_profiles` e revisões:
- RLS habilitado;
- sem acesso `anon`;
- sem acesso `authenticated`;
- backend `service_role`.

Nenhuma função `SECURITY DEFINER` nova.

## Limitação deliberada

`Lucro est.*` continua sendo estimativa operacional.

Não inclui automaticamente:
- impostos;
- depreciação contábil;
- multas;
- estacionamento;
- pedágio não presente na oferta;
- qualquer custo que o motorista não tenha informado.

A memória deixa essas ausências explícitas.

## Próximo marco

0.19 — validação de campo consolidada:
- OCR;
- Context Engine;
- Radar;
- jornada;
- exposição;
- importação;
- estatística;
- custos;
- memória do Lucro est.*;
- consumo de bateria/CPU;
- sync.
