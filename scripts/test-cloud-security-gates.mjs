import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import test from "node:test";
import manifest from "../supabase/artifacts/hf-artifacts-manifest.json" with { type: "json" };

const root = new URL("..", import.meta.url);
const rootPath = root.pathname.replace(/^\/([A-Za-z]:)/u, "$1");

const assetTicketFunction = text("supabase/functions/asset-ticket/index.ts");
const assetDownloadFunction = text("supabase/functions/asset-download/index.ts");
const assetTicketMigration = text("supabase/migrations/20260731044500_artifact_tickets.sql");
const cloudSyncMigration = text("supabase/migrations/20260731060000_cloud_sync_foundation.sql");

test("cloud source does not contain server secrets or legacy package Drive URLs", () => {
  const scannedFiles = walk([
    "app/src/main/java",
    "app/src/main/res",
    "supabase",
    "scripts",
  ]).filter((file) => /\.(kt|xml|ts|mjs|json|sql|ps1)$/u.test(file));
  const forbidden = [
    /hf_[A-Za-z0-9]{20,}/u,
    /service_role\s*[:=]\s*eyJ/iu,
    /SUPABASE_SERVICE_ROLE_KEY\s*=/u,
    /HF_READ_TOKEN\s*=/u,
    /ASSET_TICKET_SECRET\s*=/u,
    /drive\.google\.com\/(?:file|uc|open)\//iu,
    /docs\.google\.com\/uc/iu,
  ];

  for (const file of scannedFiles) {
    const content = readFileSync(join(rootPath, file), "utf8");
    for (const pattern of forbidden) {
      assert.doesNotMatch(content, pattern, `${file} must not contain ${pattern}`);
    }
  }
});

test("HF artifact manifest is allow-listed and never exposes token-bearing URLs", () => {
  assert.equal(manifest.repository, "Drduc/Legadofork");
  assert.ok(manifest.artifacts.length > 0);
  for (const artifact of manifest.artifacts) {
    assert.equal(artifact.hfRepo, "Drduc/Legadofork");
    assert.match(artifact.id, /^[a-z0-9][a-z0-9._-]{1,127}$/u);
    assert.match(artifact.sha256, /^[a-f0-9]{64}$/u);
    assert.ok(Number.isSafeInteger(artifact.sizeBytes) && artifact.sizeBytes > 0);
    assert.ok(!artifact.hfPath.startsWith("/"));
    assert.ok(!artifact.hfPath.includes(".."));
    assert.ok(!artifact.hfPath.includes("\\"));
    assert.ok(["hf_proxy", "storage_mirror_required"].includes(artifact.deliveryClass));
    assert.doesNotMatch(JSON.stringify(artifact), /hf_[A-Za-z0-9]{20,}|drive\.google\.com|docs\.google\.com/iu);
  }
});

test("Piper voice license review only clears audited Apache voices", () => {
  const piperArtifacts = manifest.artifacts.filter((artifact) => artifact.id.startsWith("tts-piper-"));
  const pending = piperArtifacts.filter((artifact) => artifact.license === "license-review-required");
  const cleared = piperArtifacts.filter((artifact) => artifact.license === "Apache-2.0");

  assert.equal(piperArtifacts.length, 29);
  assert.equal(cleared.length, 25);
  assert.deepEqual(
    pending.map((artifact) => artifact.id).sort(),
    [
      "tts-piper-indo_goreng",
      "tts-piper-john",
      "tts-piper-mattheo",
      "tts-piper-mattheo1",
    ],
  );
  for (const artifact of pending) {
    assert.equal(artifact.inventoryState, "local_verified_license_pending");
  }
  for (const artifact of cleared) {
    assert.equal(artifact.inventoryState, "local_verified");
    assert.match(artifact.provenance, /piper-voice-license-review\.md/u);
  }
});

test("asset ticket endpoint blocks license-pending Piper voices before issuing tickets", () => {
  assert.match(assetTicketFunction, /inventoryState[\s\S]+license_pending/u);
  assert.match(assetTicketFunction, /451[\s\S]+license_review_required/u);
  assert.match(assetTicketFunction, /Artifact is blocked until license review is complete/u);
});

test("asset ticket endpoint never reads HF token and persists only ticket hash", () => {
  const insertMatch = /from\("artifact_tickets"\)\.insert\(\{([\s\S]+?)\n\s*\}\);/u.exec(assetTicketFunction);

  assert.doesNotMatch(assetTicketFunction, /HF_READ_TOKEN/u);
  assert.match(assetTicketFunction, /ticketIdHash\(ticket\)/u);
  assert.match(assetTicketFunction, /id_hash:\s*idHash/u);
  assert.ok(insertMatch, "asset ticket endpoint must persist a ticket record");
  assert.doesNotMatch(insertMatch[1], /\bticket\b/u);
  assert.match(assetTicketFunction, /enforceRateLimit\(supabase,\s*userId\)/u);
  assert.match(assetTicketFunction, /decodeJwtSubject\(request\.headers\.get\("authorization"\)\)/u);
});

test("asset download endpoint requires one-time consume before proxying HF", () => {
  const consumeIndex = assetDownloadFunction.indexOf("await consumeTicket(ticket, artifact.id)");
  const fetchIndex = assetDownloadFunction.indexOf("await fetch(buildHfResolveUrl(artifact)");

  assert.ok(consumeIndex > 0, "download function must consume ticket");
  assert.ok(fetchIndex > consumeIndex, "ticket must be consumed before HF fetch");
  assert.match(assetDownloadFunction, /expectedArtifactId:\s*artifactId \?\? undefined/u);
  assert.match(assetDownloadFunction, /authorization:\s*`Bearer \$\{requiredEnv\("HF_READ_TOKEN"\)\}`/u);
  assert.match(assetDownloadFunction, /parseRangeHeader\(request\.headers\.get\("range"\),\s*artifact\.sizeBytes\)/u);
  assert.match(assetDownloadFunction, /storage_mirror_required/u);
});

test("artifact tickets are RLS-protected and consumable only by service role RPC", () => {
  assert.match(assetTicketMigration, /alter table public\.artifact_tickets enable row level security/i);
  assert.match(assetTicketMigration, /revoke all on public\.artifact_tickets from anon, authenticated/i);
  assert.match(assetTicketMigration, /security definer/i);
  assert.match(assetTicketMigration, /ticket\.consumed_at is null/i);
  assert.match(assetTicketMigration, /ticket\.expires_at > now\(\)/i);
  assert.match(assetTicketMigration, /grant execute on function public\.consume_artifact_ticket\(text, text\) to service_role/i);
});

test("cloud sync migration keeps RLS, private storage, immutable snapshots and delete cascade", () => {
  for (const table of ["profiles", "cloud_devices", "sync_snapshots", "sync_heads", "sync_events"]) {
    assert.match(cloudSyncMigration, new RegExp(`alter table public\\.${table} enable row level security`, "i"));
    assert.match(cloudSyncMigration, new RegExp(`revoke all on public\\.${table} from anon`, "i"));
  }
  assert.match(cloudSyncMigration, /references auth\.users\(id\) on delete cascade/i);
  assert.match(cloudSyncMigration, /'drducbook-snapshots'[\s\S]+false[\s\S]+536870912/i);
  assert.match(cloudSyncMigration, /'drducbook-user-assets'[\s\S]+false[\s\S]+536870912/i);
  assert.match(cloudSyncMigration, /sync_snapshots_no_update[\s\S]+with check \(false\)/i);
  assert.match(cloudSyncMigration, /storage\.foldername\(name\)\)\[1\] = auth\.uid\(\)::text/i);
});

function text(path) {
  return readFileSync(join(rootPath, path), "utf8");
}

function walk(paths) {
  const files = [];
  for (const path of paths) {
    const fullPath = join(rootPath, path);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      for (const child of readdirSync(fullPath)) {
        files.push(...walk([join(path, child)]));
      }
    } else {
      files.push(relative(rootPath, fullPath).replaceAll("\\", "/"));
    }
  }
  return files;
}
