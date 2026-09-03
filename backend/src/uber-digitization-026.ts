import { adminSupabase } from "./supabase";

function num(value: unknown) { const n=Number(value); return Number.isFinite(n)?n:null; }
function iso(value: unknown) { if(value===null||value===undefined||String(value).trim()==="")return null; const d=new Date(String(value)); return Number.isFinite(d.getTime())?d.toISOString():null; }
function text(value: unknown, max=180){const s=String(value??"").trim();return s?s.slice(0,max):null;}

async function matchOffer(driverId:string, ride:any){
  const at=iso(ride.occurred_at); const fare=num(ride.fare); if(!at||fare===null)return null;
  const center=new Date(at).getTime(); const from=new Date(center-45*60000).toISOString(); const to=new Date(center+45*60000).toISOString();
  let q=adminSupabase().from("ride_offers").select("id,journey_id,local_offer_id,service_type,observed_at,fare").eq("driver_id",driverId).gte("observed_at",from).lte("observed_at",to).gte("fare",fare-0.011).lte("fare",fare+0.011);
  const service=text(ride.service_type,30); if(service&&service!=="unknown")q=q.eq("service_type",service);
  const {data,error}=await q.limit(3); if(error)throw new Error(error.message); const rows=data??[]; return rows.length===1?rows[0]:null;
}

export async function saveSessionImport(driverId:string,input:Record<string,unknown>){
  const sourceKey=text(input.source_key,120); if(!sourceKey)throw new Error("source_key_required");
  const row={driver_id:driverId,source_key:sourceKey,captured_at:iso(input.captured_at)??new Date().toISOString(),started_at:iso(input.started_at),ended_at:iso(input.ended_at),earnings:num(input.earnings),completed_trips:num(input.completed_trips),offered_trips:num(input.offered_trips),confidence:Math.max(0,Math.min(1,num(input.confidence)??0))};
  const {data,error}=await adminSupabase().from("uber_session_imports").upsert(row,{onConflict:"driver_id,source_key"}).select("*").single(); if(error)throw new Error(error.message); return data;
}

export async function saveCompletedRides(driverId:string,deviceId:string,input:Record<string,unknown>){
  const rides=Array.isArray(input.rides)?input.rides:[]; if(!rides.length)throw new Error("rides_required"); const saved:any[]=[];
  for(const raw of rides.slice(0,30)){ const ride=(raw&&typeof raw==="object"?raw:{}) as Record<string,unknown>; const sourceKey=text(ride.source_key,120); const fare=num(ride.fare); if(!sourceKey||fare===null||fare<=0)continue; const match=await matchOffer(driverId,ride);
    const row={driver_id:driverId,device_id:deviceId,source_key:sourceKey,captured_at:iso(ride.captured_at)??new Date().toISOString(),occurred_at:iso(ride.occurred_at),fare,service_type:text(ride.service_type,40)??"unknown",pickup_label:text(ride.pickup_label),destination_label:text(ride.destination_label),confidence:Math.max(0,Math.min(1,num(ride.confidence)??0)),matched_ride_offer_id:match?.id??null};
    const up=await adminSupabase().from("uber_completed_ride_imports").upsert(row,{onConflict:"driver_id,source_key"}).select("*").single(); if(up.error)throw new Error(up.error.message); saved.push(up.data);
    if(match?.journey_id&&match?.local_offer_id){
      const current=await adminSupabase().from("ride_outcomes").select("revision").eq("driver_id",driverId).eq("local_offer_id",match.local_offer_id).maybeSingle();
      if(current.error)throw new Error(current.error.message);
      const revision=Math.max(1,Number(current.data?.revision??0)+1);
      const outcome={driver_id:driverId,device_id:deviceId,journey_id:match.journey_id,ride_offer_id:match.id,local_offer_id:match.local_offer_id,status:"COMPLETED",completed_at:row.occurred_at??row.captured_at,corrected_at:new Date().toISOString(),source:"uber_history_ocr",revision,updated_at:new Date().toISOString()};
      const out=await adminSupabase().from("ride_outcomes").upsert(outcome,{onConflict:"driver_id,local_offer_id"}); if(out.error)throw new Error(out.error.message);
    }
  }
  return saved;
}
