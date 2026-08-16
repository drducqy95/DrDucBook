-- Keep the production administrator deterministic when the Google account already exists.
-- This is idempotent and does not create an auth user or embed a password.
insert into public.account_access (user_id, email, role, permissions, updated_at)
select
    users.id,
    coalesce(users.email, 'drducqy95@gmail.com'),
    'admin',
    array[
        'cloud_backup',
        'download_content',
        'export_ebook',
        'authoring_chapter',
        'edit_ebook_chapter',
        'web_service',
        'manage_accounts'
    ]::text[],
    now()
from auth.users users
where lower(users.email) = 'drducqy95@gmail.com'
on conflict (user_id) do update
set email = excluded.email,
    role = 'admin',
    permissions = excluded.permissions,
    updated_at = now();

-- PostgREST can retain a stale schema cache after a freshly applied table migration.
notify pgrst, 'reload schema';
