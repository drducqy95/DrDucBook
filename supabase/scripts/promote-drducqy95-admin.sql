-- One-time server-side promotion for the existing Google account.
-- Run only after the account_access migrations have been applied.
-- Execute in the Supabase SQL Editor or with a service-role deployment identity.
-- Never put a service-role key in the Android app.

begin;

do $$
declare
    target_user_id uuid;
    updated_rows integer;
begin
    select id
      into target_user_id
      from auth.users
     where lower(email) = lower('drducqy95@gmail.com')
     order by created_at asc
     limit 1;

    if target_user_id is null then
        raise exception 'No auth user found for drducqy95@gmail.com';
    end if;

    update public.account_access
       set role = 'admin',
           permissions = array[
               'cloud_backup',
               'download_content',
               'export_ebook',
               'authoring_chapter',
               'edit_ebook_chapter',
               'web_service',
               'manage_accounts'
           ]::text[],
           updated_at = now()
     where user_id = target_user_id;

    get diagnostics updated_rows = row_count;
    if updated_rows <> 1 then
        raise exception 'Expected one account_access row, updated %', updated_rows;
    end if;
end;
$$;

commit;

