import { adminSupabase } from "./supabase";
const TYPES=new Set(["ride_radar_armed","radar_opened","opportunity_viewed","navigation_opened","assistant_eligible","assistant_shown","assistant_ignored","assistant_viewed"]);
function text(v:any,max=160){const s=String(v??"").trim();return s?s.slice(0,max):null}
function number(v:any,min:number,max:number){if(v===null||v===undefined||v==="")return null;const n=Number(v);return Number.isFinite(n)?Math.max(min,Math.min(max,n)):null}
function safeMetadata(v:any){
 const allowed=new Set(["surface","reason","source","mode","variant"]);
 if(!v||typeof v!=="object"||Array.isArray(v)) return {};
 const out:Record<string,string|number|boolean>={};
 for(const [k,value] of Object.entries(v)){
  if(!allowed.has(k)) continue;
  if(typeof value==="string") out[k]=value.slice(0,120);
  else if(typeof value==="number"&&Number.isFinite(value)) out[k]=value;
  else if(typeof value==="boolean") out[k]=value;
 }
 return out;
}
export async function saveRadarContextualEventV1(driverId:string,deviceId:string|null|undefined,input:any){
 const t=text(input?.event_type,40); if(!t||!TYPES.has(t)) throw new Error("event_type_invalid");
 const id=String(input?.client_event_id??""); if(!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("client_event_id_invalid");
 const when=new Date(String(input?.occurred_at??"")); if(Number.isNaN(when.getTime())) throw new Error("occurred_at_invalid");
 const row={
  client_event_id:id,driver_id:driverId,device_id:deviceId??null,
  journey_id:text(input?.journey_id,80),ride_local_offer_id:text(input?.ride_local_offer_id,160),
  event_type:t,occurred_at:when.toISOString(),opportunity_id:text(input?.opportunity_id,180),
  poi_id:text(input?.poi_id,80),ranking_score:number(input?.ranking_score,0,100),
  continuity_probability_pct:number(input?.continuity_probability_pct,0,100),
  baseline_probability_pct:number(input?.baseline_probability_pct,0,100),
  distance_km:number(input?.distance_km,0,50),
  metadata:safeMetadata(input?.metadata)
 };
 const q=await adminSupabase().from("sr_radar_contextual_events")
  .upsert(row,{onConflict:"driver_id,client_event_id",ignoreDuplicates:true})
  .select("id,event_type,occurred_at").maybeSingle();
 if(q.error) throw new Error(q.error.message);
 return q.data??{duplicate:true};
}
