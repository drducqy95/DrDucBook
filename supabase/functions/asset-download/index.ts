import manifest from "../../artifacts/hf-artifacts-manifest.json" with { type: "json" };
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import {
  AssetTicketError,
  buildHfResolveUrl,
  findArtifact,
  jsonResponse,
  parseRangeHeader,
  ticketIdHash,
  ticketPublicArtifact,
  verifyAssetTicket,
} from "../_shared/asset_ticket.mjs";
import { optionsResponse, withCors } from "../_shared/http.mjs";

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return optionsResponse();
  if (request.method !== "GET" && request.method !== "HEAD") {
    return withCors(jsonResponse(405, { code: "method_not_allowed" }));
  }

  try {
    const url = new URL(request.url);
    const artifactId = url.searchParams.get("artifactId");
    const ticket = url.searchParams.get("ticket")
      ?? request.headers.get("x-drducbook-asset-ticket")
      ?? "";
    const payload = await verifyAssetTicket({
      ticket,
      secret: requiredEnv("ASSET_TICKET_SECRET"),
      expectedArtifactId: artifactId ?? undefined,
    });
    const artifact = findArtifact(manifest, payload.artifactId);

    if (artifact.deliveryClass === "storage_mirror_required") {
      return withCors(jsonResponse(409, {
        code: "storage_mirror_required",
        artifact: ticketPublicArtifact(artifact),
      }));
    }

    await consumeTicket(ticket, artifact.id);
    const range = parseRangeHeader(request.headers.get("range"), artifact.sizeBytes);
    const hfResponse = await fetch(buildHfResolveUrl(artifact), {
      method: request.method,
      headers: buildHfHeaders(range?.header),
    });
    if (!hfResponse.ok && hfResponse.status !== 206) {
      return withCors(jsonResponse(hfResponse.status, { code: "hf_fetch_failed" }));
    }

    const headers = new Headers();
    copyHeader(hfResponse.headers, headers, "content-type");
    copyHeader(hfResponse.headers, headers, "content-length");
    copyHeader(hfResponse.headers, headers, "content-range");
    copyHeader(hfResponse.headers, headers, "etag");
    headers.set("accept-ranges", "bytes");
    headers.set("x-drducbook-sha256", artifact.sha256);
    headers.set("x-drducbook-size", String(artifact.sizeBytes));

    return withCors(new Response(request.method === "HEAD" ? null : hfResponse.body, {
      status: hfResponse.status,
      headers,
    }));
  } catch (error) {
    return withCors(toErrorResponse(error));
  }
});

async function consumeTicket(ticket: string, artifactId: string) {
  const supabase = createClient(
    requiredEnv("SUPABASE_URL"),
    requiredEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
      global: {
        headers: { "x-application-name": "drducbook-asset-download" },
      },
    },
  );
  const { data, error } = await supabase.rpc("consume_artifact_ticket", {
    p_id_hash: await ticketIdHash(ticket),
    p_artifact_id: artifactId,
  });
  if (error) throw new AssetTicketError(500, "ticket_consume_failed", "Could not consume ticket");
  if (!Array.isArray(data) || data.length !== 1) {
    throw new AssetTicketError(409, "ticket_replayed_or_expired", "Ticket was already used or expired");
  }
}

function buildHfHeaders(rangeHeader?: string) {
  const headers = new Headers({
    authorization: `Bearer ${requiredEnv("HF_READ_TOKEN")}`,
  });
  if (rangeHeader) headers.set("range", rangeHeader);
  return headers;
}

function copyHeader(from: Headers, to: Headers, name: string) {
  const value = from.get(name);
  if (value) to.set(name, value);
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new AssetTicketError(500, `missing_${name.toLowerCase()}`, "Function secret is not configured");
  return value;
}

function toErrorResponse(error: unknown): Response {
  if (error instanceof AssetTicketError) {
    return jsonResponse(error.status, { code: error.code, message: error.message });
  }
  return jsonResponse(500, { code: "internal_error", message: "Asset download request failed" });
}
