alter table public.account_access
    add column if not exists role_starts_at timestamptz,
    add column if not exists role_expires_at timestamptz;

alter table public.account_access
    drop constraint if exists account_access_role_window_check;

alter table public.account_access
    add constraint account_access_role_window_check check (
        role_expires_at is null or (
            role_starts_at is not null and role_expires_at > role_starts_at
        )
    );

alter table public.account_access_audit
    add column if not exists old_role_starts_at timestamptz,
    add column if not exists new_role_starts_at timestamptz,
    add column if not exists old_role_expires_at timestamptz,
    add column if not exists new_role_expires_at timestamptz;

create or replace function public.effective_account_role(
    candidate uuid,
    at_time timestamptz default now()
)
returns text
language sql
stable
security definer
set search_path = public
as $$
    select coalesce((
        select case
            when (access.role_starts_at is null or access.role_starts_at <= at_time)
             and (access.role_expires_at is null or access.role_expires_at > at_time)
                then access.role
            else 'free'
        end
        from public.account_access access
        where access.user_id = candidate
    ), 'free');
$$;

revoke all on function public.effective_account_role(uuid, timestamptz) from public;
grant execute on function public.effective_account_role(uuid, timestamptz) to authenticated;

create or replace function public.is_account_admin(candidate uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.effective_account_role(candidate) = 'admin'
       and exists (
            select 1
            from public.account_access access
            where access.user_id = candidate
              and 'manage_accounts' = any(access.permissions)
       );
$$;

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

    insert into public.account_access as current_access(
        user_id,
        email,
        role,
        permissions,
        role_starts_at,
        role_expires_at
    ) values (
        new.id,
        coalesce(new.email, ''),
        case when lower(coalesce(new.email, '')) = 'drducqy95@gmail.com'
            then 'admin' else 'premium' end,
        case when lower(coalesce(new.email, '')) = 'drducqy95@gmail.com'
            then array[
                'cloud_backup', 'download_content', 'export_ebook',
                'authoring_chapter', 'edit_ebook_chapter', 'web_service',
                'manage_accounts'
            ]::text[]
            else array[
                'cloud_backup', 'download_content', 'export_ebook',
                'authoring_chapter', 'edit_ebook_chapter', 'web_service'
            ]::text[]
        end,
        case when lower(coalesce(new.email, '')) = 'drducqy95@gmail.com'
            then null else now() end,
        case when lower(coalesce(new.email, '')) = 'drducqy95@gmail.com'
            then null else now() + interval '7 days' end
    )
    on conflict (user_id) do update
       set email = excluded.email,
           role = case when lower(excluded.email) = 'drducqy95@gmail.com'
               then 'admin' else current_access.role end,
           permissions = case when lower(excluded.email) = 'drducqy95@gmail.com'
               then array[
                   'cloud_backup', 'download_content', 'export_ebook',
                   'authoring_chapter', 'edit_ebook_chapter', 'web_service',
                   'manage_accounts'
               ]::text[]
               else current_access.permissions end,
           role_starts_at = case when lower(excluded.email) = 'drducqy95@gmail.com'
               then null else current_access.role_starts_at end,
           role_expires_at = case when lower(excluded.email) = 'drducqy95@gmail.com'
               then null else current_access.role_expires_at end,
           updated_at = now();
    return new;
end;
$$;

drop function if exists public.admin_update_account_access(uuid, text, text[]);

create function public.admin_update_account_access(
    p_user_id uuid,
    p_role text,
    p_permissions text[],
    p_role_starts_at timestamptz,
    p_role_expires_at timestamptz
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
    normalized_starts_at timestamptz;
    normalized_expires_at timestamptz;
begin
    if not public.is_account_admin(auth.uid()) then
        raise exception 'account administration permission required' using errcode = '42501';
    end if;
    if p_role not in ('free', 'premium', 'admin') then
        raise exception 'invalid account role' using errcode = '22023';
    end if;

    normalized_permissions := case p_role
        when 'free' then array[
            'cloud_backup', 'download_content', 'export_ebook',
            'authoring_chapter', 'edit_ebook_chapter'
        ]::text[]
        when 'premium' then array[
            'cloud_backup', 'download_content', 'export_ebook',
            'authoring_chapter', 'edit_ebook_chapter', 'web_service'
        ]::text[]
        when 'admin' then array[
            'cloud_backup', 'download_content', 'export_ebook',
            'authoring_chapter', 'edit_ebook_chapter', 'web_service',
            'manage_accounts'
        ]::text[]
    end;

    normalized_starts_at := case when p_role = 'free' then null else p_role_starts_at end;
    normalized_expires_at := case when p_role = 'free' then null else p_role_expires_at end;
    if normalized_expires_at is not null and (
        normalized_starts_at is null or normalized_expires_at <= normalized_starts_at
    ) then
        raise exception 'invalid account role window' using errcode = '22023';
    end if;

    select * into previous
      from public.account_access
     where user_id = p_user_id
     for update;
    if not found then
        raise exception 'account not found' using errcode = 'P0002';
    end if;
    if lower(previous.email) = 'drducqy95@gmail.com' and (
        p_role <> 'admin' or p_role_starts_at is not null or p_role_expires_at is not null
    ) then
        raise exception 'default administrator must remain permanent' using errcode = '42501';
    end if;

    update public.account_access
       set role = p_role,
           permissions = normalized_permissions,
           role_starts_at = normalized_starts_at,
           role_expires_at = normalized_expires_at,
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
        new_permissions,
        old_role_starts_at,
        new_role_starts_at,
        old_role_expires_at,
        new_role_expires_at
    ) values (
        p_user_id,
        auth.uid(),
        previous.role,
        updated.role,
        previous.permissions,
        updated.permissions,
        previous.role_starts_at,
        updated.role_starts_at,
        previous.role_expires_at,
        updated.role_expires_at
    );
    return updated;
end;
$$;

revoke all on function public.admin_update_account_access(
    uuid, text, text[], timestamptz, timestamptz
) from public;
grant execute on function public.admin_update_account_access(
    uuid, text, text[], timestamptz, timestamptz
) to authenticated;

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

    account_role := public.effective_account_role(auth.uid());
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
            'kind', p_quota_kind, 'used', used_count, 'limit', null, 'unlimited', true
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
        select public.effective_account_role(auth.uid()) as role
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

create index if not exists account_access_role_expiry_idx
    on public.account_access(role, role_expires_at);

update public.account_access access
set role = 'admin',
    permissions = array[
        'cloud_backup', 'download_content', 'export_ebook',
        'authoring_chapter', 'edit_ebook_chapter', 'web_service',
        'manage_accounts'
    ]::text[],
    role_starts_at = null,
    role_expires_at = null,
    updated_at = now()
from auth.users users
where access.user_id = users.id
  and lower(users.email) = 'drducqy95@gmail.com';

notify pgrst, 'reload schema';
