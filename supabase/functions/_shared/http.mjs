export const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, content-type, range",
  "access-control-allow-methods": "GET, HEAD, POST, OPTIONS",
  "access-control-expose-headers": "accept-ranges, content-length, content-range, content-type, etag, x-drducbook-sha256, x-drducbook-size",
  "vary": "origin",
};

export function withCors(response) {
  const headers = new Headers(response.headers);
  Object.entries(corsHeaders).forEach(([key, value]) => headers.set(key, value));
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

export function optionsResponse() {
  return new Response(null, { status: 204, headers: corsHeaders });
}

export function decodeJwtSubject(authorizationHeader) {
  const match = /^Bearer\s+(.+)$/iu.exec(authorizationHeader ?? "");
  if (!match) return "";
  const parts = match[1].split(".");
  if (parts.length < 2) return "";
  try {
    const padded = parts[1].replaceAll("-", "+").replaceAll("_", "/")
      .padEnd(Math.ceil(parts[1].length / 4) * 4, "=");
    const binary = typeof atob === "function"
      ? atob(padded)
      : Buffer.from(padded, "base64").toString("binary");
    return JSON.parse(binary).sub ?? "";
  } catch {
    return "";
  }
}
