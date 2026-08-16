export default function Home() {
  return (
    <main style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <section style={{ width: "min(920px, 100%)", background: "white", borderRadius: 32, padding: "36px clamp(24px, 5vw, 56px)", boxShadow: "0 20px 70px rgba(7,55,70,.12)" }}>
        <div style={{ display: "grid", gridTemplateColumns: "minmax(180px, 300px) 1fr", gap: 32, alignItems: "center" }}>
          <img src="/logo-srrotas.png" alt="Sr. Rotas" style={{ width: "100%", maxHeight: 360, objectFit: "contain" }} />
          <div>
            <p style={{ margin: 0, fontWeight: 800, color: "#0D7F85", letterSpacing: ".08em", textTransform: "uppercase", fontSize: 13 }}>Sr. Rotas 2.0</p>
            <h1 style={{ margin: "8px 0 12px", fontSize: "clamp(36px, 6vw, 68px)", lineHeight: .98 }}>Seu copiloto inteligente.</h1>
            <p style={{ fontSize: 19, lineHeight: 1.55, color: "#35565c" }}>
              Análise local de ofertas, metas personalizadas, histórico estruturado e Pesquisa IA para motoristas de aplicativo.
            </p>
            <p style={{ fontSize: 14, color: "#587177" }}>
              Alpha em desenvolvimento. O Sr. Rotas não aceita nem rejeita corridas automaticamente.
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
