import { radarContinuityEstimateV1 } from "./radar-continuity-v1";
import { nearbyPoiOpportunitiesV1, geoCellV1, PoiOpportunityV1 } from "./radar-poi-v1";

export type RadarContextualEvidenceV1 = {
  kind:"continuity"|"context"|"distance"|"baseline"|"reposition"|"quality";
  label:string;
  value?:number|null;
  samples?:number|null;
  reliability?:string|null;
};

export type RadarContextualItemV1 = {
  id:string;
  poi_id:string;
  title:string;
  subtitle:string|null;
  poi_type:string;
  lat:number;
  lng:number;
  distance_km:number;
  eta:string;
  continuity_probability_pct:number|null;
  baseline_probability_pct:number|null;
  delta_probability_pct:number|null;
  confidence:number;
  ranking_score:number;
  potential:"high"|"medium"|"low"|"insufficient";
  reason_headline:string;
  optimal_window:{start:string|null;end:string|null};
  context:null|{
    id:string; name:string; type:string;
    egress_start_at:string; egress_end_at:string; confidence:number;
  };
  evidence:RadarContextualEvidenceV1[];
};

export type RadarAssistantSignalV1 = {
  eligible:boolean;
  reason:string;
  headline:string|null;
  action_label:"Ver"|null;
  opportunity_id:string|null;
  min_eta_minutes:number;
  max_eta_minutes:number;
};

function n(v:any):number|null {
  const x=Number(v); return Number.isFinite(x)?x:null;
}
function round(v:number,d=1){const p=10**d;return Math.round(v*p)/p;}
function selectedP10(raw:any) {
  const p=raw?.personal?.p10;
  if(p?.probability_pct!=null) return {p:n(p.probability_pct),samples:Number(p.eligible_intervals||0),reliability:String(p.reliability||"insufficient"),source:"personal"};
  const c=raw?.collective?.p10;
  if(c?.probability_pct!=null) return {p:n(c.probability_pct),samples:Number(c.eligible_intervals||0),reliability:String(c.reliability||"insufficient"),source:"collective"};
  const samples=Math.max(Number(p?.eligible_intervals||0),Number(c?.eligible_intervals||0));
  return {p:null,samples,reliability:"insufficient",source:"insufficient"};
}

function minutesBetween(a:string,b:string) {
  return (new Date(a).getTime()-new Date(b).getTime())/60_000;
}

/**
 * Ranking != probabilidade.
 * É apenas ordenação operacional entre candidatos já explicados.
 */
export function contextualRankingScoreV1(input:{
  probability:number|null; baseline:number|null; distanceKm:number;
  confidence:number; hasContext:boolean; eta:string; contextStart?:string|null; contextEnd?:string|null;
}) {
  if(input.probability===null) {
    const discovery = (input.hasContext?18:0) + Math.max(0,12-input.distanceKm*3) + input.confidence*10;
    return round(Math.max(0,Math.min(39,discovery)),1);
  }
  const baseline=input.baseline ?? input.probability;
  const delta=input.probability-baseline;
  const continuity=Math.max(0,Math.min(55,input.probability*.55));
  const improvement=Math.max(-12,Math.min(18,delta*.45));
  const distancePenalty=Math.min(22,input.distanceKm*4.0);
  let temporal=0;
  if(input.hasContext && input.contextStart && input.contextEnd) {
    const toStart=minutesBetween(input.contextStart,input.eta);
    const toEnd=minutesBetween(input.contextEnd,input.eta);
    temporal = toStart<=20 && toEnd>=-10 ? 12 : toStart<=60 && toEnd>=-30 ? 7 : 2;
  }
  const confidence=input.confidence*12;
  return round(Math.max(0,Math.min(100,continuity+improvement+temporal+confidence-distancePenalty)),1);
}

function potential(score:number, probability:number|null, samples:number) {
  if(probability===null || samples<20) return "insufficient" as const;
  if(score>=68) return "high" as const;
  if(score>=48) return "medium" as const;
  return "low" as const;
}

function headline(item:{
  delta:number|null; probability:number|null; distance:number; contextName?:string|null; baseline:number|null;
}) {
  if(item.probability===null) {
    return item.contextName ? `${item.contextName} coincide com sua chegada` : "Contexto próximo, ainda sem amostra suficiente";
  }
  if(item.delta!==null && item.delta>=10) return "Continuidade melhor que permanecer no destino";
  if(item.delta!==null && item.delta>=3) return "Leve vantagem de continuidade nas proximidades";
  if(item.distance<=0.8) return "Opção próxima com continuidade observada";
  return "Alternativa contextual próxima ao destino";
}

function assistantSignal(items:RadarContextualItemV1[], destinationEta:string, nowIso:string):RadarAssistantSignalV1 {
  const minutesToEta=minutesBetween(destinationEta,nowIso);
  const best=items.find(x =>
    x.potential==="high" &&
    x.confidence>=0.70 &&
    x.ranking_score>=68 &&
    (x.delta_probability_pct===null || x.delta_probability_pct>=5) &&
    x.distance_km<=3.5
  );
  if(!best) return {eligible:false,reason:"no_strong_opportunity",headline:null,action_label:null,opportunity_id:null,min_eta_minutes:4,max_eta_minutes:18};
  if(minutesToEta<4 || minutesToEta>18) return {eligible:false,reason:"outside_assistant_eta_window",headline:null,action_label:null,opportunity_id:best.id,min_eta_minutes:4,max_eta_minutes:18};
  return {
    eligible:true,
    reason:"strong_destination_continuity",
    headline:"Boa chance de continuidade no destino.",
    action_label:"Ver",
    opportunity_id:best.id,
    min_eta_minutes:4,
    max_eta_minutes:18
  };
}

export async function radarContextualV1(input:{
  driverId:string;
  destination:{lat:number;lng:number;eta:string;label?:string|null};
  radiusKm?:number;
  limit?:number;
  nowIso?:string;
}) {
  const eta=new Date(input.destination.eta);
  if(Number.isNaN(eta.getTime())) throw new Error("invalid_eta");

  const destinationCell=geoCellV1(input.destination.lat,input.destination.lng);
  const [baselineRaw, poiRows] = await Promise.all([
    radarContinuityEstimateV1(input.driverId,destinationCell,eta.toISOString(),60),
    nearbyPoiOpportunitiesV1({
      driverId:input.driverId,
      lat:input.destination.lat,
      lng:input.destination.lng,
      eta:eta.toISOString(),
      radiusKm:Math.max(.5,Math.min(input.radiusKm??4,15)),
      limit:Math.max(3,Math.min(input.limit??8,12))
    })
  ]);

  const baseline=selectedP10(baselineRaw);

  const items:RadarContextualItemV1[]=poiRows.map((poi:PoiOpportunityV1)=>{
    const statSamples = poi.evidence.find(e=>e.source==="personal_cell"||e.source==="collective_cell")?.samples ?? 0;
    const delta = poi.probability_pct!==null && baseline.p!==null ? round(poi.probability_pct-baseline.p,1) : null;
    const score=contextualRankingScoreV1({
      probability:poi.probability_pct,
      baseline:baseline.p,
      distanceKm:poi.distance_km,
      confidence:poi.confidence,
      hasContext:Boolean(poi.context),
      eta:eta.toISOString(),
      contextStart:poi.context?.egress_start_at,
      contextEnd:poi.context?.egress_end_at
    });
    const evidence:RadarContextualEvidenceV1[]=[
      {
        kind:"baseline",
        label:baseline.p===null
          ? `Destino: amostra insuficiente (${baseline.samples})`
          : `Permanecer no destino: ${baseline.p}% em até 10 min`,
        value:baseline.p,samples:baseline.samples,reliability:baseline.reliability
      },
      ...poi.evidence.map(e=>({
        kind:(e.source==="context"?"context":e.source==="distance"?"distance":"continuity") as any,
        label:e.label,value:e.probability_pct ?? null,samples:e.samples ?? null,reliability:e.reliability ?? null
      }))
    ];
    if(delta!==null) evidence.push({
      kind:"reposition",
      label:delta>=0?`+${delta} p.p. versus permanecer no destino`:`${delta} p.p. versus permanecer no destino`,
      value:delta
    });
    return {
      id:`poi:${poi.poi_id}`,
      poi_id:poi.poi_id,
      title:poi.name,
      subtitle:poi.context?.name ?? null,
      poi_type:poi.poi_type,
      lat:poi.lat,lng:poi.lng,
      distance_km:poi.distance_km,
      eta:eta.toISOString(),
      continuity_probability_pct:poi.probability_pct,
      baseline_probability_pct:baseline.p,
      delta_probability_pct:delta,
      confidence:poi.confidence,
      ranking_score:score,
      potential:potential(score,poi.probability_pct,Number(statSamples)),
      reason_headline:headline({
        delta,probability:poi.probability_pct,distance:poi.distance_km,
        contextName:poi.context?.name,baseline:baseline.p
      }),
      optimal_window:{
        start:poi.context?.egress_start_at ?? null,
        end:poi.context?.egress_end_at ?? null
      },
      context:poi.context,
      evidence
    };
  }).sort((a,b)=>b.ranking_score-a.ranking_score || b.confidence-a.confidence || a.distance_km-b.distance_km);

  const nowIso=input.nowIso ?? new Date().toISOString();

  return {
    schema_version:"srrotas-radar-contextual-v1",
    generated_at:nowIso,
    destination:{
      lat:input.destination.lat,lng:input.destination.lng,
      eta:eta.toISOString(),label:input.destination.label ?? null,
      geo_cell:destinationCell
    },
    baseline:{
      probability_pct:baseline.p,
      samples:baseline.samples,
      reliability:baseline.reliability,
      source:baseline.source
    },
    opportunities:items.slice(0,input.limit??8),
    assistant:assistantSignal(items,eta.toISOString(),nowIso),
    semantics:{
      ranking_score:"Índice interno de ordenação; NÃO é probabilidade de corrida.",
      continuity_probability_pct:"Probabilidade estatística somente quando há exposição observada suficiente.",
      external_context:"Contexto externo explica temporalidade e nunca cria demanda por si só."
    }
  };
}
