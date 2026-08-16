import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./base.css";
import PwaRegister from "./pwa-register";

function resolveSiteUrl() {
  const explicit = process.env.NEXT_PUBLIC_SITE_URL?.trim();
  if (explicit) return explicit.replace(/\/$/, "");
  const production = process.env.VERCEL_PROJECT_PRODUCTION_URL?.trim();
  if (production) return `https://${production}`;
  return "http://localhost:3000";
}

const siteUrl = resolveSiteUrl();
const indexSite = process.env.NEXT_PUBLIC_INDEX_SITE === "true";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: { default: "Sr. Rotas — Seu copiloto inteligente", template: "%s | Sr. Rotas" },
  description: "Copiloto inteligente para motoristas de aplicativo: análise local de ofertas, metas personalizadas, histórico estruturado, Pesquisa IA e MCP somente leitura.",
  applicationName: "Sr. Rotas",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/favicon.ico", apple: "/apple-touch-icon.png" },
  openGraph: {
    title: "Sr. Rotas — Seu copiloto inteligente",
    description: "Mais clareza para interpretar ofertas e entender os indicadores da sua jornada.",
    url: siteUrl,
    siteName: "Sr. Rotas",
    images: [{ url: "/og-srrotas.png", width: 1200, height: 630 }],
    locale: "pt_BR",
    type: "website",
  },
  robots: { index: indexSite, follow: indexSite },
};

export const viewport: Viewport = { themeColor: "#073746", colorScheme: "light" };

export default function RootLayout({ children }: { children: ReactNode }) {
  return <html lang="pt-BR"><body><PwaRegister />{children}</body></html>;
}
