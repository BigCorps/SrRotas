# Backend Radar Contextual — estado R1

Data: 02/10/2026.

Validação feita primeiro em transação com ROLLBACK e depois aplicada.

Resultado atual:
- 4 POIs canônicos;
- 4 aliases;
- 7 eventos ativos com poi_id;
- 0 eventos ativos sem poi_id;
- 4 decisões created;
- 3 decisões linked;
- 0 REVIEW com poi_id;
- 0 contextual_events antes do teste;
- 0 learning_rows antes do teste.

Os links repetidos foram aceitos somente quando `venue_name`, endereço e coordenadas eram idênticos.

Advisors após R1:
- apenas INFO de RLS server-only já conhecida;
- índices novos ainda podem aparecer como unused até receber tráfego;
- nenhum novo alerta crítico.

NÃO reaplicar migrations Radar apenas por causa deste pacote.
