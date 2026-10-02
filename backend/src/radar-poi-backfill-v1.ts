import { adminSupabase } from "./supabase";
import { geoCellV1, normalizePoiTextV1 } from "./radar-poi-resolver-v1";

/**
 * Importador controlado para POIs oficiais/estáticos (GeoSampa, CNES, operadores
 * de mobilidade etc). Não é scraper. Recebe dados já normalizados pelo Collector.
 */
export type StaticPoiInputV1 = {
 source:string; external_id:string; name:string; poi_type:string;
 address?:string|null; city?:string|null; state?:string|null; country_code?:string|null;
 lat:number; lng:number; source_url?:string|null; confidence?:number; aliases?:string[];
 region_key?:string|null; region_label?:string|null; metadata?:Record<string,unknown>;
};

export async function upsertStaticPoisV1(rows:StaticPoiInputV1[]) {
 if(!Array.isArray(rows)||!rows.length) throw new Error("pois_required");
 if(rows.length>1000) throw new Error("too_many_pois");
 const now=new Date().toISOString();
 const values=rows.map(r=>{
   if(!r.source?.trim()||!r.external_id?.trim()||!r.name?.trim()) throw new Error("poi_identity_required");
   if(!Number.isFinite(r.lat)||!Number.isFinite(r.lng)||r.lat < -90||r.lat > 90||r.lng < -180||r.lng > 180) throw new Error("poi_location_invalid");
   return {
    canonical_name:r.name.trim().slice(0,240),
    normalized_name:normalizePoiTextV1(r.name).slice(0,240),
    poi_type:r.poi_type?.trim()||"place",
    address:r.address?.slice(0,500)??null,city:r.city?.slice(0,120)??null,state:r.state?.slice(0,80)??null,
    country_code:(r.country_code||"BR").slice(0,3).toUpperCase(),
    lat:r.lat,lng:r.lng,geo_cell:geoCellV1(r.lat,r.lng),region_key:r.region_key??null,region_label:r.region_label??null,
    source:r.source.trim().toLowerCase(),source_external_id:r.external_id.trim(),source_url:r.source_url??null,
    source_confidence:Math.max(0,Math.min(1,r.confidence??.8)),status:"active",
    aliases:[...new Set([r.name,...(r.aliases??[])])],metadata:r.metadata??{},last_verified_at:now,updated_at:now
   };
 });
 const q=await adminSupabase().from("sr_radar_pois").upsert(values,{onConflict:"source,source_external_id"}).select("id,source,source_external_id,canonical_name");
 if(q.error) throw new Error(q.error.message);
 const map=new Map((q.data??[]).map((p:any)=>[`${p.source}|${p.source_external_id}`,p.id]));
 const aliases:any[]=[];
 rows.forEach(r=>{
   const id=map.get(`${r.source.trim().toLowerCase()}|${r.external_id.trim()}`); if(!id)return;
   for(const alias of [...new Set([r.name,...(r.aliases??[])])]) aliases.push({poi_id:id,alias,normalized_alias:normalizePoiTextV1(alias),source:r.source});
 });
 if(aliases.length) {
   const a=await adminSupabase().from("sr_radar_poi_aliases").upsert(aliases,{onConflict:"poi_id,normalized_alias"});
   if(a.error) throw new Error(a.error.message);
 }
 return {received:rows.length,saved:q.data?.length??values.length};
}
