"use client";
import type {ReactNode} from "react";

export default function AccountPageHeader({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children?: ReactNode;
}) {
  return (
    <section className="srAccountPageHeader">
      <div className="srAccountBrandRow">
        <img src="/logo-srrotas.png" alt="Sr. Rotas" />
        <div>
          <strong>Sr. Rotas</strong>
          <small>Seu copiloto de rentabilidade</small>
        </div>
      </div>
      <div className="srAccountPageTitle">
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {children}
    </section>
  );
}
