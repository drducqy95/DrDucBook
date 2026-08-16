import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const timedMigration = readFileSync(
  new URL("../supabase/migrations/20260809120000_timed_account_entitlements.sql", import.meta.url),
  "utf8",
);
const repairMigration = readFileSync(
  new URL("../supabase/migrations/20260812090000_repair_premium_trial_and_rpc_compatibility.sql", import.meta.url),
  "utf8",
);

test("new accounts receive a seven-day Premium trial", () => {
  assert.match(timedMigration, /then 'admin' else 'premium' end/i);
  assert.match(timedMigration, /then null else now\(\) \+ interval '7 days' end/i);
});

test("repair backfills only recent untouched Free accounts", () => {
  assert.match(repairMigration, /access\.role = 'free'/i);
  assert.match(repairMigration, /access\.updated_by is null/i);
  assert.match(repairMigration, /users\.created_at > now\(\) - interval '7 days'/i);
  assert.match(repairMigration, /role_starts_at = users\.created_at/i);
  assert.match(repairMigration, /role_expires_at = users\.created_at \+ interval '7 days'/i);
});

test("repair keeps the legacy permanent-role RPC overload", () => {
  assert.match(
    repairMigration,
    /admin_update_account_access\(\s*p_user_id uuid,\s*p_role text,\s*p_permissions text\[\]\s*\)/i,
  );
  assert.match(
    repairMigration,
    /admin_update_account_access\(\s*p_user_id,\s*p_role,\s*p_permissions,\s*null,\s*null\s*\)/i,
  );
  assert.match(repairMigration, /notify pgrst, 'reload schema'/i);
});
