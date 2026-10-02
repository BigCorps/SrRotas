import { adminSupabase } from "./supabase";
import { haversineKm026 } from "./event-radar-026";

type EventRow = {
  id:string; source:string; external_id:string; event_type:string; name:string;
  venue_name:string|null; address:string|null; city:string|null; state:string|null;
  lat:number; lng:number; source_url:string|null; confidence:number; metadata:any;
};

type PoiRow = {
  id:string; canonical_name:string; normalized_name:string; poi_type:string;
  address:string|null; city:string|null; state:string|null; lat:number; lng:number;
  geo_cell:string; source:string; source_external_id:string|null;
  source_confidence:number; aliases?: Array<{normalized_alias:string}>;
};

export type PoiResolutionDecisionV1 = {
  event_id:string; poi_id:string|null;
  decision:"linked"|"created"|"review"|"skipped";
  score:number; name_score:number; distance_m:number|null; address_score:number;
  reasons:string[];
};

const STOP = new Set(["de","da","do","das","dos","e","em","a","o","the","centro","center"]);

export function normalizePoiTextV1(value:unknown):string {
  return String(value ?? "")
    .normalize("NFD").replace(/[\u0300-\u036f]/g,"")
    .toLowerCase()
    .replace(/&/g," e ")
    .replace(/[^a-z0-9]+/g," ")
    .trim().replace(/\s+/g," ");
}

function tokens(value:string) {
  return new Set(normalizePoiTextV1(value).split(" ").filter(x => x.length > 1 && !STOP.has(x)));
}

export function nameSimilarityV1(a:string,b:string):number {
  const na=normalizePoiTextV1(a), nb=normalizePoiTextV1(b);
  if(!na || !nb) return 0;
  if(na===nb) return 1;
  if(na.includes(nb) || nb.includes(na)) return Math.min(0.96, Math.min(na.length,nb.length)/Math.max(na.length,nb.length)+0.18);
  const A=tokens(na), B=tokens(nb);
  const inter=[...A].filter(x=>B.has(x)).length;
  const union=new Set([...A,...B]).size;
  return union ? inter/union : 0;
}

export function geoCellV1(lat:number,lng:number) {
  if(!Number.isFinite(lat)||!Number.isFinite(lng)||lat < -90||lat > 90||lng < -180||lng > 180) throw new Error("invalid_location");
  return `g2:${Math.floor(lat*100)}:${Math.floor(lng*100)}`;
}

function neighborCells(cell:string):string[] {
  const m=/^g2:(-?\d+):(-?\d+)$/.exec(cell);
  if(!m) return [cell];
  const a=Number(m[1]),b=Number(m[2]),out:string[]=[];
  for(let da=-1;da<=1;da++) for(let db=-1;db<=1;db++) out.push(`g2:${a+da}:${b+db}`);
  return out;
}

function addressSimilarity(a:string|null,b:string|null) {
  if(!a || !b) return 0;
  const A=tokens(a),B=tokens(b),i=[...A].filter(x=>B.has(x)).length,u=new Set([...A,...B]).size;
  return u?i/u:0;
}

function typeCompatible(eventType:string,poiType:string) {
  const t=eventType.toLowerCase(), p=poiType.toLowerCase();
  if(t===p) return true;
  const groups = [
    new Set(["theatre","cultural","event"]),
    new Set(["music","cultural","event"]),
    new Set(["fair_convention","event","corporate"]),
    new Set(["sports","arena","stadium","event"]),
    new Set(["airport","mobility_hub"]),
    new Set(["bus_terminal","mobility_hub"]),
    new Set(["mall","commercial"])
  ];
  return groups.some(g=>g.has(t)&&g.has(p));
}

function scoreCandidate(event:EventRow,poi:PoiRow,aliases:string[]) {
  const names=[poi.canonical_name,...aliases];
  const eventNames=[event.venue_name,event.name].filter(Boolean) as string[];
  let ns=0;
  for(const e of eventNames) for(const p of names) ns=Math.max(ns,nameSimilarityV1(e,p));
  const km=haversineKm026(event.lat,event.lng,Number(poi.lat),Number(poi.lng));
  const distanceM=Math.round(km*1000);
  const distanceScore = distanceM<=40?1:distanceM<=100?.95:distanceM<=200?.82:distanceM<=350?.65:distanceM<=700?.30:0;
  const as=addressSimilarity(event.address,poi.address);
  const typeScore=typeCompatible(event.event_type,poi.poi_type)?1:.55;
  const total = Math.max(0,Math.min(1,ns*.52 + distanceScore*.28 + as*.12 + typeScore*.08));
  return {score:total,nameScore:ns,distanceM,addressScore:as};
}

async function logDecision(d:PoiResolutionDecisionV1) {
  await adminSupabase().from("sr_radar_poi_resolution_log").insert({
    event_id:d.event_id,poi_id:d.poi_id,decision:d.decision,score:d.score,
    name_score:d.name_score,distance_m:d.distance_m,address_score:d.address_score,reasons:d.reasons
  });
}

async function aliasesByPoi(ids:string[]) {
  const result=new Map<string,string[]>();
  if(!ids.length) return result;
  const q=await adminSupabase().from("sr_radar_poi_aliases").select("poi_id,normalized_alias").in("poi_id",ids);
  if(q.error) return result;
  for(const r of q.data??[]) result.set(r.poi_id,[...(result.get(r.poi_id)??[]),String(r.normalized_alias)]);
  return result;
}

function observedPoiNames(event:EventRow) {
  const canonical=(event.venue_name || event.name).trim();
  const names=[canonical];
  if(event.name && normalizePoiTextV1(event.name)!==normalizePoiTextV1(canonical) && nameSimilarityV1(event.name,canonical)>=.72) names.push(event.name);
  return [...new Set(names.filter(Boolean))];
}

async function createPoiFromEvent(event:EventRow):Promise<string> {
  const canonical=(event.venue_name || event.name).trim();
  const observed=observedPoiNames(event);
  const row={
    canonical_name:canonical,
    normalized_name:normalizePoiTextV1(canonical),
    poi_type:event.event_type,
    address:event.address,city:event.city,state:event.state,country_code:"BR",
    lat:event.lat,lng:event.lng,geo_cell:geoCellV1(event.lat,event.lng),
    source:event.source,source_external_id:event.external_id?.trim()||null,source_url:event.source_url,
    source_confidence:Math.max(.35,Math.min(.95,Number(event.confidence||.5))),
    aliases:observed,
    metadata:{created_from_event_id:event.id},
    last_verified_at:new Date().toISOString()
  };
  const q=await adminSupabase().from("sr_radar_pois").insert(row).select("id").single();
  if(q.error) throw new Error(q.error.message);
  const id=String(q.data.id);
  const aliases=observed.map(a=>({poi_id:id,alias:a,normalized_alias:normalizePoiTextV1(a),source:event.source}));
  if(aliases.length) await adminSupabase().from("sr_radar_poi_aliases").upsert(aliases,{onConflict:"poi_id,normalized_alias"});
  return id;
}

export async function resolveRadarEventPoiV1(event:EventRow):Promise<PoiResolutionDecisionV1> {
  if(!event?.id || !Number.isFinite(Number(event.lat)) || !Number.isFinite(Number(event.lng)) || Number(event.lat)<-90 || Number(event.lat)>90 || Number(event.lng)<-180 || Number(event.lng)>180) {
    const d={event_id:event?.id||"",poi_id:null,decision:"skipped" as const,score:0,name_score:0,distance_m:null,address_score:0,reasons:["Evento sem identidade/coordenada suficiente"]};
    if(event?.id) await logDecision(d); return d;
  }

  if(event.source?.trim() && event.external_id?.trim()) {
    const exact=await adminSupabase().from("sr_radar_pois")
      .select("id,canonical_name")
      .eq("status","active").eq("source",event.source.trim()).eq("source_external_id",event.external_id.trim())
      .maybeSingle();
    if(exact.error) throw new Error(exact.error.message);
    if(exact.data?.id) {
      const poiId=String(exact.data.id);
      const linked=await adminSupabase().from("sr_event_opportunities").update({poi_id:poiId}).eq("id",event.id);
      if(linked.error) throw new Error(linked.error.message);
      const aliasRows=observedPoiNames(event).map(a=>({poi_id:poiId,alias:a,normalized_alias:normalizePoiTextV1(a),source:event.source}));
      if(aliasRows.length) { const a=await adminSupabase().from("sr_radar_poi_aliases").upsert(aliasRows,{onConflict:"poi_id,normalized_alias"}); if(a.error) throw new Error(a.error.message); }
      const d={event_id:event.id,poi_id:poiId,decision:"linked" as const,score:1,name_score:1,distance_m:0,address_score:event.address?1:0,reasons:["source + external_id correspondem ao POI canônico"]};
      await logDecision(d); return d;
    }
  }

  const cells=neighborCells(geoCellV1(Number(event.lat),Number(event.lng)));
  const q=await adminSupabase().from("sr_radar_pois")
    .select("id,canonical_name,normalized_name,poi_type,address,city,state,lat,lng,geo_cell,source,source_external_id,source_confidence")
    .eq("status","active").in("geo_cell",cells).limit(80);
  if(q.error) throw new Error(q.error.message);

  const candidates=(q.data??[]) as PoiRow[];
  const aliasMap=await aliasesByPoi(candidates.map(p=>p.id));
  const ranked=candidates.map(p=>({poi:p,...scoreCandidate(event,p,aliasMap.get(p.id)??[])}))
    .sort((a,b)=>b.score-a.score);

  const best=ranked[0], second=ranked[1];
  const ambiguous=Boolean(best && second && best.score-second.score<.07 && second.score>=.72);

  if(best && best.score>=.82 && best.distanceM<=350 && !ambiguous) {
    await adminSupabase().from("sr_event_opportunities").update({poi_id:best.poi.id}).eq("id",event.id);
    const observed=observedPoiNames(event);
    const aliasRows=observed.map(a=>({poi_id:best.poi.id,alias:a,normalized_alias:normalizePoiTextV1(a),source:event.source}));
    if(aliasRows.length) await adminSupabase().from("sr_radar_poi_aliases").upsert(aliasRows,{onConflict:"poi_id,normalized_alias"});
    const d={event_id:event.id,poi_id:best.poi.id,decision:"linked" as const,score:best.score,name_score:best.nameScore,distance_m:best.distanceM,address_score:best.addressScore,
      reasons:["Nome/local compatíveis","Distância dentro do limite","Associação automática acima do threshold"]};
    await logDecision(d); return d;
  }

  if(ambiguous || (best && best.score>=.64)) {
    const d={event_id:event.id,poi_id:null,decision:"review" as const,score:best?.score??0,name_score:best?.nameScore??0,distance_m:best?.distanceM??null,address_score:best?.addressScore??0,
      reasons:[ambiguous?"Dois candidatos muito próximos":"Candidato parcial sem confiança para auto-link","Nenhum poi_id foi gravado"]};
    await logDecision(d); return d;
  }

  const sourceConfidence=Number(event.confidence||0);
  if(sourceConfidence>=.65 && (event.venue_name||event.name)) {
    const poiId=await createPoiFromEvent(event);
    await adminSupabase().from("sr_event_opportunities").update({poi_id:poiId}).eq("id",event.id);
    const d={event_id:event.id,poi_id:poiId,decision:"created" as const,score:sourceConfidence,name_score:1,distance_m:0,address_score:event.address?1:0,
      reasons:["Nenhum POI compatível próximo","Fonte possui confiança suficiente","Novo POI criado a partir da evidência da fonte"]};
    await logDecision(d); return d;
  }

  const d={event_id:event.id,poi_id:null,decision:"review" as const,score:sourceConfidence,name_score:0,distance_m:null,address_score:0,
    reasons:["Nenhum POI forte","Confiança da fonte insuficiente para criar POI automaticamente"]};
  await logDecision(d); return d;
}

export async function reconcileUnlinkedRadarEventsV1(limit=200) {
  const q=await adminSupabase().from("sr_event_opportunities")
    .select("id,source,external_id,event_type,name,venue_name,address,city,state,lat,lng,source_url,confidence,metadata")
    .is("poi_id",null).eq("status","active").order("starts_at",{ascending:true})
    .limit(Math.max(1,Math.min(limit,500)));
  if(q.error) throw new Error(q.error.message);
  const decisions:PoiResolutionDecisionV1[]=[];
  for(const event of q.data??[]) decisions.push(await resolveRadarEventPoiV1(event as EventRow));
  const counts=decisions.reduce<Record<string,number>>((a,d)=>(a[d.decision]=(a[d.decision]??0)+1,a),{});
  return {processed:decisions.length,counts,decisions};
}

export async function searchRadarPoisV1(query:string,limit=20) {
  const nq=normalizePoiTextV1(query);
  if(nq.length<2) return [];
  const q=await adminSupabase().from("sr_radar_pois")
    .select("id,canonical_name,poi_type,address,city,state,lat,lng,geo_cell,source_confidence,status")
    .eq("status","active").ilike("normalized_name",`%${nq}%`).limit(Math.max(1,Math.min(limit,50)));
  if(q.error) throw new Error(q.error.message);
  return q.data??[];
}
