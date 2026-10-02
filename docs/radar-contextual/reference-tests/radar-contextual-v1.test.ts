import { contextualRankingScoreV1 } from "../../../backend/src/radar-contextual-v1";

describe("radar contextual v1",()=>{
 test("better nearby probability ranks above distant equivalent",()=>{
  const near=contextualRankingScoreV1({probability:75,baseline:60,distanceKm:.8,confidence:.8,hasContext:false,eta:"2026-10-01T22:00:00Z"});
  const far=contextualRankingScoreV1({probability:75,baseline:60,distanceKm:5,confidence:.8,hasContext:false,eta:"2026-10-01T22:00:00Z"});
  expect(near).toBeGreaterThan(far);
 });
 test("context without probability stays discovery-only",()=>{
  const score=contextualRankingScoreV1({probability:null,baseline:60,distanceKm:.5,confidence:.9,hasContext:true,eta:"2026-10-01T22:00:00Z"});
  expect(score).toBeLessThan(40);
 });
 test("ranking is bounded",()=>{
  const score=contextualRankingScoreV1({probability:100,baseline:0,distanceKm:0,confidence:1,hasContext:true,
    eta:"2026-10-01T22:00:00Z",contextStart:"2026-10-01T21:55:00Z",contextEnd:"2026-10-01T22:30:00Z"});
  expect(score).toBeLessThanOrEqual(100);
 });
});
