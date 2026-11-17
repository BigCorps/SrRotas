/**
 * Read-only classification for the observed radar.admin-import.v2 envelope.
 * "ready" here means structurally eligible for PRIVATE human review, not
 * approved/licensed/published. Every write is rechecked in PostgreSQL.
 */
import { nameSimilarityV1, normalizePoiTextV1 } from "../radar-poi-resolver-v1";
import {
  digest, ImportError, type Snapshot, type ItemResult, type Preview,
} from "./validation";

export const EXTERNAL_V2_SCHEMA = "radar.admin-import.v2";
export const V2_POI_TYPES = new Set([
  "music","sports","theatre","fair_convention","cultural",
  "mall","hospital","hotel","airport","bus_terminal","mobility_hub",
]);
const UF = new Set(
  "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" "),
);
type Dict = Record<string, unknown>;
const obj = (v: unknown): Dict | null =>
  !!v && typeof v === "object" && !Array.isArray(v) ? v as Dict : null;
const str = (v: unknown) => typeof v === "string" ? v.trim().normalize("NFC") : "";
const reason = (code: string, message: string, field?: string) =>
  ({ code, message, ...(field ? { field } : {}) });
const validCoords = (lat: unknown,lng: unknown): lat is number =>
  typeof lat === "number" && typeof lng === "number" &&
  Number.isFinite(lat) && Number.isFinite(lng) &&
  lat >= -34 && lat <= 5.5 && lng >= -74.5 && lng <= -28.5;
const dist = (a: {lat:number;lng:number}, b:{lat:number;lng:number}) => {
  const r = Math.PI/180;
  const h = Math.sin((a.lat-b.lat)*r/2)**2 +
    Math.cos(a.lat*r)*Math.cos(b.lat*r)*Math.sin((a.lng-b.lng)*r/2)**2;
  return 12742000*Math.asin(Math.sqrt(Math.min(1,h)));
};
const isHttps = (value: unknown) => {
  try {
    const u = new URL(str(value));
    return u.protocol === "https:" && !u.username && !u.password &&
      !u.hash && u.hostname.includes(".") &&
      !/^(?:\d+\.){3}\d+$/.test(u.hostname) &&
      !/(^|\.)(localhost|local|internal)$/.test(u.hostname);
  } catch { return false; }
};
const identity = (p: Dict) => {
  const ref = obj(p.origin_ref);
  return ref && str(ref.source) && str(ref.external_id)
    ? [str(ref.source),str(ref.external_id)]
    : null;
};

export function previewExternalV2(input: unknown, snapshot: Snapshot): Preview {
  const env = obj(input);
  if (!env || env.schema_version !== EXTERNAL_V2_SCHEMA ||
      !Array.isArray(env.events) || !Array.isArray(env.pois) ||
      !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(str(env.batch_id)) ||
      Object.keys(env).some(k=>!["schema_version","batch_id","events","pois"].includes(k)) ||
      env.events.length+env.pois.length<1 || env.events.length+env.pois.length>200)
    throw new ImportError("INVALID_ENVELOPE");

  const records = [
    ...env.events.map((p:unknown,n:number)=>({kind:"event" as const,p,n})),
    ...env.pois.map((p:unknown,n:number)=>({kind:"poi" as const,p,n})),
  ];
  const rows: ItemResult[] = records.map(({kind,p:raw,n})=>{
    const p = obj(raw);
    const ref = str(p?.client_ref);
    const name = str(kind==="poi"?p?.canonical_name:(p?.name??p?.event_name));
    const category = str(kind==="poi"?p?.poi_type:p?.event_type);
    const address = str(p?.address),city=str(p?.city),state=str(p?.state);
    const row: ItemResult = {
      item_id:ref || `invalid-${kind}-${n+1}`,kind,classification:"ready",
      reasons:[],related_ids:[],candidates:[],
      display_name:name||"Nome ausente",
      display_type:category||"Tipo ausente",
      display_location:[address,city,state].filter(Boolean).join(" · "),
    };
    const invalid=(code:string,msg:string,field?:string)=>{
      row.classification="invalid";row.reasons.push(reason(code,msg,field));
    };
    const pending=(code:string,msg:string,field?:string)=>{
      if(row.classification==="ready")row.classification="pending";
      row.reasons.push(reason(code,msg,field));
    };
    if (!p) { invalid("INVALID_RECORD","Registro não é um objeto.");return row; }
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(ref))
      invalid("INVALID_CLIENT_REF","client_ref ausente ou inválido.","client_ref");
    if (name.length<3 || name.length>200)
      invalid("INVALID_NAME","Nome ausente ou fora do limite.","canonical_name");
    if (!city || city.length>100 || !UF.has(state))
      invalid("INVALID_LOCATION","Cidade ou UF inválida.","city/state");
    if(kind==="event") {
      pending("EVENT_V2_NOT_MAPPED","Evento precisa de mapeamento temporal específico antes de importar.");
      return row;
    }
    if(!category || !/^[a-z][a-z0-9_]{1,63}$/.test(category))
      invalid("INVALID_TYPE","Categoria inválida.","poi_type");
    else if(!V2_POI_TYPES.has(category))
      pending("CATEGORY_MAPPING_REQUIRED","Categoria sem mapeamento aprovado.","poi_type");
    if(!address)
      pending("ADDRESS_REQUIRED","Endereço/entrada precisa ser confirmado.","address");
    else if(address.length>500)
      invalid("ADDRESS_TOO_LONG","Endereço acima do limite.","address");
    const {lat,lng} = p;
    if(lat==null && lng==null)pending("PIN_REQUIRED","Pino da localização não informado.","lat/lng");
    else if(!validCoords(lat,lng))
      invalid("INVALID_COORDINATES","Latitude/longitude inválidas ou incompletas.","lat/lng");
    if(!Array.isArray(p.evidence) || p.evidence.length===0)
      pending("EVIDENCE_REQUIRED","Sem fonte para revisão.","evidence");
    else if(p.evidence.length>30)
      invalid("TOO_MANY_EVIDENCE","Mais de 30 evidências.","evidence");
    else if(p.evidence.some(e=>!obj(e) || !isHttps(obj(e)?.url)))
      pending("EVIDENCE_URL_REVIEW","Referência HTTPS precisa ser conferida.","evidence");

    // v2 evidence is not a rights attestation. Human approval is mandatory
    // AFTER import; this warning does not block entry into private review.
    row.reasons.push(reason("REVIEW_BEFORE_PUBLICATION",
      "Fonte, licença, endereço e pino devem ser confirmados pelo revisor antes da publicação."));
    if(!snapshot.complete)
      pending("DEDUP_SNAPSHOT_INCOMPLETE","Consulta de duplicidades incompleta.");

    if(validCoords(lat,lng)){
      const point={lat:lat as number,lng:lng as number};
      for(const e of snapshot.existing){
        if(e.kind!=="poi" || !Number.isFinite(e.lat) || !Number.isFinite(e.lng))continue;
        const distance=dist(point, {lat:e.lat,lng:e.lng});
        const nameSim=nameSimilarityV1(name,e.name);
        const addrSim=nameSimilarityV1(address,e.address??"");
        const alias=identity(p);
        const sameOrigin=!!alias && e.source===alias[0] && e.external_id===alias[1];
        const exact=(sameOrigin && nameSim>=0.9) ||
          (distance<=20 && normalizePoiTextV1(name)===normalizePoiTextV1(e.name) &&
           !!address && normalizePoiTextV1(address)===normalizePoiTextV1(e.address??""));
        const suspect=sameOrigin || (distance<=150 && nameSim>=0.60 &&
          (addrSim>=0.45 || nameSim>=0.85));
        if(!exact && !suspect)continue;
        row.candidates!.push({
          id:e.id,source:e.source,name:e.name,address:e.address??null,
          lat:e.lat,lng:e.lng,kind:"poi",match:exact?"exact":"possible",
          reason:exact?"Mesma identidade e localização confirmadas":
            `Possível mesmo local: nome compatível e distância de ${Math.round(distance)} m`,
        });
        row.related_ids.push(e.id);
        if(exact) {
          if(row.classification!=="invalid")row.classification="duplicate";
          row.reasons.push(reason("CONFIRMED_CROSS_SOURCE_DUPLICATE",
            `Local já registrado em ${e.source}; não incluir outro.`));
        }else if(row.classification!=="duplicate")
          pending("POSSIBLE_CROSS_SOURCE_DUPLICATE",
            `Possível correspondência com registro ${e.id}; operador deve decidir.`);
      }
    }
    return row;
  });
  for(let i=0;i<records.length;i++){
    const a=obj(records[i].p),row=rows[i];
    if(!a || row.classification==="invalid")continue;
    const repeats=records
      .map((r,j)=>({r,j,p:obj(r.p)}))
      .filter(({r,p,j})=>j!==i && r.kind===records[i].kind && p &&
        str(p.client_ref)===str(a.client_ref));
    if(repeats.length){
      row.classification="invalid";
      row.reasons.push(reason("DUPLICATE_CLIENT_REF","client_ref repetido no lote."));
    }
    if(records[i].kind!=="poi" || !validCoords(a.lat,a.lng))continue;
    for(let j=0;j<records.length;j++){
      if(j===i || records[j].kind!=="poi")continue;
      const other=obj(records[j].p);
      if(!other || !validCoords(other.lat,other.lng))continue;
      const distance=dist({lat:a.lat as number,lng:a.lng as number},
        {lat:other.lat as number,lng:other.lng as number});
      const sameName=normalizePoiTextV1(a.canonical_name)===
        normalizePoiTextV1(other.canonical_name);
      const exact=distance<=20 && sameName && normalizePoiTextV1(a.address)===
        normalizePoiTextV1(other.address);
      const suspect=exact || (distance<=150 &&
        nameSimilarityV1(a.canonical_name,other.canonical_name)>=0.6);
      if(!suspect)continue;
      row.candidates!.push({
        id:str(other.client_ref),kind:"file",source:"Mesmo arquivo",
        name:str(other.canonical_name),address:str(other.address),
        lat:other.lat as number,lng:other.lng as number,match:exact?"exact":"possible",
        reason:exact?"Registros idênticos no lote":"Possível repetição dentro do lote",
      });
      if(row.classification!=="invalid"){
        if(exact)row.classification="duplicate";
        else if(row.classification==="ready")row.classification="pending";
      }
      row.reasons.push(reason(exact?"CONFIRMED_IN_FILE_DUPLICATE":"POSSIBLE_IN_FILE_DUPLICATE",
        "Comparar também com o registro do próprio arquivo."));
    }
  }
  const counts=Object.fromEntries(["ready","duplicate","pending","invalid"].map(k=>[
    k,rows.filter(r=>r.classification===k).length,
  ]));
  return {
    schema:EXTERNAL_V2_SCHEMA,input_schema:EXTERNAL_V2_SCHEMA,
    original_batch_id:str(env.batch_id),items:rows,counts,
    fingerprint:digest(snapshot),envelope_hash:digest(input),
    commit_enabled:false,block_code:"DEDUP_COORDINATION_UNAVAILABLE",
  };
}
