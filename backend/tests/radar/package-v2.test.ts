import {test} from "node:test";
import assert from "node:assert/strict";
import {deflateRawSync} from "node:zlib";
import {inspectRadarPackageV2} from "../../src/radar-admin-import/package-v2";
import {previewExternalV2} from "../../src/radar-admin-import/external-v2-preview";
import type {Snapshot} from "../../src/radar-admin-import/validation";
const snapshot:Snapshot={registry:[],registryAvailable:false,existing:[],complete:true};
const REGIONS=["centro","norte","noroeste","leste","sudeste","sul","sudoeste","oeste"];
const SIZES=[33,33,23,84,15,65,15,96];
const label=(s:string)=>s.charAt(0).toUpperCase()+s.slice(1);
function crc32(buf:Buffer) {
  let c=0xffffffff;
  for(const b of buf){c^=b;for(let i=0;i<8;i++)c=(c>>>1)^(0xedb88320&-(c&1));}
  return(c^0xffffffff)>>>0;
}
function archive(files:Array<[string,Buffer]>){
  const locals:Buffer[]=[], centrals:Buffer[]=[];let offset=0;
  for(const [name,body] of files) {
    const filename=Buffer.from(name),zip=deflateRawSync(body),crc=crc32(body);
    const local=Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50,0);local.writeUInt16LE(20,4);
    local.writeUInt16LE(8,8);local.writeUInt32LE(crc,14);
    local.writeUInt32LE(zip.length,18);local.writeUInt32LE(body.length,22);
    local.writeUInt16LE(filename.length,26);
    locals.push(local,filename,zip);
    const central=Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50,0);central.writeUInt16LE(20,4);
    central.writeUInt16LE(20,6);central.writeUInt16LE(8,10);
    central.writeUInt32LE(crc,16);central.writeUInt32LE(zip.length,20);
    central.writeUInt32LE(body.length,24);central.writeUInt16LE(filename.length,28);
    central.writeUInt32LE(offset,42);
    centrals.push(central,filename);offset+=local.length+filename.length+zip.length;
  }
  const cd=Buffer.concat(centrals),end=Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50,0);end.writeUInt16LE(files.length,8);
  end.writeUInt16LE(files.length,10);end.writeUInt32LE(cd.length,12);
  end.writeUInt32LE(offset,16);
  return Buffer.concat([...locals,cd,end]);
}
const poi=(id:string,n:number)=>({
  client_ref:id,canonical_name:"Terminal Exemplo "+id,poi_type:"mobility_hub",
  city:"São Paulo",state:"SP",address:"Rua "+id+", 20",
  lat:-23.5-n*0.00001,lng:-46.6-n*0.00001,
  evidence:[{kind:"web",url:"https://example.org/local"}]
});
function fixture(){
  const files:Array<[string,Buffer]>=[];
  const features:unknown[]=[];let number=0;
  for(let i=0;i<REGIONS.length;i++){
    const points=[];
    for(let j=0;j<SIZES[i];j++){
      const id="sp-poi-"+String(++number).padStart(4,"0");
      const p=poi(id,number);points.push(p);
      if(number<=192)features.push({
        type:"Feature",geometry:{type:"Point",coordinates:[p.lng,p.lat]},
        properties:{id,nome:p.canonical_name,regiao:label(REGIONS[i])}
      });
    }
    files.push(["lotes/0"+(i+1)+"_"+REGIONS[i]+".poi.v2.json",
      Buffer.from(JSON.stringify({schema_version:"radar.admin-import.v2",
        batch_id:"00000000-0000-4000-8000-000000000001",events:[],pois:points}))]);
  }
  files.unshift(["POIs_SP_com_coordenadas.geojson",Buffer.from(JSON.stringify({
    type:"FeatureCollection",features
  }))]);
  files.push(["Catalogo_POIs_SP.csv",Buffer.from("id;nome\n")]);
  return archive(files);
}
test("ZIP SP regional: 364 identities, 192 GeoJSON linked, eight regions and stable id",()=>{
  const raw=fixture(),one=inspectRadarPackageV2(raw,"Radar_Sao_Paulo.zip");
  const two=inspectRadarPackageV2(raw,"Radar_Sao_Paulo.zip");
  const p=one.envelope.pois as Record<string,unknown>[];
  assert.equal(p.length,364);assert.equal(new Set(p.map(x=>x.client_ref)).size,364);
  assert.equal(one.manifest.geojson_features,192);
  assert.equal(one.manifest.geojson_matched,192);
  assert.equal(one.manifest.regions.length,8);
  assert.equal(one.manifest.excluded.length,0);
  assert.equal(one.envelope.batch_id,two.envelope.batch_id);
  assert.equal(one.manifest.auxiliary_files.length,0);
  const view=previewExternalV2(one.envelope,snapshot);
  assert.equal(view.items.length,364);
  assert.equal(view.items.filter(p=>p.classification==="duplicate").length,0);
  assert.equal(view.items.filter(p=>p.region==="Centro").length,33);
});
test("JSON v2 avulso mantém o mesmo batch_id sem criar um registro GeoJSON",()=>{
  const bytes=Buffer.from(JSON.stringify({
    schema_version:"radar.admin-import.v2",
    batch_id:"00000000-0000-4000-8000-000000000005",pois:[poi("p-1",1)],events:[]
  }));
  const r=inspectRadarPackageV2(bytes,"05_sudeste.poi.v2.json");
  assert.equal(r.envelope.batch_id,"00000000-0000-4000-8000-000000000005");
  assert.equal(r.manifest.regions[0],"Sudeste");
  assert.equal(r.manifest.geojson_features,0);
});
test("ZIP rejeita entrada traversal e cada arquivo só aparece uma vez",()=>{
  const zip=archive([["../lotes/01_centro.poi.v2.json",Buffer.from("{}")]]);
  assert.throws(()=>inspectRadarPackageV2(zip,"bad.zip"),/UNSAFE_ZIP_ENTRY/);
});
