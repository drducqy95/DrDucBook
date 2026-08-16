create extension if not exists pgcrypto;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create table if not exists public.profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.cloud_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    install_id text not null,
    platform text not null default 'android',
    app_id text not null,
    app_version text,
    device_name text,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(user_id, install_id),
    check (length(trim(install_id)) between 8 and 160),
    check (platform in ('android', 'web', 'desktop')),
    check (app_id in ('com.drducbook.app', 'com.drducbook.app.debug'))
);

create table if not exists public.sync_snapshots (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    revision text not null,
    parent_revision text,
    schema_version integer not null,
    content_sha256 text not null,
    content_size_bytes bigint not null,
    storage_bucket text not null default 'drducbook-snapshots',
    storage_path text not null,
    encrypted boolean not null default true,
    created_by_device uuid references public.cloud_devices(id) on delete set null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique(user_id, revision),
    unique(storage_bucket, storage_path),
    check (revision ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
    check (parent_revision is null or parent_revision ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
    check (schema_version > 0),
    check (content_sha256 ~ '^[a-f0-9]{64}$'),
    check (content_size_bytes >= 0),
    check (storage_bucket = 'drducbook-snapshots'),
    check (storage_path = user_id::text || '/snapshots/' || revision || '/' || id::text || '.drducsnapshot')
);

create table if not exists public.sync_heads (
    user_id uuid not null references auth.users(id) on delete cascade,
    target text not null,
    namespace text not null default 'drducbook',
    head_revision text,
    snapshot_id uuid references public.sync_snapshots(id) on delete set null,
    updated_by_device uuid references public.cloud_devices(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key(user_id, target, namespace),
    check (target in ('supabase', 'google_drive', 'both')),
    check (namespace = 'drducbook'),
    check (head_revision is null or head_revision ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$')
);

create table if not exists public.sync_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    device_id uuid references public.cloud_devices(id) on delete set null,
    target text not null,
    namespace text not null default 'drducbook',
    event_type text not null,
    snapshot_id uuid references public.sync_snapshots(id) on delete set null,
    redacted_details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    check (target in ('supabase', 'google_drive', 'both')),
    check (namespace = 'drducbook'),
    check (event_type in ('upload', 'download', 'restore', 'conflict', 'delete_account', 'unlink'))
);

alter table public.profiles enable row level security;
alter table public.cloud_devices enable row level security;
alter table public.sync_snapshots enable row level security;
alter table public.sync_heads enable row level security;
alter table public.sync_events enable row level security;

revoke all on public.profiles from anon;
revoke all on public.cloud_devices from anon;
revoke all on public.sync_snapshots from anon;
revoke all on public.sync_heads from anon;
revoke all on public.sync_events from anon;

grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.cloud_devices to authenticated;
grant select, insert, update, delete on public.sync_snapshots to authenticated;
grant select, insert, update, delete on public.sync_heads to authenticated;
grant select, insert, update, delete on public.sync_events to authenticated;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists cloud_devices_set_updated_at on public.cloud_devices;
create trigger cloud_devices_set_updated_at
before update on public.cloud_devices
for each row execute function public.set_updated_at();

drop trigger if exists sync_heads_set_updated_at on public.sync_heads;
create trigger sync_heads_set_updated_at
before update on public.sync_heads
for each row execute function public.set_updated_at();

create policy profiles_select_own on public.profiles
    for select to authenticated
    using (user_id = auth.uid());

create policy profiles_insert_own on public.profiles
    for insert to authenticated
    with check (user_id = auth.uid());

create policy profiles_update_own on public.profiles
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy profiles_delete_own on public.profiles
    for delete to authenticated
    using (user_id = auth.uid());

create policy cloud_devices_select_own on public.cloud_devices
    for select to authenticated
    using (user_id = auth.uid());

create policy cloud_devices_insert_own on public.cloud_devices
    for insert to authenticated
    with check (user_id = auth.uid());

create policy cloud_devices_update_own on public.cloud_devices
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy cloud_devices_delete_own on public.cloud_devices
    for delete to authenticated
    using (user_id = auth.uid());

create policy sync_snapshots_select_own on public.sync_snapshots
    for select to authenticated
    using (user_id = auth.uid());

create policy sync_snapshots_insert_own on public.sync_snapshots
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and storage_bucket = 'drducbook-snapshots'
        and storage_path = user_id::text || '/snapshots/' || revision || '/' || id::text || '.drducsnapshot'
    );

create policy sync_snapshots_no_update on public.sync_snapshots
    for update to authenticated
    using (false)
    with check (false);

create policy sync_snapshots_delete_own on public.sync_snapshots
    for delete to authenticated
    using (user_id = auth.uid());

create policy sync_heads_select_own on public.sync_heads
    for select to authenticated
    using (user_id = auth.uid());

create policy sync_heads_insert_own on public.sync_heads
    for insert to authenticated
    with check (user_id = auth.uid());

create policy sync_heads_update_own on public.sync_heads
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy sync_heads_delete_own on public.sync_heads
    for delete to authenticated
    using (user_id = auth.uid());

create policy sync_events_select_own on public.sync_events
    for select to authenticated
    using (user_id = auth.uid());

create policy sync_events_insert_own on public.sync_events
    for insert to authenticated
    with check (user_id = auth.uid());

create policy sync_events_no_update on public.sync_events
    for update to authenticated
    using (false)
    with check (false);

create policy sync_events_delete_own on public.sync_events
    for delete to authenticated
    using (user_id = auth.uid());

create index if not exists cloud_devices_user_seen_idx
    on public.cloud_devices(user_id, last_seen_at desc);

create index if not exists sync_snapshots_user_created_idx
    on public.sync_snapshots(user_id, created_at desc);

create index if not exists sync_heads_user_updated_idx
    on public.sync_heads(user_id, updated_at desc);

create index if not exists sync_events_user_created_idx
    on public.sync_events(user_id, created_at desc);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    (
        'drducbook-snapshots',
        'drducbook-snapshots',
        false,
        536870912,
        array['application/octet-stream', 'application/json']::text[]
    ),
    (
        'drducbook-user-assets',
        'drducbook-user-assets',
        false,
        536870912,
        array['application/octet-stream', 'application/json', 'image/png', 'image/jpeg', 'image/webp']::text[]
    )
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists drducbook_snapshots_select_own on storage.objects;
create policy drducbook_snapshots_select_own on storage.objects
    for select to authenticated
    using (
        bucket_id = 'drducbook-snapshots'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_snapshots_insert_own on storage.objects;
create policy drducbook_snapshots_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'drducbook-snapshots'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_snapshots_update_own on storage.objects;
create policy drducbook_snapshots_update_own on storage.objects
    for update to authenticated
    using (
        bucket_id = 'drducbook-snapshots'
        and (storage.foldername(name))[1] = auth.uid()::text
    )
    with check (
        bucket_id = 'drducbook-snapshots'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_snapshots_delete_own on storage.objects;
create policy drducbook_snapshots_delete_own on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'drducbook-snapshots'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_user_assets_select_own on storage.objects;
create policy drducbook_user_assets_select_own on storage.objects
    for select to authenticated
    using (
        bucket_id = 'drducbook-user-assets'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_user_assets_insert_own on storage.objects;
create policy drducbook_user_assets_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'drducbook-user-assets'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_user_assets_update_own on storage.objects;
create policy drducbook_user_assets_update_own on storage.objects
    for update to authenticated
    using (
        bucket_id = 'drducbook-user-assets'
        and (storage.foldername(name))[1] = auth.uid()::text
    )
    with check (
        bucket_id = 'drducbook-user-assets'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists drducbook_user_assets_delete_own on storage.objects;
create policy drducbook_user_assets_delete_own on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'drducbook-user-assets'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
