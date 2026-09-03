"use client";

import { FormEvent, useMemo, useState } from "react";

type RadarEvent = {
  id: string;
  source: string;
  external_id: string;
  event_type: string;
  name: string;
  venue_name: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  lat: number;
  lng: number;
  starts_at: string;
  expected_end_at: string;
  source_url: string | null;
  confidence: number;
  status: "active" | "expired" | "cancelled";
  last_verified_at: string;
};

type FormState = {
  id: string;
  source: string;
  event_type: string;
  name: string;
  venue_name: string;
  address: string;
  city: string;
  state: string;
  lat: string;
  lng: string;
  starts_at: string;
  expected_end_at: string;
  source_url: string;
  confidence: string;
};

const emptyForm: FormState = {
  id: "",
  source: "manual",
  event_type: "event",
  name: "",
  venue_name: "",
  address: "",
  city: "",
  state: "SP",
  lat: "",
  lng: "",
  starts_at: "",
  expected_end_at: "",
  source_url: "",
  confidence: "0.85",
};

function localInput(iso?: string | null) {
  if (!iso) return "";
  const date = new Date(iso);
  if (!Number.isFinite(date.getTime())) return "";
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 16);
}

export default function RadarAdminPage() {
  const [secret, setSecret] = useState("");
  const [events, setEvents] = useState<RadarEvent[]>([]);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [status, setStatus] = useState<"all" | RadarEvent["status"]>("active");
  const [message, setMessage] = useState("Informe o RADAR_INGEST_SECRET para carregar os eventos.");
  const [busy, setBusy] = useState(false);

  const headers = useMemo(() => ({ Authorization: `Bearer ${secret}`, "Content-Type": "application/json" }), [secret]);
  const visible = useMemo(() => status === "all" ? events : events.filter((event) => event.status === status), [events, status]);

  async function load() {
    if (!secret.trim()) return setMessage("Informe o segredo do Radar.");
    setBusy(true);
    try {
      const response = await fetch("/api/v1/radar/admin?limit=300", { headers });
      const json = await response.json();
      if (!response.ok) throw new Error(json.error || `HTTP ${response.status}`);
      setEvents(json.events || []);
      setMessage(`${json.events?.length || 0} eventos carregados.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao carregar eventos.");
    } finally {
      setBusy(false);
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!secret.trim()) return setMessage("Informe o segredo do Radar.");
    const payload = {
      ...(form.id ? { id: form.id } : {}),
      source: form.source,
      event_type: form.event_type,
      name: form.name,
      venue_name: form.venue_name || null,
      address: form.address || null,
      city: form.city || null,
      state: form.state || null,
      country_code: "BR",
      lat: Number(form.lat),
      lng: Number(form.lng),
      starts_at: new Date(form.starts_at).toISOString(),
      expected_end_at: form.expected_end_at ? new Date(form.expected_end_at).toISOString() : null,
      source_url: form.source_url || null,
      confidence: Number(form.confidence || 0.85),
      status: "active",
    };
    setBusy(true);
    try {
      const response = await fetch("/api/v1/radar/admin", {
        method: form.id ? "PATCH" : "POST",
        headers,
        body: JSON.stringify(payload),
      });
      const json = await response.json();
      if (!response.ok) throw new Error(json.error || `HTTP ${response.status}`);
      setForm(emptyForm);
      setMessage(form.id ? "Evento atualizado." : "Evento cadastrado.");
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao salvar evento.");
    } finally {
      setBusy(false);
    }
  }

  function edit(event: RadarEvent) {
    setForm({
      id: event.id,
      source: event.source,
      event_type: event.event_type,
      name: event.name,
      venue_name: event.venue_name || "",
      address: event.address || "",
      city: event.city || "",
      state: event.state || "",
      lat: String(event.lat),
      lng: String(event.lng),
      starts_at: localInput(event.starts_at),
      expected_end_at: localInput(event.expected_end_at),
      source_url: event.source_url || "",
      confidence: String(event.confidence ?? 0.85),
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function cancel(id: string) {
    if (!secret.trim()) return;
    if (!window.confirm("Cancelar este evento no Radar? O registro será preservado para auditoria.")) return;
    setBusy(true);
    try {
      const response = await fetch(`/api/v1/radar/admin?id=${encodeURIComponent(id)}`, { method: "DELETE", headers });
      const json = await response.json();
      if (!response.ok) throw new Error(json.error || `HTTP ${response.status}`);
      setMessage("Evento cancelado.");
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Falha ao cancelar evento.");
    } finally {
      setBusy(false);
    }
  }

  const inputStyle = { width: "100%", boxSizing: "border-box", padding: "10px 12px", borderRadius: 10, border: "1px solid #d8dee4", background: "#fff" } as const;
  const buttonStyle = { border: 0, borderRadius: 10, padding: "10px 14px", cursor: "pointer", fontWeight: 700 } as const;

  return (
    <main style={{ minHeight: "100vh", background: "#fbfaf5", color: "#17323a", padding: 24, fontFamily: "system-ui, sans-serif" }}>
      <div style={{ maxWidth: 1120, margin: "0 auto" }}>
        <header style={{ marginBottom: 18 }}>
          <div style={{ fontSize: 13, fontWeight: 800, color: "#7457d5" }}>Sr. Rotas Radar</div>
          <h1 style={{ margin: "4px 0 6px", fontSize: 28 }}>Administração de eventos</h1>
          <p style={{ margin: 0, color: "#63747a" }}>Cadastrar, revisar, corrigir e cancelar eventos sem alterar o histórico da coleta.</p>
        </header>

        <section style={{ background: "white", border: "1px solid #e2e6e8", borderRadius: 18, padding: 18, marginBottom: 16 }}>
          <label style={{ fontWeight: 700, display: "block", marginBottom: 6 }}>RADAR_INGEST_SECRET</label>
          <div style={{ display: "flex", gap: 8 }}>
            <input type="password" value={secret} onChange={(e) => setSecret(e.target.value)} style={inputStyle} autoComplete="off" />
            <button onClick={load} disabled={busy} style={{ ...buttonStyle, background: "#147fa1", color: "white", minWidth: 110 }}>Carregar</button>
          </div>
          <div style={{ marginTop: 8, color: "#63747a", fontSize: 13 }}>{message}</div>
        </section>

        <form onSubmit={save} style={{ background: "white", border: "1px solid #e2e6e8", borderRadius: 18, padding: 18, marginBottom: 16 }}>
          <h2 style={{ marginTop: 0, fontSize: 18 }}>{form.id ? "Editar evento" : "Cadastrar evento manual"}</h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(210px,1fr))", gap: 10 }}>
            <input required placeholder="Nome do evento" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} style={inputStyle} />
            <input placeholder="Local / venue" value={form.venue_name} onChange={(e) => setForm({ ...form, venue_name: e.target.value })} style={inputStyle} />
            <select value={form.event_type} onChange={(e) => setForm({ ...form, event_type: e.target.value })} style={inputStyle}>
              <option value="event">Evento</option><option value="music">Show / música</option><option value="sports">Esporte</option>
              <option value="theatre">Teatro / cultura</option><option value="fair_convention">Feira / convenção</option><option value="family">Família</option>
              <option value="airport">Aeroporto</option><option value="bus_terminal">Rodoviária</option><option value="mall">Shopping</option><option value="mobility_hub">Hub de mobilidade</option>
            </select>
            <input required placeholder="Fonte" value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value })} style={inputStyle} disabled={Boolean(form.id)} />
            <input placeholder="Endereço" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} style={inputStyle} />
            <input placeholder="Cidade" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} style={inputStyle} />
            <input placeholder="UF" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} style={inputStyle} />
            <input required type="number" step="any" placeholder="Latitude" value={form.lat} onChange={(e) => setForm({ ...form, lat: e.target.value })} style={inputStyle} />
            <input required type="number" step="any" placeholder="Longitude" value={form.lng} onChange={(e) => setForm({ ...form, lng: e.target.value })} style={inputStyle} />
            <label style={{ fontSize: 12, color: "#63747a" }}>Início<input required type="datetime-local" value={form.starts_at} onChange={(e) => setForm({ ...form, starts_at: e.target.value })} style={{ ...inputStyle, marginTop: 4 }} /></label>
            <label style={{ fontSize: 12, color: "#63747a" }}>Fim esperado<input type="datetime-local" value={form.expected_end_at} onChange={(e) => setForm({ ...form, expected_end_at: e.target.value })} style={{ ...inputStyle, marginTop: 4 }} /></label>
            <input type="number" min="0" max="1" step="0.01" placeholder="Confiança" value={form.confidence} onChange={(e) => setForm({ ...form, confidence: e.target.value })} style={inputStyle} />
            <input placeholder="URL da fonte" value={form.source_url} onChange={(e) => setForm({ ...form, source_url: e.target.value })} style={inputStyle} />
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button type="submit" disabled={busy} style={{ ...buttonStyle, background: "#147fa1", color: "white" }}>{form.id ? "Salvar alterações" : "Cadastrar"}</button>
            {form.id && <button type="button" onClick={() => setForm(emptyForm)} style={{ ...buttonStyle, background: "#eef2f3", color: "#17323a" }}>Cancelar edição</button>}
          </div>
        </form>

        <section style={{ background: "white", border: "1px solid #e2e6e8", borderRadius: 18, padding: 18 }}>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", justifyContent: "space-between" }}>
            <h2 style={{ margin: 0, fontSize: 18 }}>Eventos</h2>
            <div style={{ display: "flex", gap: 6 }}>
              {(["active", "expired", "cancelled", "all"] as const).map((key) => <button key={key} onClick={() => setStatus(key)} style={{ ...buttonStyle, padding: "7px 10px", background: status === key ? "#7457d5" : "#eef2f3", color: status === key ? "white" : "#17323a" }}>{key}</button>)}
            </div>
          </div>
          <div style={{ display: "grid", gap: 9, marginTop: 12 }}>
            {visible.map((event) => (
              <article key={event.id} style={{ border: "1px solid #e7eaec", borderRadius: 14, padding: 12, display: "grid", gap: 5 }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}><strong>{event.name}</strong><span style={{ fontSize: 12, fontWeight: 800, color: event.status === "active" ? "#16855b" : "#78858a" }}>{event.status}</span></div>
                <div style={{ fontSize: 13, color: "#63747a" }}>{event.source} · {event.event_type} · {event.venue_name || event.city || "local não informado"}</div>
                <div style={{ fontSize: 13 }}>{new Date(event.starts_at).toLocaleString("pt-BR")} → {new Date(event.expected_end_at).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</div>
                <div style={{ display: "flex", gap: 7, marginTop: 4 }}><button onClick={() => edit(event)} style={{ ...buttonStyle, padding: "7px 10px", background: "#eef2f3" }}>Editar</button>{event.status !== "cancelled" && <button onClick={() => cancel(event.id)} style={{ ...buttonStyle, padding: "7px 10px", background: "#ffe9e7", color: "#9c3029" }}>Cancelar evento</button>}</div>
              </article>
            ))}
            {!visible.length && <div style={{ color: "#63747a", padding: 10 }}>Nenhum evento neste filtro.</div>}
          </div>
        </section>
      </div>
    </main>
  );
}
