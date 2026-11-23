-- OXEIPTVPRO - banco de dados das licenças
-- Cole tudo no Supabase em: SQL Editor -> New query -> Run

create table if not exists public.devices (
  device_id    text primary key,              -- código do aparelho (ex: A4:3F:91:7C:22:E0)
  created_at   timestamptz not null default now(),
  trial_until  timestamptz not null,          -- fim do teste grátis
  active_until timestamptz,                   -- fim da licença paga (vitalícia = daqui a 100 anos)
  plan         text,                          -- 'anual' ou 'vitalicio'
  blocked      boolean not null default false,-- marque true para bloquear um aparelho
  last_seen    timestamptz,
  app_version  text,
  note         text                           -- anotações suas (nome do cliente, etc.)
);

create table if not exists public.payments (
  link_id    text primary key,                -- id do link de pagamento no Asaas
  device_id  text not null references public.devices(device_id),
  plan       text not null,
  value      numeric not null,
  url        text not null,
  status     text not null default 'PENDING', -- PENDING ou PAID
  payment_id text,
  created_at timestamptz not null default now(),
  paid_at    timestamptz
);

-- Ninguém de fora lê ou altera as tabelas; só o servidor (função "oxe").
alter table public.devices  enable row level security;
alter table public.payments enable row level security;
