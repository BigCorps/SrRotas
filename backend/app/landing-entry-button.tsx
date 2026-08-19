"use client";

import { usePathname } from "next/navigation";

export default function LandingEntryButton() {
  const pathname = usePathname();
  if (pathname !== "/") return null;

  return (
    <a
      href="/app"
      aria-label="Entrar no painel do Sr. Rotas"
      style={{
        position: "fixed",
        right: "max(18px, env(safe-area-inset-right))",
        bottom: "max(18px, calc(env(safe-area-inset-bottom) + 18px))",
        zIndex: 90,
        display: "inline-flex",
        alignItems: "center",
        gap: 10,
        padding: "13px 18px",
        borderRadius: 999,
        background: "#087e8e",
        color: "#fff",
        textDecoration: "none",
        fontFamily: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif",
        fontSize: 14,
        fontWeight: 900,
        boxShadow: "0 14px 38px rgba(6,31,43,.24)",
        border: "1px solid rgba(255,255,255,.18)",
      }}
    >
      <img src="/logo-srrotas.png" alt="" style={{ width: 30, height: 30, objectFit: "contain" }} />
      Entrar no painel
      <span aria-hidden="true">→</span>
    </a>
  );
}
