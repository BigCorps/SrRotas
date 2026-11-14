import { test } from "node:test";
import assert from "node:assert/strict";
import { inspectRadarJsonFile, RADAR_LOCAL_FILE_LIMIT } from "../../src/radar-admin-file-inspection";
const bytes = (text: string) => new TextEncoder().encode(text);
test("syntax inspection does not infer any unavailable v1/v2 envelope or record readiness", () => {
  for (const text of ['{}','[]','null','12','{"schema":"radar.admin-import.v2"}','{"schema":"radar.admin-import.v1"}']) {
    const result = inspectRadarJsonFile(bytes(text));
    assert.equal(result.status, "syntax_valid");
    assert.equal(Object.hasOwn(result, "rows"), false);
    assert.match(result.message, /registros ainda não foram validados/);
  }
});
test("empty, malformed, executable text and invalid UTF-8 fail without exposing contents", () => {
  for (const text of ['', '{', '{"secret":"test-private",}', '<script>alert(1)</script>']) {
    const result = inspectRadarJsonFile(bytes(text));
    assert.equal(result.status, "invalid_json"); assert.doesNotMatch(JSON.stringify(result), /test-private|script/);
  }
  assert.equal(inspectRadarJsonFile(new Uint8Array([0xc3, 0x28])).status, "invalid_encoding");
});
test("bounded bytes, Unicode and UTF-8 BOM are handled locally", () => {
  assert.equal(inspectRadarJsonFile(new Uint8Array(RADAR_LOCAL_FILE_LIMIT + 1)).status, "too_large");
  assert.equal(inspectRadarJsonFile(bytes('\ufeff{"nome":"São Paulo"}')).status, "syntax_valid");
  assert.equal(inspectRadarJsonFile(bytes('"' + 'x'.repeat(RADAR_LOCAL_FILE_LIMIT - 2) + '"')).status, "syntax_valid");
});
