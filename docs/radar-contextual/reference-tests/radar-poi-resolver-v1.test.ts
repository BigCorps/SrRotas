import { geoCellV1,nameSimilarityV1,normalizePoiTextV1 } from "../../../backend/src/radar-poi-resolver-v1";

describe("radar poi resolver v1",()=>{
 test("normalization removes accents and punctuation",()=>{
  expect(normalizePoiTextV1("Teatro Renault — São Paulo")).toBe("teatro renault sao paulo");
 });
 test("same venue variations are highly similar",()=>{
  expect(nameSimilarityV1("Teatro Renault","Teatro-Renault")).toBeGreaterThan(.9);
 });
 test("unrelated venues are not merged",()=>{
  expect(nameSimilarityV1("Shopping Eldorado","Hospital Sírio Libanês")).toBeLessThan(.25);
 });
 test("geo cell exactly follows OfferContextEngine",()=>{
  expect(geoCellV1(-23.5505,-46.6333)).toBe("g2:-2356:-4664");
 });
});
