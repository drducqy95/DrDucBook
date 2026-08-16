import manifest from "../../artifacts/hf-artifacts-manifest.json" with { type: "json" };
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import {
  AssetTicketError,
  createAssetTicket,
  findArtifact,
  jsonResponse,
  ticketIdHash,
  ticketPublicArtifact,
} from "../_shared/asset_ticket.mjs";
import { corsHeaders, decodeJwtSubject, optionsResponse, withCors } from "../_shared/http.mjs";

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return optionsResponse();
  if (request.method !== "POST") {
    return withCors(jsonResponse(405, { code: "method_not_allowed" }));
  }

  try {
    const userId = decodeJwtSubject(request.headers.get("authorization"));
    if (!userId) throw new AssetTicketError(401, "missing_user", "Authenticated user is required");

    const body = await request.json().catch(() => ({}));
    const artifact = findArtifact(manifest, body.artifactId);
    if (String(artifact.inventoryState ?? "").includes("license_pending")) {
      throw new AssetTicketError(451, "license_review_required", "Artifact is blocked until license review is complete");
    }

    const ticketSecret = requiredEnv("ASSET_TICKET_SECRET");
    const now = new Date();
    const ticket = await createAssetTicket({
      userId,
      artifactId: artifact.id,
      secret: ticketSecret,
      now,
    });
    const idHash = await ticketIdHash(ticket);
    const expiresAt = new Date(now.getTime() + 10 * 60 * 1000).toISOString();

    const supabase = adminClient();
    await enforceRateLimit(supabase, userId);
    const { error } = await supabase.from("artifact_tickets").insert({
      id_hash: idHash,
      user_id: userId,
      artifact_id: artifact.id,
      expires_at: expiresAt,
    });
    if (error) throw new AssetTicketError(500, "ticket_persist_failed", "Could not persist ticket");

    return withCors(jsonResponse(200, {
      ticket,
      expiresAt,
      artifact: ticketPublicArtifact(artifact),
    }));
  } catch (error) {
    return withCors(toErrorResponse(error));
  }
});

function adminClient() {
  return createClient(
    requiredEnv("SUPABASE_URL"),
    requiredEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
      global: {
        headers: { "x-application-name": "drducbook-asset-ticket" },
      },
    },
  );
}

async function enforceRateLimit(
  supabase: ReturnType<typeof createClient>,
  userId: string,
) {
  const windowStart = new Date(Date.now() - 60 * 1000).toISOString();
  const { count, error } = await supabase
    .from("artifact_tickets")
    .select("id_hash", { count: "exact", head: true })
    .eq("user_id", userId)
    .gte("created_at", windowStart);
  if (error) throw new AssetTicketError(500, "rate_limit_check_failed", "Could not check rate limit");
  if ((count ?? 0) >= 30) {
    throw new AssetTicketError(429, "rate_limited", "Too many asset ticket requests");
  }
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
  return jsonResponse(500, { code: "internal_error", message: "Asset ticket request failed" }, corsHeaders);
}
