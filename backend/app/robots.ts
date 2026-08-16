import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  const index = process.env.NEXT_PUBLIC_INDEX_SITE === "true";
  const base = (process.env.NEXT_PUBLIC_SITE_URL || "https://sr-rotas.vercel.app").replace(/\/$/, "");
  return {
    rules: index ? { userAgent: "*", allow: "/" } : { userAgent: "*", disallow: "/" },
    sitemap: index ? `${base}/sitemap.xml` : undefined,
  };
}
