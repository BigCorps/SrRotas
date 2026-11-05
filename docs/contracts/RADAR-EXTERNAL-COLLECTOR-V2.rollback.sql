-- Contenção da extensão V2, sem apagar schema/histórico nem reprocessar canônicos.
-- Conferir projeto, backup e responsável antes de executar em produção.
begin;
select set_config('sr.radar.mode','review',true);
select set_config('sr.radar.actor','operator_rollback',true);
update public.sr_radar_collectors set status='revoked',revoked_at=coalesce(revoked_at,now()),changed_by='operator_rollback';
update public.sr_event_opportunities set status='expired',updated_at=now() where collector_id is not null and status='active';
update public.sr_radar_pois set status='inactive',updated_at=now() where metadata ? 'collector_id' and status='active';
revoke execute on function public.sr_radar_receive_v2(uuid,text,text,text,text,jsonb,jsonb,int,boolean),public.sr_radar_review_v2(text,uuid,bigint,text,text,text),public.sr_radar_budget_v2(text,int,int),public.sr_radar_run_health_v2(bigint[]) from service_role;
select set_config('sr.radar.mode','',true);
select set_config('sr.radar.actor','',true);
commit;
-- Não remover review_status, coletores ou guards. Deploy anterior não pode contornar a revisão.
-- Para retomar: grants explícitos dos RPCs após revisão, credencial válida e homologação de dados.
