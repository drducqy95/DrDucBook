create table if not exists public.account_access (
    user_id uuid primary key references auth.users(id) on delete cascade,
    email text not null default '',
    role text not null default 'free',
    permissions text[] not null default array[
        'cloud_backup',
        'download_content',
        'export_ebook',
        'authoring_chapter',
        'edit_ebook_chapter'
    ]::text[],
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id) on delete set null,
    check (role in ('free', 'premium', 'admin')),
    check (
        permissions <@ array[
            'cloud_backup',
            'download_content',
            'export_ebook',
            'authoring_chapter',
            'edit_ebook_chapter',
            'manage_accounts'
        ]::text[]
    )
);

create table if not exists public.account_access_audit (
    id bigint generated always as identity primary key,
    target_user_id uuid not null references auth.users(id) on delete cascade,
    changed_by uuid not null references auth.users(id) on delete restrict,
    old_role text,
    new_role text not null,
    old_permissions text[] not null default '{}'::text[],
    new_permissions text[] not null default '{}'::text[],
    created_at timestamptz not null default now()
);

create or replace function public.is_account_admin(candidate uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
          from public.account_access access
         where access.user_id = candidate
           and access.role = 'admin'
           and 'manage_accounts' = any(access.permissions)
    );
$$;

revoke all on function public.is_account_admin(uuid) from public;
grant execute on function public.is_account_admin(uuid) to authenticated;

create or replace function public.bootstrap_account_access()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles(user_id, display_name, avatar_url)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'),
        new.raw_user_meta_data ->> 'avatar_url'
    )
    on conflict (user_id) do update
       set display_name = coalesce(excluded.display_name, public.profiles.display_name),
           avatar_url = coalesce(excluded.avatar_url, public.profiles.avatar_url),
           updated_at = now();

    insert into public.account_access(user_id, email)
    values (new.id, coalesce(new.email, ''))
    on conflict (user_id) do update
       set email = excluded.email,
           updated_at = now();
    return new;
end;
$$;

drop trigger if exists auth_user_bootstrap_account_access on auth.users;
create trigger auth_user_bootstrap_account_access
after insert or update of email, raw_user_meta_data on auth.users
for each row execute function public.bootstrap_account_access();

insert into public.profiles(user_id, display_name, avatar_url)
select
    users.id,
    coalesce(users.raw_user_meta_data ->> 'full_name', users.raw_user_meta_data ->> 'name'),
    users.raw_user_meta_data ->> 'avatar_url'
from auth.users users
on conflict (user_id) do nothing;

insert into public.account_access(user_id, email)
select users.id, coalesce(users.email, '')
from auth.users users
on conflict (user_id) do update
set email = excluded.email;

alter table public.account_access enable row level security;
alter table public.account_access_audit enable row level security;

revoke all on public.account_access from anon, authenticated;
revoke all on public.account_access_audit from anon, authenticated;
grant select on public.account_access to authenticated;
grant select on public.account_access_audit to authenticated;

drop policy if exists account_access_select_self_or_admin on public.account_access;
create policy account_access_select_self_or_admin on public.account_access
    for select to authenticated
    using (user_id = auth.uid() or public.is_account_admin());

drop policy if exists account_access_audit_select_admin on public.account_access_audit;
create policy account_access_audit_select_admin on public.account_access_audit
    for select to authenticated
    using (public.is_account_admin());

create or replace function public.admin_update_account_access(
    p_user_id uuid,
    p_role text,
    p_permissions text[]
)
returns public.account_access
language plpgsql
security definer
set search_path = public
as $$
declare
    previous public.account_access;
    updated public.account_access;
    normalized_permissions text[];
begin
    if not public.is_account_admin(auth.uid()) then
        raise exception 'account administration permission required' using errcode = '42501';
    end if;
    if p_role not in ('free', 'premium', 'admin') then
        raise exception 'invalid account role' using errcode = '22023';
    end if;
    if not coalesce(p_permissions, '{}'::text[]) <@ array[
        'cloud_backup', 'download_content', 'export_ebook',
        'authoring_chapter', 'edit_ebook_chapter', 'manage_accounts'
    ]::text[] then
        raise exception 'invalid account permission' using errcode = '22023';
    end if;

    normalized_permissions := coalesce(p_permissions, '{}'::text[]);
    if p_role = 'admin' then
        normalized_permissions := array[
            'cloud_backup', 'download_content', 'export_ebook',
            'authoring_chapter', 'edit_ebook_chapter', 'manage_accounts'
        ]::text[];
    else
        normalized_permissions := array_remove(normalized_permissions, 'manage_accounts');
    end if;

    select * into previous
      from public.account_access
     where user_id = p_user_id
     for update;
    if not found then
        raise exception 'account not found' using errcode = 'P0002';
    end if;

    update public.account_access
       set role = p_role,
           permissions = normalized_permissions,
           updated_by = auth.uid(),
           updated_at = now()
     where user_id = p_user_id
     returning * into updated;

    insert into public.account_access_audit(
        target_user_id,
        changed_by,
        old_role,
        new_role,
        old_permissions,
        new_permissions
    ) values (
        p_user_id,
        auth.uid(),
        previous.role,
        updated.role,
        previous.permissions,
        updated.permissions
    );
    return updated;
end;
$$;

revoke all on function public.admin_update_account_access(uuid, text, text[]) from public;
grant execute on function public.admin_update_account_access(uuid, text, text[]) to authenticated;

create index if not exists account_access_role_idx on public.account_access(role, updated_at desc);
create index if not exists account_access_audit_target_idx
    on public.account_access_audit(target_user_id, created_at desc);

create table if not exists public.account_daily_quota_events (
    user_id uuid not null references auth.users(id) on delete cascade,
    usage_date date not null default (timezone('utc', now())::date),
    quota_kind text not null,
    operation_key text not null,
    created_at timestamptz not null default now(),
    primary key(user_id, usage_date, quota_kind, operation_key),
    check (quota_kind in (
        'download_content',
        'export_ebook',
        'authoring_chapter',
        'edit_ebook_chapter'
    )),
    check (operation_key ~ '^[a-f0-9]{64}$')
);

alter table public.account_daily_quota_events enable row level security;
revoke all on public.account_daily_quota_events from anon, authenticated;
grant select on public.account_daily_quota_events to authenticated;

create policy account_daily_quota_events_select_own on public.account_daily_quota_events
    for select to authenticated
    using (user_id = auth.uid());

create or replace function public.consume_account_daily_quota(
    p_quota_kind text,
    p_operation_keys text[]
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    account_role text;
    today date := timezone('utc', now())::date;
    daily_limit integer;
    used_count integer;
    new_count integer;
    normalized_keys text[];
begin
    if auth.uid() is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    if p_quota_kind not in (
        'download_content', 'export_ebook', 'authoring_chapter', 'edit_ebook_chapter'
    ) then
        raise exception 'invalid quota kind' using errcode = '22023';
    end if;

    select array_agg(distinct pending.operation_key order by pending.operation_key)
      into normalized_keys
      from unnest(coalesce(p_operation_keys, '{}'::text[])) as pending(operation_key);
    if coalesce(array_length(normalized_keys, 1), 0) = 0
       or array_length(normalized_keys, 1) > 100
       or exists (
           select 1 from unnest(normalized_keys) as pending(operation_key)
            where pending.operation_key !~ '^[a-f0-9]{64}$'
       ) then
        raise exception 'invalid quota operation keys' using errcode = '22023';
    end if;

    select access.role into account_role
      from public.account_access access
     where access.user_id = auth.uid();
    if account_role is null then
        raise exception 'account access row not found' using errcode = 'P0002';
    end if;

    perform pg_advisory_xact_lock(
        hashtextextended(auth.uid()::text || ':' || today::text || ':' || p_quota_kind, 0)
    );
    select count(*) into used_count
      from public.account_daily_quota_events usage
     where usage.user_id = auth.uid()
       and usage.usage_date = today
       and usage.quota_kind = p_quota_kind;

    if account_role in ('premium', 'admin') then
        return jsonb_build_object(
            'kind', p_quota_kind,
            'used', used_count,
            'limit', null,
            'unlimited', true
        );
    end if;

    daily_limit := case p_quota_kind
        when 'download_content' then 5
        when 'export_ebook' then 1
        when 'authoring_chapter' then 3
        when 'edit_ebook_chapter' then 3
    end;
    select count(*) into new_count
      from unnest(normalized_keys) as pending(operation_key)
     where not exists (
         select 1
           from public.account_daily_quota_events usage
          where usage.user_id = auth.uid()
            and usage.usage_date = today
            and usage.quota_kind = p_quota_kind
            and usage.operation_key = pending.operation_key
     );
    if used_count + new_count > daily_limit then
        raise exception 'daily_quota_exceeded:%:%:%', p_quota_kind, used_count, daily_limit
            using errcode = 'P0001';
    end if;

    insert into public.account_daily_quota_events(user_id, usage_date, quota_kind, operation_key)
    select auth.uid(), today, p_quota_kind, pending.operation_key
      from unnest(normalized_keys) as pending(operation_key)
    on conflict do nothing;

    return jsonb_build_object(
        'kind', p_quota_kind,
        'used', used_count + new_count,
        'limit', daily_limit,
        'unlimited', false
    );
end;
$$;

revoke all on function public.consume_account_daily_quota(text, text[]) from public;
grant execute on function public.consume_account_daily_quota(text, text[]) to authenticated;

create or replace function public.get_account_daily_quota_usage()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    with kinds(kind, free_limit) as (
        values
            ('download_content'::text, 5),
            ('export_ebook'::text, 1),
            ('authoring_chapter'::text, 3),
            ('edit_ebook_chapter'::text, 3)
    ), current_access as (
        select role from public.account_access where user_id = auth.uid()
    )
    select coalesce(jsonb_agg(jsonb_build_object(
        'kind', kinds.kind,
        'used', (
            select count(*)
              from public.account_daily_quota_events usage
             where usage.user_id = auth.uid()
               and usage.usage_date = timezone('utc', now())::date
               and usage.quota_kind = kinds.kind
        ),
        'limit', case when current_access.role = 'free' then kinds.free_limit else null end,
        'unlimited', current_access.role in ('premium', 'admin')
    ) order by kinds.kind), '[]'::jsonb)
    from kinds cross join current_access;
$$;

revoke all on function public.get_account_daily_quota_usage() from public;
grant execute on function public.get_account_daily_quota_usage() to authenticated;

create index if not exists account_daily_quota_events_lookup_idx
    on public.account_daily_quota_events(user_id, usage_date, quota_kind);

-- The first administrator must be promoted once through the Supabase SQL editor
-- or another service-role-only deployment step. The Android app never contains
-- a service role key and cannot bootstrap its own administrator.
