import type { ReactNode } from "react";
import styles from "./legal.module.css";

export default function LegalPage({ kicker, title, intro, children }: { kicker: string; title: string; intro: string; children: ReactNode }) {
  return <main className={styles.page}>
    <header className={styles.header}><a className={styles.brand} href="/"><img src="/logo-srrotas.png" alt=""/><span>Sr. Rotas</span></a></header>
    <section className={styles.main}>
      <span className={styles.kicker}>{kicker}</span><h1>{title}</h1><p className={styles.lede}>{intro}</p>
      <article className={styles.card}>{children}</article>
      <a className={styles.back} href="/">← Voltar para o início</a>
    </section>
    <footer className={styles.footer}>Sr. Rotas é desenvolvido pela <strong>BigCorps</strong> • contato@bigcorps.com.br</footer>
  </main>;
}
