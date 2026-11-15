import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

// Guard the production login regression: an admin may also be a driver.
// The admin inbox must never accept a driver cookie in place of an admin cookie.
const loginRoute = readFileSync(
  new URL("../../app/api/v1/web/session/route.ts", import.meta.url),
  "utf8",
);

test("web login issues both cookies for a driver with authorized import access", () => {
  const start = loginRoute.indexOf("const headers = new Headers()");
  const end = loginRoute.indexOf("return Response.json(", start);
  assert.ok(start >= 0 && end > start, "login cookie issuance code not found");
  const source = loginRoute.slice(start, end);

  assert.match(source, /if \(driver\) \{[\s\S]*?billingCookieHeader\(session\.token\)/);
  assert.match(source, /if \(canImport\) \{[\s\S]*?importCookieHeader\(session\.token\)/);
  assert.doesNotMatch(
    source,
    /\}\s*else\s+if\s*\(canImport\)/,
    "admin cookie must not depend on absence of a driver account",
  );
  assert.match(source, /headers\.append\("Set-Cookie", billingCookieHeader/);
  assert.match(source, /headers\.append\("Set-Cookie", importCookieHeader/);
});

test("upload keeps the stricter admin-cookie requirement", () => {
  const service = readFileSync(
    new URL("../../src/radar-json-inbox/service.ts", import.meta.url),
    "utf8",
  );
  assert.match(service, /checked\.actor!\.source !== "admin"/);
  assert.match(service, /admin_session_required/);
});
