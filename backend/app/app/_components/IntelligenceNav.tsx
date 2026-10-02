"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const items = [
  { href: "/app/estatisticas", label: "Estatísticas" },
  { href: "/app/ia", label: "Pergunte" },
  { href: "/app/conciliacao", label: "Conciliação" },
];

export default function IntelligenceNav() {
  const path = usePathname();
  return (
    <nav className="srG3Nav" aria-label="Inteligência Sr. Rotas">
      {items.map((item) => (
        <Link key={item.href} href={item.href} className={path.startsWith(item.href) ? "active" : ""}>
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
