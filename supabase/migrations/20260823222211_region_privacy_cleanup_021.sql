begin;

create or replace function public.sr_region_canonical_label_v1(value text)
returns text
language plpgsql
immutable
parallel safe
as $$
declare
  base text := public.sr_region_label_v1(value);
  key text;
begin
  if base is null then return null; end if;
  key := public.sr_text_key_v1(base);

  -- 0.21 privacy hardening: a superfície pública é de região/bairro/cidade,
  -- nunca rua, endereço, terminal, shopping, hotel ou outro POI específico.
  if key = ''
     or length(base) > 55
     or base ~ '[0-9]{2,}'
     or key ~ '^(rua|r|avenida|av|alameda|al|estrada|rodovia|travessa|tv|praca|largo)-'
     or key ~ '(shopping|plaza|terminal|hospital|hotel|aeroporto|airport|bus-terminal|ponto-de-encontro|entrada-principal)'
     or key in ('bela','vista','sao','paulo','1-parada','area-semi-coberta')
     or key ~ '(parada|como-foi|viagem-longa|ajude-a-melhorar)'
  then return null; end if;

  return case
    when key in ('consolagao','consolacao','consolacgao','consola-gao','consola-ao') or key like 'consola%ao' then 'Consolação'
    when key in ('itaim-bibi','ltaim-bibi') then 'Itaim Bibi'
    when key = 'campo-belo' or key like 'campo%belo' then 'Campo Belo'
    when key in ('saude','satide') then 'Saúde'
    when key in ('chacara-santo-antenio','chacara-santo-antonio') then 'Chácara Santo Antônio'
    when key in ('higienopolis','higiendpolis') then 'Higienópolis'
    when key in ('indianopolis','indiandpolis') then 'Indianópolis'
    when key='cerqueira-cesar' then 'Cerqueira César'
    when key='bairro-de-pinheiros' then 'Pinheiros'
    when key='republica' or key like '%republica' then 'República'
    when key='paraiso' then 'Paraíso'
    when key='limao' then 'Limão'
    when key='agua-branca' then 'Água Branca'
    when key='santa-cecilia' then 'Santa Cecília'
    when key='vila-olimpia' then 'Vila Olímpia'
    when key='butanta' then 'Butantã'
    when key='sacoma' then 'Sacomã'
    when key='vila-maria-vila-guilherme' then 'Vila Maria / Vila Guilherme'
    else base
  end;
end;
$$;

select public.sr_refresh_region_seed_v1();

commit;
