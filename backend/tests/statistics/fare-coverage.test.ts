import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { parseKnownFare } from "../../src/journey-fare-coverage";

test("missing fare is never treated as a confirmed zero-value fare", () => {
  for (const absent of [null, undefined, "", "  ", Number.NaN, Number.POSITIVE_INFINITY,
    Number.NEGATIVE_INFINITY, -1, "-0.01", "not-a-fare", false, true, [], {}]) {
    assert.equal(parseKnownFare(absent), null, String(absent));
  }
});

test("explicit zero fare remains reconciled and positive fares preserve cents", () => {
  for (const recordedZero of [0, "0", " 0 ", -0]) {
    assert.equal(parseKnownFare(recordedZero), 0);
  }
  assert.equal(parseKnownFare("17.29"), 17.29);
  assert.equal(parseKnownFare(17.291), 17.29);
  assert.equal(parseKnownFare(17.295), 17.3);
});

test("realized journey reader uses guarded fare parsing for both outcome and session amounts", () => {
  const code = readFileSync(new URL("../../src/journey-realized-0262.ts", import.meta.url), "utf8");
  assert.match(code, /import \{ parseKnownFare as money \} from "\.\/journey-fare-coverage"/);
  assert.match(code, /fareById = new Map\(fares\.map\(\(row\) => \[String\(row\.id\), money\(row\.fare\)\]\)\)/);
  assert.match(code, /if \(typeof fare === "number"\) \{/);
  assert.match(code, /current\.matched \+= 1/);
  assert.match(code, /session \? money\(session\.earnings\) : null/);
});
