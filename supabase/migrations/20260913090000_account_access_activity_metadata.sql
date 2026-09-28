-- Migration: 20260913090000_account_access_activity_metadata.sql
-- Description: Add last_sign_in_at column to account_access and sync from auth.users

alter table public.account_access
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists last_sign_in_at timestamptz;

-- Backfill created_at and last_sign_in_at from auth.users
update public.account_access access
   set created_at = coalesce(u.created_at, access.created_at),
       last_sign_in_at = u.last_sign_in_at
  from auth.users u
 where access.user_id = u.id;

-- Update trigger to populate last_sign_in_at and created_at on bootstrap
create or replace function public.bootstrap_account_access()
returns trigger
language plpgsql
security definer
set search_path = public
as $func$
begin
    insert into public.account_access (user_id, email, role, permissions, created_at, last_sign_in_at)
    values (
        new.id,
        coalesce(new.email, ''),
        'free',
        array[
            'cloud_backup',
            'download_content',
            'export_ebook',
            'authoring_chapter',
            'edit_ebook_chapter'
        ]::text[],
        coalesce(new.created_at, now()),
        new.last_sign_in_at
    )
    on conflict (user_id) do update
    set email = excluded.email,
        last_sign_in_at = coalesce(excluded.last_sign_in_at, public.account_access.last_sign_in_at);
    return new;
end;
$func$;
