import { adminSupabase } from './supabase';
import { radarContinuityEstimateV1 } from './radar-continuity-v1';
import { radarDestinationCellV1 } from './radar-destination-cell-v1';
import { eventRowToRadarContextV1 } from './radar-context-adapter-v1';
import { haversineKmV1, scoreRadarOpportunityV1, ContinuitySignalV1 } from './radar-opportunity-engine-v1';

const num=(v:unknown)=>{const n=Number(v);return Number.isFinite(n)?n:null};
const clamp=(n:number,a:number,b:number)=>Math.max(a,Math.min(b,n));

type Input={driverId:string;lat:number;lng:number;eta:string;destinationLabel?:string|null;serviceProfile?:string|null;radiusKm?:number;limit?:number;days?:number};

function signal(part:any, preferred:string):ContinuitySignalV1 {
  const h=part?.p10;
  return {
    probabilityPct:num(h?.probability_pct),
    samples:Math.max(0,Number(h?.eligible_intervals||0)),
    reliability:['low','medium','high'].includes(String(h?.reliability))?h.reliability:'insufficient',
    source:preferred==='personal'?'personal':preferred==='collective'?'collective':'insufficient',
  };
}

async function eventCandidates(lat:number,lng:number,eta:string,radiusKm:number){
  const t=new Date(eta); if(Number.isNaN(t.getTime())) throw new Error('invalid_eta');
  const from=new Date(t.getTime()-90*60_000).toISOString();
  const until=new Date(t.getTime()+6*60*60_000).toISOString();
  const {data,error}=await adminSupabase().from('sr_event_opportunities')
    .select('id,source,event_type,name,venue_name,address,lat,lng,starts_at,expected_end_at,egress_start_at,egress_end_at,confidence,status,metadata')
    .eq('status','active').gte('egress_end_at',from).lte('starts_at',until).limit(300);
  if(error) throw new Error(error.message);
  return (data||[]).filter((r:any)=>haversineKmV1(lat,lng,Number(r.lat),Number(r.lng))<=radiusKm);
}

async function economicEvidence(driverId:string, eta:string, destinationLabel?:string|null, serviceProfile?:string|null){
  if(!destinationLabel?.trim()) return null;
  const key=(v:string)=>v.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  const target=key(destinationLabel);
  const parts=new Intl.DateTimeFormat('en-US',{timeZone:'America/Sao_Paulo',weekday:'short',hour:'2-digit',hour12:false}).formatToParts(new Date(eta));
  const wd:{[k:string]:number}={Mon:1,Tue:2,Wed:3,Thu:4,Fri:5,Sat:6,Sun:7};
  const weekday=wd[parts.find(p=>p.type==='weekday')?.value||'Mon']||1;
  const hour=Math.floor((Number(parts.find(p=>p.type==='hour')?.value||0)%24)/3)*3;
  let q=adminSupabase().from('sr_personal_offer_region_hour_v1')
    .select('region_key,region_label,weekday_iso,hour_bucket,service_profile,sample_count,average_fare,average_per_km,average_per_minute,average_per_hour,average_pickup_km,average_pickup_minutes')
    .eq('driver_id',driverId).eq('weekday_iso',weekday).eq('hour_bucket',hour).order('sample_count',{ascending:false}).limit(250);
  if(serviceProfile?.trim()) q=q.eq('service_profile',serviceProfile.trim());
  const {data,error}=await q; if(error)return null;
  const rows: Array<{r:any;k:string}>=(data||[])
    .map((r:any)=>({r,k:key(String(r.region_label||''))}))
    .filter((x:{r:any;k:string})=>x.k && (target.includes(x.k)||x.k.includes(target)));
  if(!rows.length)return null;
  const best=rows.sort((a:{r:any;k:string},b:{r:any;k:string})=>Number(b.r.sample_count||0)-Number(a.r.sample_count||0))[0].r;
  return {region_key:best.region_key,region_label:best.region_label,samples:Number(best.sample_count||0),average_fare:num(best.average_fare),average_per_km:num(best.average_per_km),average_per_minute:num(best.average_per_minute),average_per_hour:num(best.average_per_hour),average_pickup_km:num(best.average_pickup_km),average_pickup_minutes:num(best.average_pickup_minutes),source:'operational_plus_canonical_v7'};
}

export async function destinationRadarOpportunitiesV1(input:Input){
  const eta=new Date(input.eta); if(Number.isNaN(eta.getTime())) throw new Error('invalid_eta');
  const radiusKm=clamp(input.radiusKm??5,1,15), limit=Math.round(clamp(input.limit??6,1,12));
  const destinationCell=radarDestinationCellV1(input.lat,input.lng);
  const baselineRaw:any=await radarContinuityEstimateV1(input.driverId,destinationCell,eta.toISOString(),input.days??60);
  const baselinePart=baselineRaw.preferred_source==='personal'?baselineRaw.personal:baselineRaw.preferred_source==='collective'?baselineRaw.collective:null;
  const baseline=signal(baselinePart,baselineRaw.preferred_source);
  const [rows,economics]=await Promise.all([eventCandidates(input.lat,input.lng,eta.toISOString(),radiusKm),economicEvidence(input.driverId,eta.toISOString(),input.destinationLabel,input.serviceProfile)]);

  // Phase 1: context candidates use destination-cell continuity as conservative evidence.
  // POI-specific cells become possible when persistent POI registry/geocell is introduced.
  const opportunities=rows.map((row:any)=>scoreRadarOpportunityV1({
    destination:{lat:input.lat,lng:input.lng,eta:eta.toISOString()},
    context:eventRowToRadarContextV1(row), target:baseline, baseline,
  })).sort((a:ReturnType<typeof scoreRadarOpportunityV1>,b:ReturnType<typeof scoreRadarOpportunityV1>)=>{
    const au=a.uplift??-999,bu=b.uplift??-999; if(au!==bu)return bu-au;
    return (b.score??-1)-(a.score??-1)||a.distanceKm-b.distanceKm;
  }).slice(0,limit);

  return {
    contract:'srrotas-radar-opportunities-v1',
    destination:{lat:input.lat,lng:input.lng,cell:destinationCell,eta:eta.toISOString()},
    baseline:{score:baseline.probabilityPct,confidence:baseline.reliability,samples:baseline.samples,source:baseline.source},
    opportunities,
    economic_context:economics?{available:true,...economics}:{available:false,source:'operational_plus_canonical_v7'},
    meta:{radius_km:radiusKm,candidates:rows.length,returned:opportunities.length,generated_at:new Date().toISOString(),phase:'contextual-v1'},
    note:'Contexto não garante demanda. Scores estatísticos só existem quando a continuidade possui amostra elegível.',
  };
}
