alter table public.account_access
    drop constraint if exists account_access_permissions_check;

alter table public.account_access
    add constraint account_access_permissions_check check (
        permissions <@ array[
            'cloud_backup',
            'download_content',
            'export_ebook',
            'authoring_chapter',
            'edit_ebook_chapter',
            'web_service',
            'manage_accounts'
        ]::text[]
    );

update public.account_access
set permissions = case role
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
end,
updated_at = now();

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
        target_user_id, changed_by, old_role, new_role,
        old_permissions, new_permissions
    ) values (
        p_user_id, auth.uid(), previous.role, updated.role,
        previous.permissions, updated.permissions
    );
    return updated;
end;
$$;

revoke all on function public.admin_update_account_access(uuid, text, text[]) from public;
grant execute on function public.admin_update_account_access(uuid, text, text[]) to authenticated;
