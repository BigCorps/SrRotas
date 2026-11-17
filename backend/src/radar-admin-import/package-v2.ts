/** Bounded, fail-closed parser for Regional Radar POI v2 archives.
 * No archive contents are ever executed, downloaded, or stored in a public bucket.
 * One matching GeoJSON feature enriches the corresponding JSON POI; never another row.
 */
import { createHash } from "node:crypto";
import { inflateRawSync } from "node:zlib";
import { ImportError, parseStrict, MAX_BYTES } from "./validation";

type Obj=Record<string,unknown>;
const obj=(value:unknown):Obj|null=>value!==null&&typeof value==="object"&&!Array.isArray(value)?value as Obj:null;
const str=(v:unknown)=>typeof v==="string"?v.trim():"";
export type PackageExclusion={region:string;name:string;client_ref:string;reason:string;source_file:string};
export type RadarPackageManifest={
  regions:string[];files:string[];source_kind:"json"|"zip";geojson_features:number;
  geojson_matched:number;excluded:PackageExclusion[];auxiliary_files:string[];
};
export type RadarPackage={envelope:Obj;manifest:RadarPackageManifest;canonical:Uint8Array};
const REGIONS:Record<string,string>={
  centro:"Centro",norte:"Norte",noroeste:"Noroeste",leste:"Leste",
  sudeste:"Sudeste",sul:"Sul",sudoeste:"Sudoeste",oeste:"Oeste"
};
const REGION_ORDER=Object.values(REGIONS);
const MAX_ENTRIES=64, MAX_EXPANDED=6_000_000,MAX_ITEMS=500;
const regionFromFile=(name:string):string=>{
  const match=/(?:^|\/)(?:\d{1,2}_)?(centro|norte|noroeste|leste|sudeste|sul|sudoeste|oeste)\.poi\.v2\.json$/i.exec(name);
  return match?REGIONS[match[1].toLowerCase()]:"Sem região";
};
const regionOf=(p:Obj,fallback:string)=>{
  const named=str(p.region||p.regiao||p.region_label);
  const known=REGION_ORDER.find(r=>r.toLowerCase()===named.toLowerCase());
  return known??fallback;
};
function shaUuid(bytes:Uint8Array){
  const hash=createHash("sha256").update(bytes).digest();
  const value=Buffer.from(hash.subarray(0,16));
  value[6]=(value[6]&15)|0x50;value[8]=(value[8]&63)|0x80;
  const h=value.toString("hex");
  return [h.slice(0,8),h.slice(8,12),h.slice(12,16),h.slice(16,20),h.slice(20,32)].join("-");
}
function checksum(bytes:Uint8Array){
  let c=0xffffffff;
  for(const b of bytes){c^=b;for(let k=0;k<8;k++)c=(c>>>1)^(0xedb88320&-(c&1));}
  return (c^0xffffffff)>>>0;
}
function zipEntries(bytes:Uint8Array){
  if(bytes.byteLength>MAX_BYTES)throw new ImportError("PAYLOAD_TOO_LARGE",413);
  const b=Buffer.from(bytes),read=(off:number)=>b.readUInt32LE(off);
  let end=-1;
  for(let at=b.length-22;at>=Math.max(0,b.length-65557);at--){
    if(read(at)===0x06054b50 && at+22+b.readUInt16LE(at+20)===b.length){end=at;break;}
  }
  if(end<0||b.readUInt16LE(end+4)||b.readUInt16LE(end+6))throw new ImportError("INVALID_ZIP");
  const entries=b.readUInt16LE(end+10),count=b.readUInt16LE(end+8);
  const directorySize=read(end+12),directoryOffset=read(end+16);
  if(entries!==count||entries>MAX_ENTRIES||entries===0||
     directoryOffset===0xffffffff||directoryOffset+directorySize>end)
    throw new ImportError("ZIP_LIMIT_OR_CENTRAL_DIRECTORY");
  const result:Array<{name:string;content:Uint8Array}>=[];
  const seen=new Set<string>();let offset=directoryOffset,total=0;
  for(let i=0;i<entries;i++){
    if(offset+46>end||read(offset)!==0x02014b50)throw new ImportError("INVALID_ZIP");
    const flags=b.readUInt16LE(offset+8),method=b.readUInt16LE(offset+10);
    const crc=read(offset+16),compressed=read(offset+20),uncompressed=read(offset+24);
    const nameLen=b.readUInt16LE(offset+28),extra=b.readUInt16LE(offset+30),comment=b.readUInt16LE(offset+32);
    const external=read(offset+38),local=read(offset+42);
    if(offset+46+nameLen+extra+comment>end||!nameLen||nameLen>180||
       flags&1||flags&0x40||compressed===0xffffffff||uncompressed===0xffffffff||
       local===0xffffffff||uncompressed>MAX_BYTES)
      throw new ImportError("UNSAFE_ZIP_ENTRY");
    const name=new TextDecoder("utf-8",{fatal:true}).decode(b.subarray(offset+46,offset+46+nameLen));
    offset+=46+nameLen+extra+comment;
    if(name.includes("\\")||name.startsWith("/")||name.startsWith(".")||
       name.split("/").some(s=>s===".."||s===".")||/\0|[\u0000-\u001f]/.test(name)||
       (external>>>16)&0xf000 && ((external>>>16)&0xf000)!==0x8000 &&
         ((external>>>16)&0xf000)!==0x4000||
       seen.has(name.toLowerCase()))throw new ImportError("UNSAFE_ZIP_ENTRY");
    seen.add(name.toLowerCase());
    if(name.endsWith("/"))continue;
    if(!/\.(json|geojson)$/i.test(name))continue;
    if(local+30>b.length||read(local)!==0x04034b50)throw new ImportError("INVALID_ZIP");
    const headLen=b.readUInt16LE(local+26),headExtra=b.readUInt16LE(local+28);
    const dataOffset=local+30+headLen+headExtra;
    if(dataOffset+compressed>directoryOffset)throw new ImportError("INVALID_ZIP");
    const raw=b.subarray(dataOffset,dataOffset+compressed);
    if(method!==0&&method!==8)throw new ImportError("UNSUPPORTED_ZIP_COMPRESSION");
    total+=uncompressed;
    if(total>MAX_EXPANDED||compressed===0&&uncompressed>0||
       compressed>0&&uncompressed/compressed>150)
      throw new ImportError("ZIP_EXPANSION_LIMIT",413);
    let content:Buffer;
    try{
      content=method===0?Buffer.from(raw):inflateRawSync(raw,{maxOutputLength:uncompressed+1});
    }catch{throw new ImportError("INVALID_ZIP_COMPRESSION");}
    if(content.byteLength!==uncompressed||checksum(content)!==crc)
      throw new ImportError("ZIP_CHECKSUM_MISMATCH");
    result.push({name,content});
  }
  if(offset!==directoryOffset+directorySize)throw new ImportError("ZIP_DIRECTORY_MISMATCH");
  return result;
}
export function inspectRadarPackageV2(bytes:Uint8Array,filename:string):RadarPackage{
  if(bytes.byteLength>MAX_BYTES)throw new ImportError("PAYLOAD_TOO_LARGE",413);
  const isZip=/\.zip$/i.test(filename),isJson=/\.json$/i.test(filename);
  if(!isZip&&!isJson)throw new ImportError("PACKAGE_EXTENSION_REQUIRED",415);
  const files=isZip?zipEntries(bytes):[{name:filename,content:bytes}];
  const manifest:RadarPackageManifest={
    source_kind:isZip?"zip":"json",regions:[],files:[],geojson_features:0,
    geojson_matched:0,excluded:[],auxiliary_files:[]
  };
  const objects:Array<{p:Obj;sourceFile:string;region:string}>=[];
  const geo=new Map<string,Obj>();
  const payloads:Array<{file:string;body:Obj}>=[];
  for(const file of files){
    const name=file.name;
    if(/\.geojson$/i.test(name)){
      const payload=obj(parseStrict(file.content));
      if(!payload||payload.type!=="FeatureCollection"||!Array.isArray(payload.features)||
         payload.features.length>2500)throw new ImportError("INVALID_GEOJSON");
      for(const raw of payload.features){
        const feature=obj(raw),properties=obj(feature?.properties),geometry=obj(feature?.geometry);
        const id=str(properties?.id);
        if(!id||geometry?.type!=="Point"||!Array.isArray(geometry.coordinates)||
           geometry.coordinates.length<2||!Number.isFinite(geometry.coordinates[0])||
           !Number.isFinite(geometry.coordinates[1]))continue;
        if(geo.has(id))throw new ImportError("DUPLICATE_GEOJSON_ID");
        geo.set(id,feature!);
      }
      manifest.geojson_features+=payload.features.length;continue;
    }
    if(!/\.poi\.v2\.json$/i.test(name) && isZip){
      manifest.auxiliary_files.push(name);continue;
    }
    const env=obj(parseStrict(file.content));
    if(!env||env.schema_version!=="radar.admin-import.v2"||
       !Array.isArray(env.pois)||!Array.isArray(env.events)||
       env.events.length>0||!str(env.batch_id))throw new ImportError("INVALID_POI_V2_ENVELOPE");
    payloads.push({file:name,body:env});
  }
  if(payloads.length===0)throw new ImportError("NO_POI_JSON_IN_PACKAGE");
  if(payloads.length>16)throw new ImportError("TOO_MANY_REGIONS");
  const seen=new Map<string,Obj>();
  for(const {file,body} of payloads){
    manifest.files.push(file);const fallback=regionFromFile(file);
    for(const raw of body.pois as unknown[]){
      const p=obj(raw);
      if(!p)throw new ImportError("INVALID_POI_RECORD");
      const ref=str(p.client_ref);
      if(!ref)throw new ImportError("INVALID_POI_CLIENT_REF");
      const region=regionOf(p,fallback);
      const previous=seen.get(ref);
      if(previous){
        const same=JSON.stringify(previous)===JSON.stringify({...p,region,source_file:file});
        manifest.excluded.push({
          region,name:str(p.canonical_name),client_ref:ref,source_file:file,
          reason:same?"Mesmo client_ref já incluído no pacote":"client_ref repetido; verificar versões divergentes"
        });
        if(!same)previous.package_conflict=true;
        continue;
      }
      const record={...p,region,source_file:file};
      seen.set(ref,record);objects.push({p:record,sourceFile:file,region});
      if(!manifest.regions.includes(region))manifest.regions.push(region);
    }
  }
  if(objects.length>MAX_ITEMS)throw new ImportError("PACKAGE_ITEM_LIMIT",413);
  const matching=new Set<string>();
  for(const {p} of objects){
    const id=str(p.client_ref),feature=geo.get(id);if(!feature)continue;
    matching.add(id);manifest.geojson_matched++;
    const coords=(obj(feature.geometry)?.coordinates as unknown[]|undefined)??[];
    const lon=Number(coords[0]),lat=Number(coords[1]);
    if(!Number.isFinite(lon)||!Number.isFinite(lat))continue;
    if(p.lat===undefined&&p.lng===undefined){
      p.lat=lat;p.lng=lon;
      p.geojson_pin_source="GeoJSON de referência do mesmo client_ref";
    }else if(Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&
      (Math.abs(Number(p.lat)-lat)>0.0005||Math.abs(Number(p.lng)-lon)>0.0005)){
      p.geojson_conflict=true;
    }
  }
  for(const [id,feature] of geo)if(!matching.has(id)){
    const props=obj(feature.properties);
    manifest.excluded.push({
      region:str(props?.regiao)||"Sem região",name:str(props?.nome)||id,
      client_ref:id,source_file:"GeoJSON",
      reason:"Ponto cartográfico não possui POI correspondente no JSON"
    });
  }
  manifest.regions.sort((a,b)=>REGION_ORDER.indexOf(a)-REGION_ORDER.indexOf(b));
  const envelope:Obj={
    schema_version:"radar.admin-import.v2",
    batch_id:isZip?shaUuid(bytes):str(payloads[0].body.batch_id),
    events:[],pois:objects.map(o=>o.p),package:manifest
  };
  const canonical=Buffer.from(JSON.stringify(envelope),"utf8");
  if(canonical.byteLength>MAX_BYTES)throw new ImportError("PACKAGE_NORMALIZED_TOO_LARGE",413);
  return {envelope,manifest,canonical};
}
