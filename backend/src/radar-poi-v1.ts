import { adminSupabase } from "./supabase";
import { radarContinuityEstimateV1 } from "./radar-continuity-v1";
import { haversineKm026 } from "./event-radar-026";

export type PoiEvidenceV1 = {
  source: "personal_cell"|"collective_cell"|"v7_region"|"context"|"distance";
  label: string;
  probability_pct?: number|null;
  samples?: number;
  reliability?: string;
};

export type PoiOpportunityV1 = {
  poi_id:string; name:string; poi_type:string; lat:number; lng:number;
  distance_km:number; geo_cell:string; eta:string;
  probability_pct:number|null; confidence:number; potential:"high"|"medium"|"low"|"insufficient";
  evidence:PoiEvidenceV1[];
  context:null|{id:string;name:string;type:string;egress_start_at:string;egress_end_at:string;confidence:number};
};

export function geoCellV1(lat:number,lng:number) {
  if (!Number.isFinite(lat)||!Number.isFinite(lng)||lat < -90||lat > 90||lng < -180||lng > 180) throw new Error("invalid_location");
  return `g2:${Math.floor(lat*100)}:${Math.floor(lng*100)}`;
}
function n(v:any){ const x=Number(v); return Number.isFinite(x)?x:null; }
function level(p:number|null, samples:number) {
  if (p===null || samples<20) return "insufficient" as const;
  if (p>=70) return "high" as const;
  if (p>=45) return "medium" as const;
  return "low" as const;
}
function selectedContinuity(raw:any) {
  const p=raw?.personal?.p10;
  if (p?.probability_pct != null) return {source:"personal_cell" as const,p:n(p.probability_pct),samples:Number(p.eligible_intervals||0),reliability:String(p.reliability||"insufficient")};
  const c=raw?.collective?.p10;
  if (c?.probability_pct != null) return {source:"collective_cell" as const,p:n(c.probability_pct),samples:Number(c.eligible_intervals||0),reliability:String(c.reliability||"insufficient")};
  return {source:"personal_cell" as const,p:null,samples:Math.max(Number(p?.eligible_intervals||0),Number(c?.eligible_intervals||0)),reliability:"insufficient"};
}

export async function nearbyPoiOpportunitiesV1(input:{
 driverId:string; lat:number; lng:number; eta:string; radiusKm:number; limit?:number;
}) {
 const eta=new Date(input.eta); if(Number.isNaN(eta.getTime())) throw new Error("invalid_eta");
 const radius=Math.max(0.5,Math.min(input.radiusKm||3,15));
 const latDelta=radius/111;
 const lngScale=Math.max(20,111*Math.cos(input.lat*Math.PI/180));
 const lngDelta=radius/lngScale;
 const {data,error}=await adminSupabase().from("sr_radar_pois")
   .select("id,canonical_name,poi_type,lat,lng,geo_cell,region_key,region_label,source_confidence")
   .eq("status","active")
   .gte("lat",input.lat-latDelta).lte("lat",input.lat+latDelta)
   .gte("lng",input.lng-lngDelta).lte("lng",input.lng+lngDelta)
   .limit(300);
 if(error) throw new Error(error.message);
 const nearby=(data??[]).map((p:any)=>({...p,distance_km:haversineKm026(input.lat,input.lng,Number(p.lat),Number(p.lng))}))
   .filter((p:any)=>p.distance_km<=radius).sort((a:any,b:any)=>a.distance_km-b.distance_km).slice(0,Math.max(6,(input.limit||6)*2));

 const uniqueCells:string[]=[...new Set<string>(nearby.map((p:any)=>String(p.geo_cell)))].slice(0,12);
 const stats=new Map<string,any>();
 for(let i=0;i<uniqueCells.length;i+=4) {
   const batch=uniqueCells.slice(i,i+4);
   await Promise.all(batch.map(async cell=>stats.set(cell,await radarContinuityEstimateV1(input.driverId,cell,eta.toISOString(),60))));
 }

 const poiIds=nearby.map((p:any)=>p.id);
 const contexts=new Map<string,any>();
 if(poiIds.length){
   const from=new Date(eta.getTime()-60*60_000).toISOString(), to=new Date(eta.getTime()+4*60*60_000).toISOString();
   const q=await adminSupabase().from("sr_event_opportunities")
    .select("id,poi_id,name,event_type,egress_start_at,egress_end_at,confidence")
    .in("poi_id",poiIds).eq("status","active").gte("egress_end_at",from).lte("egress_start_at",to).limit(200);
   if(!q.error) for(const c of q.data??[]) {
     const prev=contexts.get(c.poi_id);
     const delta=Math.abs(new Date(c.egress_start_at).getTime()-eta.getTime());
     if(!prev||delta<prev.delta) contexts.set(c.poi_id,{...c,delta});
   }
 }
 const out:PoiOpportunityV1[]=nearby.map((p:any)=>{
   const s=selectedContinuity(stats.get(String(p.geo_cell))), context=contexts.get(p.id);
   const evidence:PoiEvidenceV1[]=[
     {source:s.source,label:s.p===null?"Amostra insuficiente nesta célula":`Continuidade observada na célula: ${s.p}%`,probability_pct:s.p,samples:s.samples,reliability:s.reliability},
     {source:"distance",label:`${p.distance_km.toFixed(1)} km do destino`}
   ];
   if(context) evidence.push({source:"context",label:`${context.name}: janela de saída próxima da sua chegada`});
   const confidence=s.p===null?Math.min(.45,Number(p.source_confidence||.5)) :
     Math.min(.95,(s.reliability==="high"?.9:s.reliability==="medium"?.75:.6)*(context?1:.92));
   return {
     poi_id:p.id,name:p.canonical_name,poi_type:p.poi_type,lat:Number(p.lat),lng:Number(p.lng),
     distance_km:Math.round(p.distance_km*10)/10,geo_cell:p.geo_cell,eta:eta.toISOString(),
     probability_pct:s.p,confidence:Math.round(confidence*100)/100,potential:level(s.p,s.samples),evidence,
     context:context?{id:context.id,name:context.name,type:context.event_type,egress_start_at:context.egress_start_at,egress_end_at:context.egress_end_at,confidence:Number(context.confidence||0)}:null
   };
 });
 return out.sort((a,b)=>{
   const ap=a.probability_pct??-1,bp=b.probability_pct??-1;
   if(ap!==bp)return bp-ap;
   if(Boolean(a.context)!==Boolean(b.context)) return a.context?-1:1;
   return a.distance_km-b.distance_km;
 }).slice(0,input.limit||6);
}
