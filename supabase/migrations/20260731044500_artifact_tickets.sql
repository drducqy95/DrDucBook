create table if not exists public.artifact_tickets (
    id_hash text primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    artifact_id text not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);

alter table public.artifact_tickets enable row level security;

revoke all on public.artifact_tickets from anon, authenticated;

create index if not exists artifact_tickets_user_created_idx
    on public.artifact_tickets(user_id, created_at desc);

create index if not exists artifact_tickets_expiry_idx
    on public.artifact_tickets(expires_at)
    where consumed_at is null;

create or replace function public.consume_artifact_ticket(
    p_id_hash text,
    p_artifact_id text
)
returns table(user_id uuid, artifact_id text)
language plpgsql
security definer
set search_path = public
as $$
begin
    return query
    update public.artifact_tickets ticket
       set consumed_at = now()
     where ticket.id_hash = p_id_hash
       and ticket.artifact_id = p_artifact_id
       and ticket.consumed_at is null
       and ticket.expires_at > now()
     returning ticket.user_id, ticket.artifact_id;
end;
$$;

revoke all on function public.consume_artifact_ticket(text, text) from public;
grant execute on function public.consume_artifact_ticket(text, text) to service_role;
