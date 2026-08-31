"use client";
import {usePathname} from "next/navigation";
import type {ReactNode} from "react";

export default function AccountPageHeader({
  title,
  subtitle,
  children,
}:{
  title:string;
  subtitle:string;
  children?:ReactNode;
}){
  const pathname=usePathname();
  const resolvedTitle=
    pathname.startsWith("/app/perfil/mensagens")
      ?"Mensagens"
      :pathname.startsWith("/app/mcp")
        ?"IA MCP"
        :title;

  return (
    <section className="srAccountPageHeader srAccountPageHeader024">
      <div className="srAccountPageTitle">
        <h1>{resolvedTitle}</h1>
        <p>{subtitle}</p>
      </div>
      {children?(
        <div className="srAccountPageHeaderExtra024">{children}</div>
      ):null}
    </section>
  );
}
