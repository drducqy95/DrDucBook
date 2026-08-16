import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const migration = readFileSync(
  new URL("../supabase/migrations/20260803090000_account_access_control.sql", import.meta.url),
  "utf8",
);

test("account access rows are protected by RLS", () => {
  assert.match(migration, /alter table public\.account_access enable row level security/i);
  assert.match(migration, /revoke all on public\.account_access from anon, authenticated/i);
  assert.match(
    migration,
    /create policy account_access_select_self_or_admin[\s\S]+user_id = auth\.uid\(\)[\s\S]+is_account_admin/i,
  );
});

test("clients cannot grant their own role directly", () => {
  assert.doesNotMatch(migration, /grant (insert|update|delete)[^;]+account_access to authenticated/i);
  assert.match(migration, /security definer[\s\S]+admin_update_account_access/i);
  assert.match(migration, /if not public\.is_account_admin\(auth\.uid\(\)\)/i);
});

test("admin updates validate and audit role changes", () => {
  assert.match(migration, /p_role not in \('free', 'premium', 'admin'\)/i);
  assert.match(migration, /array_remove\(normalized_permissions, 'manage_accounts'\)/i);
  assert.match(migration, /insert into public\.account_access_audit/i);
});

test("base migration creates a safe access row before later trial migrations", () => {
  assert.match(migration, /create trigger auth_user_bootstrap_account_access/i);
  assert.match(migration, /role text not null default 'free'/i);
  assert.match(migration, /permissions text\[\] not null default array[\s\S]+download_content/i);
});

test("free daily quotas are atomic, idempotent and server enforced", () => {
  assert.match(migration, /create table if not exists public\.account_daily_quota_events/i);
  assert.match(migration, /primary key\(user_id, usage_date, quota_kind, operation_key\)/i);
  assert.match(migration, /pg_advisory_xact_lock/i);
  assert.match(migration, /when 'download_content' then 5/i);
  assert.match(migration, /when 'export_ebook' then 1/i);
  assert.match(migration, /when 'authoring_chapter' then 3/i);
  assert.match(migration, /when 'edit_ebook_chapter' then 3/i);
  assert.match(migration, /account_role in \('premium', 'admin'\)/i);
});
