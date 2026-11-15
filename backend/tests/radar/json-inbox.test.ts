import test from "node:test";
import assert from "node:assert/strict";
import {
  readUpload,
  sanitizeUploadName,
} from "../../src/radar-json-inbox/service";
function request(body: BodyInit, headers: Record<string, string> = {}) {
  return new Request("https://srrotas.com/api/v1/admin/radar/json-inbox", {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body,
    duplex: "half",
  } as RequestInit);
}
test("quarantine accepts legacy UTF-8 and preserves exact bytes including BOM/newlines", async () => {
  const bytes = Buffer.from(
    '\ufeff{\n "legacy": [1,2], "link": "http://169.254.169.254/latest"\n}\n',
  );
  assert.deepEqual(await readUpload(request(bytes)), bytes);
  assert.equal(sanitizeUploadName("../../arquivo\r\n.json"), "arquivo.json");
  assert.throws(() => sanitizeUploadName("a.zip"));
});
test("quarantine rejects invalid UTF-8, duplicate keys, depth, malformed/partial and oversized streams", async () => {
  for (const bytes of [
    Buffer.from([255]),
    Buffer.from("{"),
    Buffer.from('{"a":1,"a":2}'),
    Buffer.from('{"a":1,"\\u0061":2}'),
    Buffer.from("[".repeat(14) + "0" + "]".repeat(14)),
  ])
    await assert.rejects(() => readUpload(request(bytes)));
  await assert.rejects(() =>
    readUpload(request("{}", { "content-type": "application/zip" })),
  );
  await assert.rejects(() =>
    readUpload(request("{}", { "content-encoding": "gzip" })),
  );
  await assert.rejects(() =>
    readUpload(request("{}", { "content-length": "9" })),
  );
  const stream = new ReadableStream<Uint8Array>({
    start(c) {
      c.enqueue(new Uint8Array(1_000_000));
      c.enqueue(new Uint8Array(1_000_001));
      c.close();
    },
  });
  await assert.rejects(() => readUpload(request(stream)), /PAYLOAD_TOO_LARGE/);
});
