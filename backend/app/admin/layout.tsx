import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./admin-brand.css";

export const metadata: Metadata = {
  title: { default: "Admin BigCorps · Sr. Rotas", template: "%s | Admin BigCorps" },
  description: "Console administrativo BigCorps para gestão do Sr. Rotas.",
  manifest: "/admin-manifest.webmanifest",
  icons: {
    icon: [
      { url: "/admin-icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { url: "/admin-icons/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
    apple: "/admin-icons/apple-touch-icon.png",
  },
  robots: { index: false, follow: false },
};

export const viewport: Viewport = {
  themeColor: "#0A2C21",
  colorScheme: "light dark",
};

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <div className="srAdminRealm">
      <nav className="srAdminUtilityNav" aria-label="Atalhos do Admin BigCorps">
        <a href="/admin">Control Center</a>
        <a href="/admin/diagnosticos">Diagnósticos</a>
        <a href="/admin/importacoes">Importações V7</a>
      </nav>
      {children}
    </div>
  );
}
