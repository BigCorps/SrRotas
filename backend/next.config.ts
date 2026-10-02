import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,

  async rewrites() {
    return [
      {
        source: "/.well-known/oauth-authorization-server",
        destination:
          "/api/v1/mcp/oauth/metadata/authorization-server",
      },
      {
        source: "/.well-known/oauth-protected-resource/mcp",
        destination:
          "/api/v1/mcp/oauth/metadata/protected-resource",
      },
      {
        source: "/.well-known/oauth-protected-resource",
        destination:
          "/api/v1/mcp/oauth/metadata/protected-resource",
      },
    ];
  },

  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          {
            key: "Referrer-Policy",
            value: "strict-origin-when-cross-origin",
          },
          {
            key: "Permissions-Policy",
            value:
              "camera=(), microphone=(self), geolocation=(), payment=()",
          },
          {
            key: "Strict-Transport-Security",
            value: "max-age=31536000; includeSubDomains",
          },
        ],
      },
    ];
  },
};

export default nextConfig;
