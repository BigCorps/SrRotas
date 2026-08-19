"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";

export default function AppSessionGate({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [ready, setReady] = useState(pathname === "/app/entrar");

  useEffect(() => {
    if (pathname === "/app/entrar") {
      setReady(true);
      return;
    }

    let active = true;
    setReady(false);

    fetch("/api/v1/account/me", { cache: "no-store" })
      .then(async (response) => {
        if (!active) return;
        if (response.ok) {
          setReady(true);
          return;
        }
        if (response.status === 401) {
          const next = pathname || "/app";
          router.replace(`/app/entrar?next=${encodeURIComponent(next)}`);
          return;
        }
        setReady(true);
      })
      .catch(() => {
        if (active) setReady(true);
      });

    return () => { active = false; };
  }, [pathname, router]);

  if (pathname === "/app/entrar") return <>{children}</>;

  if (!ready) {
    return (
      <div className="srSessionLoading">
        <img src="/logo-srrotas.png" alt="" />
        <strong>Carregando seu Sr. Rotas...</strong>
        <span>Validando a sessão Web.</span>
      </div>
    );
  }

  return <>{children}</>;
}
