import test from "node:test";
import assert from "node:assert/strict";
import { contextualRankingScoreV1 } from "../../src/radar-contextual-v1";
import { geoCellV1 as poiCell } from "../../src/radar-poi-v1";
import {
  geoCellV1 as resolverCell,
  nameSimilarityV1,
  normalizePoiTextV1,
} from "../../src/radar-poi-resolver-v1";

test("nearby equivalent outranks distant equivalent", () => {
  const near = contextualRankingScoreV1({
    probability: 75, baseline: 60, distanceKm: .8, confidence: .8,
    hasContext: false, eta: "2026-10-01T22:00:00Z",
  });
  const far = contextualRankingScoreV1({
    probability: 75, baseline: 60, distanceKm: 5, confidence: .8,
    hasContext: false, eta: "2026-10-01T22:00:00Z",
  });
  assert.ok(near > far);
});

test("context without probability remains discovery-only", () => {
  const score = contextualRankingScoreV1({
    probability: null, baseline: 60, distanceKm: .5, confidence: .9,
    hasContext: true, eta: "2026-10-01T22:00:00Z",
  });
  assert.ok(score < 40);
});

test("ranking remains bounded", () => {
  const score = contextualRankingScoreV1({
    probability: 100, baseline: 0, distanceKm: 0, confidence: 1,
    hasContext: true, eta: "2026-10-01T22:00:00Z",
    contextStart: "2026-10-01T21:55:00Z",
    contextEnd: "2026-10-01T22:30:00Z",
  });
  assert.ok(score <= 100);
});

test("POI normalization and conservative similarity", () => {
  assert.equal(normalizePoiTextV1("Teatro Renault — São Paulo"), "teatro renault sao paulo");
  assert.ok(nameSimilarityV1("Teatro Renault", "Teatro-Renault") > .9);
  assert.ok(nameSimilarityV1("Shopping Eldorado", "Hospital Sírio Libanês") < .25);
});

test("both POI engines preserve the g2 negative-coordinate contract", () => {
  assert.equal(poiCell(-23.5505, -46.6333), "g2:-2356:-4664");
  assert.equal(resolverCell(-23.5505, -46.6333), "g2:-2356:-4664");
});
