import { adminSupabase } from "./supabase";
import { geoCellV1, normalizePoiTextV1 } from "./radar-poi-resolver-v1";

/** Privileged ingestion only. Permanent POIs do not get invented demand or event times. */
const MAX_BATCH=50;
function text(value:unknown,max:number):string|null {
  const s=String(value??"").trim();return s?s.slice(0,max):null;
}
function code(value:unknown,label:string):string {
  const s=text(value,100)?.toLowerCase();
  if(!s||!/^[a-z0-9][a-z0-9_-]{1,99}$/.test(s))throw Error(label+"_invalid");
  return s;
}
function coord(value:unknown,min:number,max:number,label:string):number {
  if(value===null||value===undefined||value==="")throw Error(label+"_required");
  const n=Number(value);
  if(!Number.isFinite(n)||n<min||n>max)throw Error(label+"_invalid");
  return n;
}
function url(value:unknown):string|null {
  const s=text(value,1200);if(!s)return null;
  try {
    const u=new URL(s);
    if(!["https:","http:"].includes(u.protocol)||u.username||u.password)throw Error();
    return u.toString();
  }catch{throw Error("source_url_invalid")}
}
export type PoiAdminInputV1=Record<string,unknown>;

export function normalizeRadarPoiAdminV1(input:PoiAdminInputV1,now:string=new Date().toISOString()) {
  const source=code(input.source,"source");
  const source_external_id=code(input.source_external_id,"source_external_id");
  const canonical_name=text(input.canonical_name,240);
  if(!canonical_name)throw Error("canonical_name_required");
  const normalized_name=normalizePoiTextV1(canonical_name);
  if(!normalized_name)throw Error("normalized_name_required");
  const poi_type=code(input.poi_type,"poi_type");
  const lat=coord(input.lat,-90,90,"lat"),lng=coord(input.lng,-180,180,"lng");
  const confidence= input.source_confidence == null ? .5 : Number(input.source_confidence);
  if(!Number.isFinite(confidence)||confidence<0||confidence>1)throw Error("confidence_invalid");
  // Scrapers submit candidates; only a second explicit admin action publishes.
  return {
    source,source_external_id,canonical_name,normalized_name,poi_type,lat,lng,
    geo_cell:geoCellV1(lat,lng),
    address:text(input.address,500),city:text(input.city,120),state:text(input.state,80),
    country_code:"BR",source_url:url(input.source_url),source_confidence:confidence,
    status:"inactive" as const,updated_at:now,
    metadata:{ingest_kind:input.ingest_kind==="manual"?"manual":"external",review:"pending"},
  };
}

export async function listRadarPoiAdminV1(limit=100,status?:string) {
  const count=Math.max(1,Math.min(Number(limit)||100,200));
  const query=adminSupabase().from("sr_radar_pois").select(
    "id,canonical_name,poi_type,city,state,lat,lng,source,source_external_id,source_url,source_confidence,status,last_verified_at,updated_at"
  ).order("updated_at",{ascending:false}).limit(count);
  const {data,error}=await (["active","inactive"].includes(status??"")?query.eq("status",status!):query);
  if(error)throw Error(error.message);
  return data??[];
}

export async function ingestRadarPoisAdminV1(input:unknown) {
  const payload=input && typeof input==="object"&&!Array.isArray(input) ? input as Record<string,unknown> : {};
  const records=Array.isArray(payload.pois)?payload.pois:Array.isArray(input)?input:[];
  if(records.length<1||records.length>MAX_BATCH)throw Error("pois_count_invalid");
  const now=new Date().toISOString();
  const rows=records.map((r:unknown)=>{
    if(!r||typeof r!=="object"||Array.isArray(r))throw Error("poi_invalid");
    return normalizeRadarPoiAdminV1(r as PoiAdminInputV1,now);
  });
  const keys=new Set<string>();
  for(const r of rows){const k=r.source+"|"+r.source_external_id;if(keys.has(k))throw Error("duplicate_source_external_id_in_batch");keys.add(k)}
  if(payload.dry_run===true)return {dry_run:true,items:rows,saved:0};

  let created=0,updated=0;
  // The live DB has a PARTIAL unique index on (source,source_external_id).
  // PostgREST upsert onConflict cannot target that index. Do not upsert blindly.
  for(const row of rows) {
    const db=adminSupabase().from("sr_radar_pois");
    const found=await db.select("id,status").eq("source",row.source)
      .eq("source_external_id",row.source_external_id).limit(1).maybeSingle();
    if(found.error)throw Error(found.error.message);
    if(found.data){
      // Never let a later scraper snapshot silently replace human-approved
      // names, locations or confidence. An admin can deactivate, reimport
      // and explicitly approve a correction after reviewing the evidence.
      if(found.data.status==="active")continue;
      const changed=await adminSupabase().from("sr_radar_pois")
        .update(row).eq("id",found.data.id);
      if(changed.error)throw Error(changed.error.message);
      updated++;
    }else{
      const inserted=await adminSupabase().from("sr_radar_pois").insert(row);
      if(inserted.error)throw Error(inserted.error.message);
      created++;
    }
  }
  return {dry_run:false,created,updated,saved:created+updated,pending_review:created};
}

/** Explicit privileged review step; inactive candidates are never user-visible. */
export async function reviewRadarPoiAdminV1(id:string,status:"active"|"inactive") {
  if(!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id))throw Error("poi_id_invalid");
  const {data,error}=await adminSupabase().from("sr_radar_pois")
    .update({status,updated_at:new Date().toISOString(),last_verified_at:new Date().toISOString()})
    .eq("id",id).select("id,status,updated_at").maybeSingle();
  if(error)throw Error(error.message);
  if(!data)throw Error("poi_not_found");
  return data;
}
