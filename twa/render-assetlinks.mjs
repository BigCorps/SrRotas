import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const fingerprint = process.env.SHA256_FINGERPRINT?.trim();
if (!fingerprint) {
  console.error("Defina SHA256_FINGERPRINT, ex.: AA:BB:CC:...");
  process.exit(1);
}

const packageName = process.env.TWA_PACKAGE_ID?.trim() || "com.srrotas.web";
const output = resolve(process.cwd(), "backend/public/.well-known/assetlinks.json");
await mkdir(resolve(process.cwd(), "backend/public/.well-known"), { recursive: true });

const payload = [
  {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: packageName,
      sha256_cert_fingerprints: [fingerprint],
    },
  },
];

await writeFile(output, JSON.stringify(payload, null, 2) + "\n", "utf8");
console.log(`Gerado: ${output}`);
