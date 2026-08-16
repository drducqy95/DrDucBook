import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const migration = readFileSync(
  new URL("../supabase/migrations/20260731060000_cloud_sync_foundation.sql", import.meta.url),
  "utf8",
);

const tables = [
  "profiles",
  "cloud_devices",
  "sync_snapshots",
  "sync_heads",
  "sync_events",
];

test("cloud sync tables enable RLS and deny anon", () => {
  for (const table of tables) {
    assert.match(
      migration,
      new RegExp(`alter table public\\.${table} enable row level security`, "i"),
      `${table} must enable RLS`,
    );
    assert.match(
      migration,
      new RegExp(`revoke all on public\\.${table} from anon`, "i"),
      `${table} must revoke anon`,
    );
  }
});

test("cloud sync policies scope rows to auth.uid", () => {
  for (const table of ["profiles", "cloud_devices", "sync_snapshots", "sync_heads", "sync_events"]) {
    assert.match(
      migration,
      new RegExp(`create policy ${table}_[\\s\\S]+?on public\\.${table}[\\s\\S]+?auth\\.uid\\(\\)`, "i"),
      `${table} must have auth.uid scoped policies`,
    );
  }
  assert.match(migration, /create policy sync_snapshots_no_update[\s\S]+using \(false\)[\s\S]+with check \(false\)/i);
  assert.match(migration, /create policy sync_events_no_update[\s\S]+using \(false\)[\s\S]+with check \(false\)/i);
});

test("storage buckets are private and path-owned by the user id folder", () => {
  assert.match(migration, /'drducbook-snapshots'[\s\S]+false[\s\S]+536870912/i);
  assert.match(migration, /'drducbook-user-assets'[\s\S]+false[\s\S]+536870912/i);
  assert.match(
    migration,
    /bucket_id = 'drducbook-snapshots'[\s\S]+storage\.foldername\(name\)\)\[1\] = auth\.uid\(\)::text/i,
  );
  assert.match(
    migration,
    /bucket_id = 'drducbook-user-assets'[\s\S]+storage\.foldername\(name\)\)\[1\] = auth\.uid\(\)::text/i,
  );
});

test("snapshot metadata requires immutable bucket, hash, size and canonical path", () => {
  assert.match(migration, /check \(content_sha256 ~ '\^\[a-f0-9\]\{64\}\$'\)/i);
  assert.match(migration, /check \(content_size_bytes >= 0\)/i);
  assert.match(migration, /check \(storage_bucket = 'drducbook-snapshots'\)/i);
  assert.match(
    migration,
    /storage_path = user_id::text \|\| '\/snapshots\/' \|\| revision \|\| '\/' \|\| id::text \|\| '\.drducsnapshot'/i,
  );
});
