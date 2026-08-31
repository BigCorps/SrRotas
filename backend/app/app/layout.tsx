import type {Metadata} from "next";
import type {ReactNode} from "react";
import WebAppShell from "./_components/WebAppShell";
import AppSessionGate from "./_components/AppSessionGate";
import "./app.css";
import "./auth-brand.css";
import "./theme-021.css";
import "./ui-023.css";
import "./theme-024.css";

export const metadata:Metadata={
  title:"Minha conta · Sr. Rotas",
  description:"Conta, plano, créditos, aparelhos, mensagens e integrações do Sr. Rotas.",
  robots:{index:false,follow:false}
};

export default function AppLayout({children}:{children:ReactNode}){
  const playStoreUrl=process.env.NEXT_PUBLIC_PLAY_STORE_URL?.trim()||undefined;
  return <AppSessionGate><WebAppShell playStoreUrl={playStoreUrl}>{children}</WebAppShell></AppSessionGate>;
}
