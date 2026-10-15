import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { normalizeRadarPoiAdminV1 } from "../../src/radar-poi-admin-v1";

const venue={
  source:"manual", source_external_id:"hospital_centro_001",
  canonical_name:"Hospital São Carlos",poi_type:"hospital",
  lat:-23.552,lng:-46.633,city:"São Paulo",state:"SP",
  source_url:"https://example.org/venue",source_confidence:0.85,
};

test("Permanent venue importer normalizes stable identity and geographic cell",()=>{
  const row=normalizeRadarPoiAdminV1(venue,"2026-10-15T12:00:00.000Z");
  assert.equal(row.canonical_name,"Hospital São Carlos");
  assert.equal(row.normalized_name,"hospital sao carlos");
  assert.equal(row.geo_cell,"g2:-2356:-4664");
  assert.equal(row.status,"inactive");
  assert.equal(row.metadata.review,"pending");
  assert.equal(row.source,"manual");
  assert.equal(row.source_external_id,"hospital_centro_001");
});
test("It does not fabricate dates, demand probability or driver data",()=>{
  const row=normalizeRadarPoiAdminV1(venue);
  for(const key of ["starts_at","egress_start_at","probability_pct","driver_id","journey_id"])
    assert.ok(!(key in row),key);
});
test("Coordinates, provenance and URLs must be valid",()=>{
  const bads=[
    {...venue,lat:120}, {...venue,lng:190}, {...venue,lat:""},
    {...venue,source:""}, {...venue,source_external_id:""},
    {...venue,canonical_name:""}, {...venue,source_url:"javascript:alert(1)"},
    {...venue,source_confidence:1.8}, {...venue,source_url:"https://user:pw@example.com/path"}
  ];
  for(const bad of bads)assert.throws(()=>normalizeRadarPoiAdminV1(bad),JSON.stringify(bad));
});
test("Admin API requires a server-side bearer secret for every method",()=>{
  const route=readFileSync(new URL("../../app/api/v1/radar/poi/admin/route.ts",import.meta.url),"utf8");
  assert.match(route,/serverEnv\(\)\.radarIngestSecret/);
  for(const method of ["GET","POST","PATCH"]) {
    const part=route.split("export async function "+method+"(")[1];
    assert.ok(part?.includes("if(!authorized(request))return unauthorized()"),method);
  }
  assert.match(route,/reviewRadarPoiAdminV1/);
  assert.match(route,/ingestRadarPoisAdminV1/);
});
test("Unreviewed crawler inserts are inactive and approved rows cannot be overwritten",()=>{
  const source=readFileSync(new URL("../../src/radar-poi-admin-v1.ts",import.meta.url),"utf8");
  assert.match(source,/status:"inactive" as const/);
  assert.match(source,/if\(found\.data\.status==="active"\)continue/);
  assert.match(source,/if\(payload\.dry_run===true\)/);
  assert.match(source,/source_external_id/);
  assert.match(source,/MAX_BATCH=50/);
});
