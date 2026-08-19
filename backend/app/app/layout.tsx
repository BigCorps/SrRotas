import type { Metadata } from "next";
import type { ReactNode } from "react";
import WebAppShell from "./_components/WebAppShell";
import AppSessionGate from "./_components/AppSessionGate";
import "./app.css";

export const metadata: Metadata = {
  title: "Painel",
  description: "Dashboard do Sr. Rotas: histórico, IA, MCP, conta e plano.",
  robots: { index: false, follow: false },
};

export default function AppLayout({ children }: { children: ReactNode }) {
  const playStoreUrl = process.env.NEXT_PUBLIC_PLAY_STORE_URL?.trim() || undefined;
  return (
    <AppSessionGate>
      <WebAppShell playStoreUrl={playStoreUrl}>{children}</WebAppShell>
    </AppSessionGate>
  );
}
