import { createHash } from "node:crypto";
import { resolveDriverAccess } from "../access";
import { authenticateBillingWeb } from "../billing-auth";
import { allowSecurityAction } from "../security-rate-limit";
import { newToken, safeEqual, sha256 } from "../security";
import { adminSupabase } from "../supabase";

export const MCP_SCOPE = "srrotas.read";
export const MCP_ACCESS_TTL_SECONDS = 60 * 60;
export const MCP_REFRESH_TTL_SECONDS = 30 * 24 * 60 * 60;
const AUTH_REQUEST_TTL_SECONDS = 10 * 60;
const AUTH_CODE_TTL_SECONDS = 5 * 60;

type OAuthClient = {
  client_id: string;
  client_name: string;
  client_uri: string | null;
  logo_uri: string | null;
  redirect_uris: string[];
  token_endpoint_auth_method: "none" | "client_secret_basic" | "client_secret_post";
  client_secret_hash: string | null;
  scopes: string[];
  revoked_at: string | null;
};

export function mcpPublicBase(request?: Request) {
  const explicit = process.env.MCP_PUBLIC_BASE_URL?.trim() || "";
  if (explicit) return explicit.replace(/\/$/, "");

  if (request) {
    const url = new URL(request.url);
    if (["localhost", "127.0.0.1", "::1"].includes(url.hostname)) {
      return url.origin.replace(/\/$/, "");
    }
  }

  // O resource/issuer MCP é canônico mesmo em previews da Vercel.
  return "https://srrotas.com";
}

export function mcpResourceUri(request?: Request) {
  return `${mcpPublicBase(request)}/mcp`;
}

export function protectedResourceMetadataUrl(request?: Request) {
  return `${mcpPublicBase(request)}/.well-known/oauth-protected-resource/mcp`;
}

export function authorizationServerMetadata(request: Request) {
  const base = mcpPublicBase(request);
  return {
    issuer: base,
    authorization_endpoint: `${base}/api/v1/mcp/oauth/authorize`,
    token_endpoint: `${base}/api/v1/mcp/oauth/token`,
    registration_endpoint: `${base}/api/v1/mcp/oauth/register`,
    revocation_endpoint: `${base}/api/v1/mcp/oauth/revoke`,
    scopes_supported: [MCP_SCOPE],
    response_types_supported: ["code"],
    grant_types_supported: ["authorization_code", "refresh_token"],
    token_endpoint_auth_methods_supported: [
      "none",
      "client_secret_basic",
      "client_secret_post",
    ],
    code_challenge_methods_supported: ["S256"],
    service_documentation: `${base}/integracoes/mcp`,
  };
}

export function protectedResourceMetadata(request: Request) {
  const base = mcpPublicBase(request);
  return {
    resource: `${base}/mcp`,
    resource_name: "Sr. Rotas MCP",
    authorization_servers: [base],
    bearer_methods_supported: ["header"],
    scopes_supported: [MCP_SCOPE],
    resource_documentation: `${base}/integracoes/mcp`,
  };
}

export function oauthNoStoreHeaders(extra?: HeadersInit) {
  const headers = new Headers(extra);
  headers.set("Cache-Control", "no-store");
  headers.set("Pragma", "no-cache");
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Vary", "Origin");
  return headers;
}

export function oauthJson(
  body: Record<string, unknown>,
  status = 200,
  extra?: HeadersInit,
) {
  const headers = oauthNoStoreHeaders(extra);
  headers.set("Content-Type", "application/json; charset=utf-8");
  return new Response(JSON.stringify(body), { status, headers });
}

export function oauthError(
  error: string,
  description: string,
  status = 400,
) {
  return oauthJson({ error, error_description: description }, status);
}

function base64urlSha256(value: string) {
  return createHash("sha256").update(value, "utf8").digest("base64url");
}

function stringValue(value: unknown, max = 1000) {
  return String(value ?? "").trim().slice(0, max);
}

function stringArray(value: unknown) {
  return Array.isArray(value)
    ? value.map((item) => stringValue(item, 2048)).filter(Boolean)
    : [];
}

function validPkceChallenge(value: string) {
  return /^[A-Za-z0-9_-]{43,128}$/.test(value);
}

function validVerifier(value: string) {
  return /^[A-Za-z0-9._~-]{43,128}$/.test(value);
}

function parseScope(raw: unknown) {
  const requested = stringValue(raw, 300)
    .split(/\s+/)
    .map((item) => item.trim())
    .filter(Boolean);
  if (!requested.length) return [MCP_SCOPE];
  if (requested.some((scope) => scope !== MCP_SCOPE)) return null;
  return [MCP_SCOPE];
}

function normalizeRedirectUri(raw: unknown) {
  const value = stringValue(raw, 2048);
  if (!value) return null;
  try {
    const url = new URL(value);
    if (url.hash || url.username || url.password) return null;
    const protocol = url.protocol.toLowerCase();

    if (protocol === "https:") return url.toString();

    if (protocol === "http:") {
      const host = url.hostname.toLowerCase();
      if (host === "localhost" || host === "127.0.0.1" || host === "::1") {
        return url.toString();
      }
      return null;
    }

    if (["javascript:", "data:", "file:", "vbscript:"].includes(protocol)) {
      return null;
    }

    // Native OAuth clients may use a private-use URI scheme.
    if (/^[a-z][a-z0-9+.-]*:$/.test(protocol)) return url.toString();
    return null;
  } catch {
    return null;
  }
}

function validPublicUri(raw: unknown) {
  const value = stringValue(raw, 2048);
  if (!value) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.hash) {
      return null;
    }
    return url.toString();
  } catch {
    return null;
  }
}

async function clientById(clientId: string): Promise<OAuthClient | null> {
  const { data, error } = await adminSupabase()
    .from("mcp_oauth_clients")
    .select("client_id,client_name,client_uri,logo_uri,redirect_uris,token_endpoint_auth_method,client_secret_hash,scopes,revoked_at")
    .eq("client_id", clientId)
    .maybeSingle();
  if (error || !data || data.revoked_at) return null;
  return data as OAuthClient;
}

export async function registerDynamicClient(
  request: Request,
  body: Record<string, unknown>,
) {
  const name = stringValue(body.client_name || "Cliente MCP", 120) || "Cliente MCP";
  const redirects = stringArray(body.redirect_uris);
  if (!redirects.length || redirects.length > 12) {
    return oauthError(
      "invalid_redirect_uri",
      "Informe de 1 a 12 redirect_uris válidas.",
    );
  }

  const normalizedRedirects = [...new Set(redirects.map(normalizeRedirectUri))];
  if (
    normalizedRedirects.some((item) => !item) ||
    normalizedRedirects.length !== redirects.length
  ) {
    return oauthError(
      "invalid_redirect_uri",
      "Uma ou mais redirect_uris não são aceitas.",
    );
  }

  const requestedGrantTypes = stringArray(body.grant_types);
  if (
    requestedGrantTypes.length &&
    requestedGrantTypes.some(
      (value) => !["authorization_code", "refresh_token"].includes(value),
    )
  ) {
    return oauthError("invalid_client_metadata", "grant_types não suportado.");
  }

  const requestedResponses = stringArray(body.response_types);
  if (
    requestedResponses.length &&
    requestedResponses.some((value) => value !== "code")
  ) {
    return oauthError(
      "invalid_client_metadata",
      "response_types não suportado.",
    );
  }

  const method = stringValue(
    body.token_endpoint_auth_method || "none",
    40,
  );
  if (!["none", "client_secret_basic", "client_secret_post"].includes(method)) {
    return oauthError(
      "invalid_client_metadata",
      "token_endpoint_auth_method não suportado.",
    );
  }

  const requestedScope = parseScope(body.scope);
  if (body.scope && !requestedScope) {
    return oauthError("invalid_scope", "Somente srrotas.read é permitido.");
  }

  const clientUri = body.client_uri ? validPublicUri(body.client_uri) : null;
  if (body.client_uri && !clientUri) {
    return oauthError("invalid_client_metadata", "client_uri inválida.");
  }

  const logoUri = body.logo_uri ? validPublicUri(body.logo_uri) : null;
  if (body.logo_uri && !logoUri) {
    return oauthError("invalid_client_metadata", "logo_uri inválida.");
  }

  const allowed = await allowSecurityAction(
    request,
    "mcp-dynamic-registration",
    normalizedRedirects[0] || name,
    30,
    3600,
  ).catch(() => false);

  if (!allowed) {
    return oauthError(
      "temporarily_unavailable",
      "Limite de registros OAuth atingido. Tente novamente mais tarde.",
      429,
    );
  }

  const clientId = `srmcpc_${newToken().slice(0, 40)}`;
  const secret =
    method === "none" ? null : `srmcps_${newToken()}`;

  const { data, error } = await adminSupabase()
    .from("mcp_oauth_clients")
    .insert({
      client_id: clientId,
      client_name: name,
      client_uri: clientUri,
      logo_uri: logoUri,
      redirect_uris: normalizedRedirects,
      token_endpoint_auth_method: method,
      client_secret_hash: secret ? sha256(secret) : null,
      client_secret_prefix: secret ? secret.slice(0, 14) : null,
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
      scopes: [MCP_SCOPE],
    })
    .select("client_id,client_name,client_uri,logo_uri,redirect_uris,token_endpoint_auth_method,grant_types,response_types,scopes,created_at")
    .single();

  if (error) {
    return oauthError(
      "server_error",
      "Não foi possível registrar o cliente MCP.",
      500,
    );
  }

  const issuedAt = Math.floor(
    new Date(String(data.created_at)).getTime() / 1000,
  );

  return oauthJson(
    {
      client_id: data.client_id,
      client_name: data.client_name,
      client_uri: data.client_uri,
      logo_uri: data.logo_uri,
      redirect_uris: data.redirect_uris,
      token_endpoint_auth_method: data.token_endpoint_auth_method,
      grant_types: data.grant_types,
      response_types: data.response_types,
      scope: MCP_SCOPE,
      client_id_issued_at: issuedAt,
      ...(secret
        ? {
            client_secret: secret,
            client_secret_expires_at: 0,
          }
        : {}),
    },
    201,
  );
}

export async function beginAuthorization(request: Request) {
  const url = new URL(request.url);
  const clientId = stringValue(url.searchParams.get("client_id"), 180);
  const responseType = stringValue(
    url.searchParams.get("response_type"),
    40,
  );
  const redirectUri = normalizeRedirectUri(
    url.searchParams.get("redirect_uri"),
  );
  const state = stringValue(url.searchParams.get("state"), 2048) || null;
  const challenge = stringValue(
    url.searchParams.get("code_challenge"),
    180,
  );
  const challengeMethod = stringValue(
    url.searchParams.get("code_challenge_method"),
    20,
  );
  const scope = parseScope(url.searchParams.get("scope"));
  const resource =
    stringValue(url.searchParams.get("resource"), 2048) ||
    mcpResourceUri(request);

  if (!clientId || responseType !== "code") {
    return oauthError(
      "invalid_request",
      "client_id e response_type=code são obrigatórios.",
    );
  }

  const client = await clientById(clientId);
  if (!client) return oauthError("invalid_client", "Cliente OAuth inválido.", 401);

  if (
    !redirectUri ||
    !client.redirect_uris.includes(redirectUri)
  ) {
    return oauthError(
      "invalid_redirect_uri",
      "redirect_uri não corresponde ao cliente registrado.",
    );
  }

  const safeRedirectUri = redirectUri;

  function redirectError(error: string, description: string) {
    const target = new URL(safeRedirectUri);
    target.searchParams.set("error", error);
    target.searchParams.set("error_description", description);
    if (state) target.searchParams.set("state", state);
    return Response.redirect(target, 302);
  }

  if (!scope) {
    return redirectError(
      "invalid_scope",
      "Somente srrotas.read é permitido.",
    );
  }

  if (
    challengeMethod !== "S256" ||
    !validPkceChallenge(challenge)
  ) {
    return redirectError(
      "invalid_request",
      "PKCE S256 é obrigatório.",
    );
  }

  if (resource !== mcpResourceUri(request)) {
    return redirectError(
      "invalid_target",
      "O recurso solicitado não é o MCP do Sr. Rotas.",
    );
  }

  const allowed = await allowSecurityAction(
    request,
    "mcp-oauth-authorize",
    clientId,
    120,
    3600,
  ).catch(() => false);

  if (!allowed) {
    return redirectError(
      "temporarily_unavailable",
      "Muitas tentativas de autorização. Tente novamente mais tarde.",
    );
  }

  const expiresAt = new Date(
    Date.now() + AUTH_REQUEST_TTL_SECONDS * 1000,
  ).toISOString();

  const { data, error } = await adminSupabase()
    .from("mcp_oauth_authorization_requests")
    .insert({
      client_id: clientId,
      redirect_uri: redirectUri,
      state,
      code_challenge: challenge,
      code_challenge_method: "S256",
      resource,
      scopes: scope,
      expires_at: expiresAt,
    })
    .select("id")
    .single();

  if (error) {
    return redirectError(
      "server_error",
      "Não foi possível iniciar a autorização.",
    );
  }

  const consent = new URL(
    `${mcpPublicBase(request)}/app/mcp/autorizar`,
  );
  consent.searchParams.set("request_id", String(data.id));
  return Response.redirect(consent, 302);
}

export async function authorizationDetails(request: Request) {
  const session = await authenticateBillingWeb(request);
  if (!session) return oauthJson({ error: "unauthorized" }, 401);

  const requestId =
    new URL(request.url).searchParams.get("request_id")?.trim() || "";
  if (!/^[0-9a-f-]{36}$/i.test(requestId)) {
    return oauthJson({ error: "invalid_request_id" }, 400);
  }

  const supabase = adminSupabase();
  const { data: authRequest, error } = await supabase
    .from("mcp_oauth_authorization_requests")
    .select("id,client_id,redirect_uri,scopes,status,expires_at,created_at")
    .eq("id", requestId)
    .maybeSingle();

  if (error || !authRequest) {
    return oauthJson({ error: "authorization_not_found" }, 404);
  }

  if (
    authRequest.status !== "pending" ||
    new Date(authRequest.expires_at).getTime() <= Date.now()
  ) {
    return oauthJson({ error: "authorization_expired" }, 410);
  }

  const client = await clientById(String(authRequest.client_id));
  if (!client) return oauthJson({ error: "client_unavailable" }, 410);

  const access = await resolveDriverAccess(session.driverId, null);

  return oauthJson({
    request_id: authRequest.id,
    client: {
      client_id: client.client_id,
      name: client.client_name,
      client_uri: client.client_uri,
      logo_uri: client.logo_uri,
    },
    redirect_uri: authRequest.redirect_uri,
    scopes: authRequest.scopes,
    access_state: access.state,
    can_mcp: access.effective.can_mcp,
    expires_at: authRequest.expires_at,
  });
}

function appendOAuthResult(
  redirectUri: string,
  params: Record<string, string | null | undefined>,
) {
  const target = new URL(redirectUri);
  for (const [key, value] of Object.entries(params)) {
    if (value) target.searchParams.set(key, value);
  }
  return target.toString();
}

export async function decideAuthorization(
  request: Request,
  body: Record<string, unknown>,
) {
  const session = await authenticateBillingWeb(request);
  if (!session) return oauthJson({ error: "unauthorized" }, 401);

  const requestId = stringValue(body.request_id, 80);
  const decision = stringValue(body.decision, 20);
  if (!/^[0-9a-f-]{36}$/i.test(requestId)) {
    return oauthJson({ error: "invalid_request_id" }, 400);
  }
  if (!["approve", "deny"].includes(decision)) {
    return oauthJson({ error: "invalid_decision" }, 400);
  }

  const supabase = adminSupabase();
  const now = new Date().toISOString();

  const claim = await supabase
    .from("mcp_oauth_authorization_requests")
    .update({
      status: decision === "approve" ? "approved" : "denied",
      driver_id: session.driverId,
      decided_at: now,
    })
    .eq("id", requestId)
    .eq("status", "pending")
    .gt("expires_at", now)
    .select("id,client_id,redirect_uri,state,code_challenge,resource,scopes")
    .maybeSingle();

  if (claim.error) {
    return oauthJson({ error: claim.error.message }, 500);
  }
  if (!claim.data) {
    return oauthJson({ error: "authorization_expired" }, 410);
  }

  const authRequest = claim.data;

  if (decision === "deny") {
    return oauthJson({
      redirect_to: appendOAuthResult(authRequest.redirect_uri, {
        error: "access_denied",
        error_description: "O usuário negou o acesso.",
        state: authRequest.state,
      }),
    });
  }

  const access = await resolveDriverAccess(session.driverId, null);
  if (!access.effective.can_mcp) {
    await supabase
      .from("mcp_oauth_authorization_requests")
      .update({ status: "denied" })
      .eq("id", requestId);

    return oauthJson(
      {
        error: "mcp_not_available",
        access_state: access.state,
        message: "O MCP não está disponível para esta conta neste momento.",
      },
      403,
    );
  }

  const grantResult = await supabase
    .from("mcp_oauth_grants")
    .upsert(
      {
        driver_id: session.driverId,
        client_id: authRequest.client_id,
        scopes: authRequest.scopes,
        approved_at: now,
        revoked_at: null,
        updated_at: now,
      },
      { onConflict: "driver_id,client_id" },
    )
    .select("id")
    .single();

  if (grantResult.error) {
    await supabase
      .from("mcp_oauth_authorization_requests")
      .update({ status: "pending", driver_id: null, decided_at: null })
      .eq("id", requestId);
    return oauthJson({ error: "grant_persistence_failed" }, 500);
  }

  const code = `srmcpcd_${newToken()}`;
  const expiresAt = new Date(
    Date.now() + AUTH_CODE_TTL_SECONDS * 1000,
  ).toISOString();

  const codeResult = await supabase
    .from("mcp_oauth_codes")
    .insert({
      request_id: requestId,
      driver_id: session.driverId,
      client_id: authRequest.client_id,
      code_hash: sha256(code),
      redirect_uri: authRequest.redirect_uri,
      code_challenge: authRequest.code_challenge,
      resource: authRequest.resource,
      scopes: authRequest.scopes,
      expires_at: expiresAt,
    });

  if (codeResult.error) {
    await supabase
      .from("mcp_oauth_authorization_requests")
      .update({ status: "pending", driver_id: null, decided_at: null })
      .eq("id", requestId);
    return oauthJson({ error: "authorization_code_failed" }, 500);
  }

  return oauthJson({
    redirect_to: appendOAuthResult(authRequest.redirect_uri, {
      code,
      state: authRequest.state,
    }),
  });
}

async function readOAuthBody(request: Request) {
  const contentType = request.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    const body = await request.json().catch(() => ({}));
    return body && typeof body === "object" && !Array.isArray(body)
      ? (body as Record<string, unknown>)
      : {};
  }

  const text = await request.text();
  const params = new URLSearchParams(text);
  return Object.fromEntries(params.entries()) as Record<string, unknown>;
}

function basicClient(request: Request) {
  const value = request.headers.get("authorization") || "";
  if (!/^Basic\s+/i.test(value)) return null;
  try {
    const decoded = Buffer.from(
      value.replace(/^Basic\s+/i, ""),
      "base64",
    ).toString("utf8");
    const separator = decoded.indexOf(":");
    if (separator < 0) return null;
    return {
      clientId: decodeURIComponent(decoded.slice(0, separator)),
      clientSecret: decodeURIComponent(decoded.slice(separator + 1)),
    };
  } catch {
    return null;
  }
}

async function authenticateOAuthClient(
  request: Request,
  body: Record<string, unknown>,
) {
  const basic = basicClient(request);
  const bodyClientId = stringValue(body.client_id, 180);
  const clientId = basic?.clientId || bodyClientId;
  if (!clientId) return null;

  const client = await clientById(clientId);
  if (!client) return null;

  if (client.token_endpoint_auth_method === "none") {
    if (basic) return null;
    return client;
  }

  const supplied =
    client.token_endpoint_auth_method === "client_secret_basic"
      ? basic?.clientSecret || ""
      : stringValue(body.client_secret, 300);

  if (!supplied || !client.client_secret_hash) return null;

  const actual = sha256(supplied);
  if (!safeEqual(actual, client.client_secret_hash)) return null;
  return client;
}

async function activeGrant(driverId: string, clientId: string) {
  const { data, error } = await adminSupabase()
    .from("mcp_oauth_grants")
    .select("id,driver_id,client_id,scopes,approved_at,last_used_at,revoked_at")
    .eq("driver_id", driverId)
    .eq("client_id", clientId)
    .is("revoked_at", null)
    .maybeSingle();
  return error ? null : data;
}

async function issueTokenPair(params: {
  driverId: string;
  clientId: string;
  grantId: string;
  scopes: string[];
  refreshExpiresAt?: string;
  rotatedFromId?: string | null;
}) {
  const accessToken = `srmcpo_${newToken()}`;
  const refreshToken = `srmcpr_${newToken()}`;
  const now = Date.now();
  const accessExpiresAt = new Date(
    now + MCP_ACCESS_TTL_SECONDS * 1000,
  ).toISOString();
  const refreshExpiresAt =
    params.refreshExpiresAt ||
    new Date(now + MCP_REFRESH_TTL_SECONDS * 1000).toISOString();

  const { data, error } = await adminSupabase()
    .from("mcp_oauth_tokens")
    .insert({
      driver_id: params.driverId,
      client_id: params.clientId,
      grant_id: params.grantId,
      access_token_hash: sha256(accessToken),
      access_token_prefix: accessToken.slice(0, 14),
      refresh_token_hash: sha256(refreshToken),
      refresh_token_prefix: refreshToken.slice(0, 14),
      scopes: params.scopes,
      access_expires_at: accessExpiresAt,
      refresh_expires_at: refreshExpiresAt,
      rotated_from_id: params.rotatedFromId || null,
    })
    .select("id")
    .single();

  if (error) throw new Error(error.message);

  return {
    id: String(data.id),
    accessToken,
    refreshToken,
    expiresIn: MCP_ACCESS_TTL_SECONDS,
    refreshExpiresAt,
  };
}

export async function exchangeToken(request: Request) {
  const body = await readOAuthBody(request);
  const grantType = stringValue(body.grant_type, 60);
  const client = await authenticateOAuthClient(request, body);
  if (!client) return oauthError("invalid_client", "Cliente inválido.", 401);

  if (grantType === "authorization_code") {
    const code = stringValue(body.code, 300);
    const redirectUri = normalizeRedirectUri(body.redirect_uri);
    const verifier = stringValue(body.code_verifier, 180);
    const resource =
      stringValue(body.resource, 2048) || mcpResourceUri(request);

    if (!code || !redirectUri || !validVerifier(verifier)) {
      return oauthError(
        "invalid_request",
        "code, redirect_uri e code_verifier são obrigatórios.",
      );
    }

    const now = new Date().toISOString();
    const lookup = await adminSupabase()
      .from("mcp_oauth_codes")
      .select("id,driver_id,client_id,redirect_uri,code_challenge,resource,scopes,expires_at,used_at")
      .eq("code_hash", sha256(code))
      .eq("client_id", client.client_id)
      .is("used_at", null)
      .gt("expires_at", now)
      .maybeSingle();

    if (lookup.error || !lookup.data) {
      return oauthError(
        "invalid_grant",
        "Código inválido, expirado ou já utilizado.",
      );
    }

    if (
      lookup.data.redirect_uri !== redirectUri ||
      lookup.data.resource !== resource ||
      !safeEqual(
        base64urlSha256(verifier),
        String(lookup.data.code_challenge),
      )
    ) {
      return oauthError("invalid_grant", "PKCE ou redirect_uri inválido.");
    }

    const claim = await adminSupabase()
      .from("mcp_oauth_codes")
      .update({ used_at: now })
      .eq("id", lookup.data.id)
      .is("used_at", null)
      .gt("expires_at", now)
      .select("id")
      .maybeSingle();

    if (claim.error || !claim.data) {
      return oauthError(
        "invalid_grant",
        "Código já utilizado por outra tentativa.",
      );
    }

    const access = await resolveDriverAccess(
      String(lookup.data.driver_id),
      null,
    );
    if (!access.effective.can_mcp) {
      return oauthError(
        "access_denied",
        "O MCP não está disponível para esta conta.",
        403,
      );
    }

    const grant = await activeGrant(
      String(lookup.data.driver_id),
      client.client_id,
    );
    if (!grant) {
      return oauthError("invalid_grant", "Autorização revogada.");
    }

    try {
      const pair = await issueTokenPair({
        driverId: String(lookup.data.driver_id),
        clientId: client.client_id,
        grantId: String(grant.id),
        scopes: lookup.data.scopes || [MCP_SCOPE],
      });

      return oauthJson({
        access_token: pair.accessToken,
        refresh_token: pair.refreshToken,
        token_type: "Bearer",
        expires_in: pair.expiresIn,
        scope: (lookup.data.scopes || [MCP_SCOPE]).join(" "),
      });
    } catch {
      await adminSupabase()
        .from("mcp_oauth_codes")
        .update({ used_at: null })
        .eq("id", lookup.data.id)
        .eq("used_at", now);
      return oauthError("server_error", "Falha ao emitir token.", 500);
    }
  }

  if (grantType === "refresh_token") {
    const refreshToken = stringValue(body.refresh_token, 300);
    if (!refreshToken) {
      return oauthError("invalid_request", "refresh_token é obrigatório.");
    }

    const now = new Date().toISOString();
    const lookup = await adminSupabase()
      .from("mcp_oauth_tokens")
      .select("id,driver_id,grant_id,scopes,refresh_expires_at,revoked_at")
      .eq("refresh_token_hash", sha256(refreshToken))
      .eq("client_id", client.client_id)
      .is("revoked_at", null)
      .gt("refresh_expires_at", now)
      .maybeSingle();

    if (lookup.error || !lookup.data) {
      return oauthError(
        "invalid_grant",
        "Refresh token inválido, expirado ou já utilizado.",
      );
    }

    const grant = await activeGrant(
      String(lookup.data.driver_id),
      client.client_id,
    );
    if (!grant || String(grant.id) !== String(lookup.data.grant_id)) {
      return oauthError("invalid_grant", "Autorização revogada.");
    }

    const access = await resolveDriverAccess(
      String(lookup.data.driver_id),
      null,
    );
    if (!access.effective.can_mcp) {
      return oauthError(
        "access_denied",
        "O MCP não está disponível para esta conta.",
        403,
      );
    }

    const old = await adminSupabase()
      .from("mcp_oauth_tokens")
      .update({
        revoked_at: now,
        revoked_reason: "rotated",
      })
      .eq("id", lookup.data.id)
      .is("revoked_at", null)
      .gt("refresh_expires_at", now)
      .select("id")
      .maybeSingle();

    if (old.error || !old.data) {
      return oauthError(
        "invalid_grant",
        "Refresh token já utilizado por outra tentativa.",
      );
    }

    try {
      const pair = await issueTokenPair({
        driverId: String(lookup.data.driver_id),
        clientId: client.client_id,
        grantId: String(lookup.data.grant_id),
        scopes: lookup.data.scopes || [MCP_SCOPE],
        refreshExpiresAt: String(lookup.data.refresh_expires_at),
        rotatedFromId: String(lookup.data.id),
      });

      return oauthJson({
        access_token: pair.accessToken,
        refresh_token: pair.refreshToken,
        token_type: "Bearer",
        expires_in: pair.expiresIn,
        scope: (lookup.data.scopes || [MCP_SCOPE]).join(" "),
      });
    } catch {
      await adminSupabase()
        .from("mcp_oauth_tokens")
        .update({ revoked_at: null, revoked_reason: null })
        .eq("id", lookup.data.id)
        .eq("revoked_at", now)
        .eq("revoked_reason", "rotated");
      return oauthError("server_error", "Falha ao renovar token.", 500);
    }
  }

  return oauthError(
    "unsupported_grant_type",
    "Use authorization_code ou refresh_token.",
  );
}

export async function revokeOAuthToken(request: Request) {
  const body = await readOAuthBody(request);
  const client = await authenticateOAuthClient(request, body);
  if (!client) return oauthError("invalid_client", "Cliente inválido.", 401);

  const token = stringValue(body.token, 300);
  if (!token) return oauthJson({});

  const hash = sha256(token);
  await adminSupabase()
    .from("mcp_oauth_tokens")
    .update({
      revoked_at: new Date().toISOString(),
      revoked_reason: "oauth_revocation",
    })
    .eq("client_id", client.client_id)
    .is("revoked_at", null)
    .or(`access_token_hash.eq.${hash},refresh_token_hash.eq.${hash}`);

  // RFC 7009: unknown tokens also receive 200.
  return oauthJson({});
}

export async function userOAuthConnections(request: Request) {
  const session = await authenticateBillingWeb(request);
  if (!session) return oauthJson({ error: "unauthorized" }, 401);

  const supabase = adminSupabase();
  const grants = await supabase
    .from("mcp_oauth_grants")
    .select("id,client_id,scopes,approved_at,last_used_at,created_at")
    .eq("driver_id", session.driverId)
    .is("revoked_at", null)
    .order("approved_at", { ascending: false });

  if (grants.error) return oauthJson({ error: grants.error.message }, 500);

  const rows = grants.data || [];
  const ids = [...new Set(rows.map((row: any) => String(row.client_id)))];
  let clients: any[] = [];
  if (ids.length) {
    const result = await supabase
      .from("mcp_oauth_clients")
      .select("client_id,client_name,client_uri,logo_uri,revoked_at")
      .in("client_id", ids);
    if (result.error) return oauthJson({ error: result.error.message }, 500);
    clients = result.data || [];
  }

  const clientMap = new Map(
    clients.map((client: any) => [String(client.client_id), client]),
  );

  const access = await resolveDriverAccess(session.driverId, null);

  return oauthJson({
    endpoint: mcpResourceUri(request),
    access_state: access.state,
    can_connect: access.effective.can_mcp,
    connections: rows.map((row: any) => {
      const client = clientMap.get(String(row.client_id));
      return {
        grant_id: row.id,
        client_id: row.client_id,
        client_name: client?.client_name || "Cliente MCP",
        client_uri: client?.client_uri || null,
        logo_uri: client?.logo_uri || null,
        scopes: row.scopes || [MCP_SCOPE],
        approved_at: row.approved_at,
        last_used_at: row.last_used_at,
        client_revoked: Boolean(client?.revoked_at),
      };
    }),
  });
}

export async function revokeUserOAuthConnection(
  request: Request,
  grantId: string,
) {
  const session = await authenticateBillingWeb(request);
  if (!session) return oauthJson({ error: "unauthorized" }, 401);
  if (!/^[0-9a-f-]{36}$/i.test(grantId)) {
    return oauthJson({ error: "invalid_grant_id" }, 400);
  }

  const now = new Date().toISOString();
  const supabase = adminSupabase();

  const grant = await supabase
    .from("mcp_oauth_grants")
    .update({ revoked_at: now, updated_at: now })
    .eq("id", grantId)
    .eq("driver_id", session.driverId)
    .is("revoked_at", null)
    .select("id")
    .maybeSingle();

  if (grant.error) return oauthJson({ error: grant.error.message }, 500);
  if (!grant.data) return oauthJson({ error: "not_found" }, 404);

  await supabase
    .from("mcp_oauth_tokens")
    .update({ revoked_at: now, revoked_reason: "user_revoked" })
    .eq("grant_id", grantId)
    .eq("driver_id", session.driverId)
    .is("revoked_at", null);

  return oauthJson({ ok: true });
}
