# QA 0.33.7-field / vc84

1. Instalar por cima da 0.33.6 sem limpar dados.
2. Abrir com internet e aguardar 20 segundos.
3. Usar normalmente e exportar JSON.

Esperado em `access_resolver_10b`:
- attempts >= 1
- success >= 1
- claim_ok = true
- device_identity_bound = true
- active_identity_devices = 1 no primeiro aparelho
- max_active_devices = 2
- enforcement_mode = observe
- exports_raw_device_identity = false
- exports_device_identity_hash = false

Depois, sair/entrar novamente no MESMO aparelho e exportar novo JSON. A contagem deve continuar 1.
