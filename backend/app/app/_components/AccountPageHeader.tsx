"use client";
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
  return (
    <section className="srAccountPageHeader srAccountPageHeader024">
      <div className="srAccountPageTitle">
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {children?(
        <div className="srAccountPageHeaderExtra024">{children}</div>
      ):null}
    </section>
  );
}
