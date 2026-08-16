import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import PwaRegister from "./pwa-register";

const siteUrl = "https://sr-rotas.vercel.app";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Sr. Rotas — Seu copiloto inteligente",
    template: "%s | Sr. Rotas",
  },
  description:
    "Copiloto inteligente para motoristas de aplicativo: análise de ofertas, metas personalizadas, histórico estruturado, Pesquisa IA e arquitetura MCP.",
  applicationName: "Sr. Rotas",
  manifest: "/manifest.webmanifest",
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png",
  },
  openGraph: {
    title: "Sr. Rotas — Seu copiloto inteligente",
    description:
      "Mais clareza para interpretar ofertas e entender a rentabilidade da sua jornada.",
    url: siteUrl,
    siteName: "Sr. Rotas",
    images: [{ url: "/og-srrotas.png", width: 1200, height: 630 }],
    locale: "pt_BR",
    type: "website",
  },
  robots: {
    index: false,
    follow: false,
  },
};

export const viewport: Viewport = {
  themeColor: "#073746",
  colorScheme: "light",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <PwaRegister />
        {children}
      </body>
    </html>
  );
}
