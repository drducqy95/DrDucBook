import assert from "node:assert/strict";
import test from "node:test";
import manifest from "../supabase/artifacts/hf-artifacts-manifest.json" with { type: "json" };
import {
  AssetTicketError,
  buildHfResolveUrl,
  createAssetTicket,
  findArtifact,
  normalizeArtifactId,
  parseRangeHeader,
  ticketIdHash,
  verifyAssetTicket,
} from "../supabase/functions/_shared/asset_ticket.mjs";

const secret = "unit-test-secret-with-at-least-thirty-two-bytes";
const userId = "00000000-0000-4000-8000-000000000001";

test("manifest only resolves allow-listed DrDucBook artifacts", () => {
  const artifact = findArtifact(manifest, "translation-quick-clean");

  assert.equal(artifact.hfRepo, "Drduc/Legadofork");
  assert.equal(
    buildHfResolveUrl(artifact),
    "https://huggingface.co/datasets/Drduc/Legadofork/resolve/main/packages/translation/legado-qt-clean-20260721.zip",
  );
  assert.throws(() => normalizeArtifactId("../secret"), AssetTicketError);
  assert.throws(() => findArtifact(manifest, "unknown-artifact"), AssetTicketError);
});

test("asset tickets are signed, scoped, expiring and hashable without leaking the ticket", async () => {
  const now = new Date("2026-07-31T00:00:00Z");
  const ticket = await createAssetTicket({
    userId,
    artifactId: "translation-quick-clean",
    secret,
    now,
    ttlSeconds: 60,
    nonce: "fixed-nonce",
  });

  const payload = await verifyAssetTicket({
    ticket,
    secret,
    now: new Date("2026-07-31T00:00:30Z"),
    expectedArtifactId: "translation-quick-clean",
  });
  assert.equal(payload.userId, userId);
  assert.equal(payload.artifactId, "translation-quick-clean");
  assert.match(await ticketIdHash(ticket), /^[a-f0-9]{64}$/u);

  await assert.rejects(
    () => verifyAssetTicket({
      ticket,
      secret,
      now: new Date("2026-07-31T00:02:00Z"),
      expectedArtifactId: "translation-quick-clean",
    }),
    /Ticket expired/u,
  );
  await assert.rejects(
    () => verifyAssetTicket({
      ticket: `${ticket.slice(0, -1)}x`,
      secret,
      now,
      expectedArtifactId: "translation-quick-clean",
    }),
    /Invalid ticket signature/u,
  );
  await assert.rejects(
    () => verifyAssetTicket({
      ticket,
      secret,
      now,
      expectedArtifactId: "translation-hachimi-onnx-arm64",
    }),
    /Ticket does not match artifact/u,
  );
});

test("range parser supports single byte ranges and rejects ambiguous requests", () => {
  assert.deepEqual(parseRangeHeader("bytes=10-99", 200), {
    start: 10,
    end: 99,
    header: "bytes=10-99",
  });
  assert.deepEqual(parseRangeHeader("bytes=150-", 200), {
    start: 150,
    end: 199,
    header: "bytes=150-199",
  });
  assert.deepEqual(parseRangeHeader("bytes=-25", 200), {
    start: 175,
    end: 199,
    header: "bytes=175-199",
  });

  assert.throws(() => parseRangeHeader("bytes=0-1,4-5", 200), /Multiple ranges/u);
  assert.throws(() => parseRangeHeader("bytes=300-400", 200), /Unsatisfiable/u);
  assert.throws(() => parseRangeHeader("items=0-1", 200), /Invalid Range/u);
});
