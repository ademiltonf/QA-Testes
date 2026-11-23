// OXEIPTVPRO - servidor de licenças (Supabase Edge Function "oxe")
// Segredos necessários (Edge Functions -> Secrets):
//   ASAAS_API_KEY  = sua chave de API do Asaas
//   ASAAS_ENV      = sandbox  (troque para production quando for vender de verdade)
//   WEBHOOK_TOKEN  = uma senha qualquer, a mesma configurada no webhook do Asaas
import { createClient } from "npm:@supabase/supabase-js@2";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);
const ASAAS_KEY = Deno.env.get("ASAAS_API_KEY") ?? "";
const ASAAS_URL = (Deno.env.get("ASAAS_ENV") ?? "sandbox") === "production"
  ? "https://api.asaas.com/v3"
  : "https://api-sandbox.asaas.com/v3";
const WEBHOOK_TOKEN = Deno.env.get("WEBHOOK_TOKEN") ?? "";

// ====== REGRAS DE VENDA ======
const TRIAL_DAYS = 3;
const PLANS: Record<string, { value: number; days: number; label: string }> = {
  anual: { value: 75, days: 365, label: "Anual" },
  vitalicio: { value: 120, days: 36500, label: "Vitalício" },
};
// =============================

const DAY = 86_400_000;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
const validId = (id: string) => /^[0-9A-F]{2}(:[0-9A-F]{2}){5}$/.test(id);

async function getDevice(id: string) {
  const found = await db.from("devices").select("*").eq("device_id", id).maybeSingle();
  if (found.data) return found.data;
  // Primeiro acesso: começa o teste grátis
  await db.from("devices").upsert(
    { device_id: id, trial_until: new Date(Date.now() + TRIAL_DAYS * DAY).toISOString() },
    { onConflict: "device_id", ignoreDuplicates: true },
  );
  const again = await db.from("devices").select("*").eq("device_id", id).single();
  if (again.error) throw new Error(again.error.message);
  return again.data;
}

function view(d: any) {
  const now = Date.now();
  const activeUntil = d.active_until ? Date.parse(d.active_until) : null;
  const trialUntil = Date.parse(d.trial_until);
  let status = "expired";
  if (d.blocked) status = "blocked";
  else if (activeUntil && activeUntil > now) status = "active";
  else if (trialUntil > now) status = "trial";
  return {
    device_id: d.device_id,
    status,
    plan: d.plan,
    active_until: activeUntil,
    trial_until: trialUntil,
    server_time: now,
    prices: Object.fromEntries(Object.entries(PLANS).map(([k, p]) => [k, p.value])),
  };
}

async function asaas(path: string, method = "GET", body?: unknown) {
  const r = await fetch(ASAAS_URL + path, {
    method,
    headers: {
      "access_token": ASAAS_KEY,
      "content-type": "application/json",
      "User-Agent": "OXEIPTVPRO",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data?.errors?.[0]?.description ?? `Asaas respondeu ${r.status}`);
  return data;
}

async function checkout(id: string, planKey: string) {
  const plan = PLANS[planKey];
  if (!plan) return json({ error: "Plano inválido" }, 400);
  await getDevice(id);

  // Reaproveita um link pendente criado nas últimas 24h
  const pending = await db.from("payments").select("*")
    .eq("device_id", id).eq("plan", planKey).eq("status", "PENDING")
    .gte("created_at", new Date(Date.now() - DAY).toISOString())
    .order("created_at", { ascending: false }).limit(1).maybeSingle();
  if (pending.data) return json({ url: pending.data.url, plan: planKey, value: plan.value });

  const link = await asaas("/paymentLinks", "POST", {
    name: `OXEIPTVPRO ${plan.label} - ${id}`,
    description: `Ativação ${plan.label} do OXEIPTVPRO no aparelho ${id}`,
    value: plan.value,
    billingType: "UNDEFINED", // cliente escolhe Pix, cartão ou boleto
    chargeType: "DETACHED",
    dueDateLimitDays: 1,
    externalReference: `${id}|${planKey}`,
    notificationEnabled: false,
  });
  if (!link?.id || !link?.url) throw new Error("O Asaas não devolveu o link de pagamento");

  const ins = await db.from("payments").insert({
    link_id: link.id, device_id: id, plan: planKey, value: plan.value, url: link.url,
  });
  if (ins.error) throw new Error(ins.error.message);
  return json({ url: link.url, plan: planKey, value: plan.value });
}

async function webhook(req: Request) {
  if (WEBHOOK_TOKEN && req.headers.get("asaas-access-token") !== WEBHOOK_TOKEN) {
    return json({ error: "unauthorized" }, 401);
  }
  const ev = await req.json().catch(() => ({}));
  if (!["PAYMENT_RECEIVED", "PAYMENT_CONFIRMED"].includes(ev?.event)) return json({ ok: true });
  const p = ev.payment ?? {};

  // Descobre de qual aparelho é o pagamento
  let row: any = null;
  if (p.paymentLink) {
    row = (await db.from("payments").select("*").eq("link_id", p.paymentLink).maybeSingle()).data;
  }
  if (!row && typeof p.externalReference === "string" && p.externalReference.includes("|")) {
    const [dev, plan] = p.externalReference.split("|");
    row = (await db.from("payments").select("*").eq("device_id", dev).eq("plan", plan)
      .eq("status", "PENDING").order("created_at", { ascending: false }).limit(1).maybeSingle()).data;
  }
  if (!row || row.status === "PAID") return json({ ok: true });

  const plan = PLANS[row.plan];
  const d = await getDevice(row.device_id);
  const current = d.active_until ? Date.parse(d.active_until) : 0;
  const base = current > Date.now() ? current : Date.now(); // renovação soma ao tempo restante
  const until = new Date(base + plan.days * DAY).toISOString();

  await db.from("devices").update({ active_until: until, plan: row.plan }).eq("device_id", row.device_id);
  await db.from("payments").update({ status: "PAID", payment_id: p.id ?? null, paid_at: new Date().toISOString() })
    .eq("link_id", row.link_id);
  // Desativa o link para não ser pago de novo
  try { await asaas(`/paymentLinks/${row.link_id}`, "DELETE"); } catch (_) { /* ignora */ }
  return json({ ok: true });
}

Deno.serve(async (req) => {
  try {
    const url = new URL(req.url);
    if (url.searchParams.get("hook") === "1") return await webhook(req);
    if (req.method !== "POST") return json({ ok: true, app: "OXEIPTVPRO" });

    const body = await req.json().catch(() => ({}));
    const id = String(body.device_id ?? "").toUpperCase();
    if (!validId(id)) return json({ error: "Código do aparelho inválido" }, 400);

    if (body.action === "checkout") return await checkout(id, String(body.plan ?? ""));

    const d = await getDevice(id);
    await db.from("devices").update({
      last_seen: new Date().toISOString(),
      app_version: body.version ?? null,
    }).eq("device_id", id);
    return json(view(d));
  } catch (e) {
    return json({ error: String((e as Error)?.message ?? e) }, 500);
  }
});
