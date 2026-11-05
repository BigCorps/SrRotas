import { execFileSync, spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
const id = randomUUID();
const network = "sr-radar-test-" + id;
const database = "sr-radar-db-" + id;
const docker = (...args) =>
  execFileSync("docker", args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
try {
  docker("network", "create", network);
  docker(
    "run",
    "-d",
    "--name",
    database,
    "--network",
    network,
    "-e",
    "POSTGRES_HOST_AUTH_METHOD=trust",
    "-p",
    "127.0.0.1::5432",
    "postgres:17.7-alpine",
  );
  let ready = false;
  for (let i = 0; i < 60; i++) {
    try {
      docker("exec", database, "pg_isready", "-U", "postgres");
      ready = true;
      break;
    } catch {}
    await new Promise((r) => setTimeout(r, 200));
  }
  if (!ready) throw Error("Isolated PostgreSQL did not start");
  const port = docker("port", database, "5432/tcp").split(":").at(-1);
  const env = {
    ...process.env,
    SR_RADAR_TEST_DATABASE_URL: `postgresql://postgres@127.0.0.1:${port}/postgres`,
    SR_RADAR_TEST_DB_CONTAINER: database,
    SR_RADAR_TEST_NETWORK: network,
  };
  const result = spawnSync(
    process.execPath,
    [
      "--import",
      "tsx",
      "--test",
      "--test-concurrency=1",
      "tests/radar-isolated/integration.test.ts",
    ],
    { env, stdio: "inherit" },
  );
  process.exitCode = result.status ?? 1;
} finally {
  try {
    docker("rm", "-f", database);
  } catch {}
  try {
    docker("network", "rm", network);
  } catch {}
}
