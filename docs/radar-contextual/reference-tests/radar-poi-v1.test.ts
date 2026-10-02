import { geoCellV1 } from "../../../backend/src/radar-poi-v1";
describe("radar poi v1",()=>{
 test("matches OfferContextEngine g2 rule",()=>{
  expect(geoCellV1(-23.5505,-46.6333)).toBe("g2:-2356:-4664");
 });
 test("floor is preserved for negative coordinates",()=>{
  expect(geoCellV1(-23.5,-46.6)).toBe("g2:-2350:-4660");
 });
});
