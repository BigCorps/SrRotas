import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import PwaRegister from "./pwa-register";

export const metadata: Metadata = {
  metadataBase: new URL("https://srrotas.com"),
  title: {
    default: "Sr. Rotas",
    template: "%s | Sr. Rotas",
  },
  description: "Seu copiloto inteligente para analisar ofertas e rentabilidade de corridas.",
  applicationName: "Sr. Rotas",
  manifest: "/manifest.webmanifest",
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png",
  },
  openGraph: {
    title: "Sr. Rotas",
    description: "Seu copiloto inteligente para corridas mais rentáveis.",
    url: "https://srrotas.com",
    siteName: "Sr. Rotas",
    images: [{ url: "/og-srrotas.png", width: 1200, height: 630 }],
    locale: "pt_BR",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#073746",
  colorScheme: "light",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body style={{ fontFamily: "system-ui, -apple-system, sans-serif", margin: 0, background: "#F7F0C8", color: "#073746" }}>
        <PwaRegister />
        {children}
      </body>
    </html>
  );
}
