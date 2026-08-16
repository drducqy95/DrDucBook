-- Accounts created while production was still on the legacy Free-only bootstrap missed the
-- seven-day Premium trial. Repair only recent rows that have never been edited by an admin.
update public.account_access as access
set role = 'premium',
    permissions = array[
        'cloud_backup', 'download_content', 'export_ebook',
        'authoring_chapter', 'edit_ebook_chapter', 'web_service'
    ]::text[],
    role_starts_at = users.created_at,
    role_expires_at = users.created_at + interval '7 days',
    updated_at = now()
from auth.users as users
where access.user_id = users.id
  and lower(coalesce(users.email, '')) <> 'drducqy95@gmail.com'
  and access.role = 'free'
  and access.updated_by is null
  and access.role_starts_at is null
  and access.role_expires_at is null
  and users.created_at > now() - interval '7 days';

-- Keep the legacy three-argument overload for installed clients that can only assign permanent
-- roles. The current five-argument function remains the authority and performs all validation,
-- normalization, protected-admin checks and audit logging.
create or replace function public.admin_update_account_access(
    p_user_id uuid,
    p_role text,
    p_permissions text[]
)
returns public.account_access
language sql
security definer
set search_path = public
as $$
    select public.admin_update_account_access(
        p_user_id,
        p_role,
        p_permissions,
        null,
        null
    );
$$;

revoke all on function public.admin_update_account_access(uuid, text, text[]) from public;
grant execute on function public.admin_update_account_access(uuid, text, text[]) to authenticated;

notify pgrst, 'reload schema';
