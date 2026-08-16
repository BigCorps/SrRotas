"use client";

import { useEffect } from "react";

export default function PwaRegister() {
  useEffect(() => {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // PWA é complementar ao app Android nativo; falha aqui não afeta as APIs.
      });
    }
  }, []);
  return null;
}
