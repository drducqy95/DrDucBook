export const DEFAULT_TICKET_TTL_SECONDS = 10 * 60;
export const MAX_TICKET_TTL_SECONDS = 15 * 60;
export const HF_DATASET_RESOLVE_BASE = "https://huggingface.co/datasets";

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

export function normalizeArtifactId(value) {
  const artifactId = String(value ?? "").trim();
  if (!/^[a-z0-9][a-z0-9._-]{1,127}$/u.test(artifactId)) {
    throw new AssetTicketError(400, "invalid_artifact_id", "Invalid artifact id");
  }
  return artifactId;
}

export function findArtifact(manifest, artifactId) {
  const normalized = normalizeArtifactId(artifactId);
  const artifact = manifest.artifacts?.find((item) => item.id === normalized);
  if (!artifact) {
    throw new AssetTicketError(404, "artifact_not_found", "Artifact is not allow-listed");
  }
  validateArtifact(artifact);
  return artifact;
}

export function validateArtifact(artifact) {
  if (artifact.hfRepo !== "Drduc/Legadofork") {
    throw new AssetTicketError(500, "bad_manifest_repo", "Unexpected artifact repository");
  }
  if (!artifact.hfPath || artifact.hfPath.startsWith("/") || artifact.hfPath.includes("\\") || artifact.hfPath.includes("..")) {
    throw new AssetTicketError(500, "bad_manifest_path", "Unsafe artifact path");
  }
  if (!Number.isSafeInteger(artifact.sizeBytes) || artifact.sizeBytes <= 0) {
    throw new AssetTicketError(500, "bad_manifest_size", "Invalid artifact size");
  }
  if (!/^[a-f0-9]{64}$/u.test(String(artifact.sha256))) {
    throw new AssetTicketError(500, "bad_manifest_hash", "Invalid artifact hash");
  }
  if (!["hf_proxy", "storage_mirror_required"].includes(artifact.deliveryClass)) {
    throw new AssetTicketError(500, "bad_manifest_delivery", "Invalid delivery class");
  }
}

export function buildHfResolveUrl(artifact) {
  validateArtifact(artifact);
  const repoPath = artifact.hfRepo.split("/").map(encodeURIComponent).join("/");
  const revision = encodeURIComponent(artifact.hfRevision || "main");
  const path = artifact.hfPath.split("/").map(encodeURIComponent).join("/");
  return `${HF_DATASET_RESOLVE_BASE}/${repoPath}/resolve/${revision}/${path}`;
}

export function parseRangeHeader(rangeHeader, sizeBytes) {
  if (!rangeHeader) return null;
  if (!Number.isSafeInteger(sizeBytes) || sizeBytes <= 0) {
    throw new AssetTicketError(416, "invalid_size", "Invalid artifact size for Range");
  }
  const value = rangeHeader.trim();
  if (value.includes(",")) {
    throw new AssetTicketError(416, "multi_range_not_supported", "Multiple ranges are not supported");
  }
  const match = /^bytes=(\d*)-(\d*)$/u.exec(value);
  if (!match) {
    throw new AssetTicketError(416, "invalid_range", "Invalid Range header");
  }
  const [, startRaw, endRaw] = match;
  if (!startRaw && !endRaw) {
    throw new AssetTicketError(416, "invalid_range", "Invalid Range header");
  }
  if (!startRaw) {
    const suffixLength = Number(endRaw);
    if (!Number.isSafeInteger(suffixLength) || suffixLength <= 0) {
      throw new AssetTicketError(416, "invalid_range", "Invalid suffix Range");
    }
    const start = Math.max(sizeBytes - suffixLength, 0);
    return { start, end: sizeBytes - 1, header: `bytes=${start}-${sizeBytes - 1}` };
  }
  const start = Number(startRaw);
  const end = endRaw ? Number(endRaw) : sizeBytes - 1;
  if (
    !Number.isSafeInteger(start) ||
    !Number.isSafeInteger(end) ||
    start < 0 ||
    end < start ||
    start >= sizeBytes
  ) {
    throw new AssetTicketError(416, "invalid_range", "Unsatisfiable Range header");
  }
  const clampedEnd = Math.min(end, sizeBytes - 1);
  return { start, end: clampedEnd, header: `bytes=${start}-${clampedEnd}` };
}

export async function createAssetTicket({
  userId,
  artifactId,
  secret,
  now = new Date(),
  ttlSeconds = DEFAULT_TICKET_TTL_SECONDS,
  nonce = crypto.randomUUID(),
}) {
  if (!userId || typeof userId !== "string") {
    throw new AssetTicketError(401, "missing_user", "Authenticated user is required");
  }
  const normalizedArtifactId = normalizeArtifactId(artifactId);
  const boundedTtl = Number(ttlSeconds);
  if (!Number.isSafeInteger(boundedTtl) || boundedTtl <= 0 || boundedTtl > MAX_TICKET_TTL_SECONDS) {
    throw new AssetTicketError(400, "invalid_ttl", "Invalid ticket TTL");
  }
  const issuedAt = Math.floor(now.getTime() / 1000);
  const payload = {
    v: 1,
    userId,
    artifactId: normalizedArtifactId,
    iat: issuedAt,
    exp: issuedAt + boundedTtl,
    nonce,
  };
  const payloadBase64 = base64UrlEncode(textEncoder.encode(JSON.stringify(payload)));
  const signature = await hmacSha256(secret, payloadBase64);
  return `${payloadBase64}.${base64UrlEncode(signature)}`;
}

export async function verifyAssetTicket({
  ticket,
  secret,
  now = new Date(),
  expectedArtifactId,
}) {
  const [payloadBase64, signatureBase64, extra] = String(ticket ?? "").split(".");
  if (!payloadBase64 || !signatureBase64 || extra) {
    throw new AssetTicketError(401, "invalid_ticket", "Invalid ticket");
  }
  const signature = base64UrlDecode(signatureBase64);
  const ok = await verifyHmacSha256(secret, payloadBase64, signature);
  if (!ok) {
    throw new AssetTicketError(401, "invalid_ticket_signature", "Invalid ticket signature");
  }
  let payload;
  try {
    payload = JSON.parse(textDecoder.decode(base64UrlDecode(payloadBase64)));
  } catch {
    throw new AssetTicketError(401, "invalid_ticket_payload", "Invalid ticket payload");
  }
  if (payload.v !== 1 || !payload.userId || !payload.artifactId || !payload.exp || !payload.nonce) {
    throw new AssetTicketError(401, "invalid_ticket_payload", "Invalid ticket payload");
  }
  normalizeArtifactId(payload.artifactId);
  if (expectedArtifactId && payload.artifactId !== normalizeArtifactId(expectedArtifactId)) {
    throw new AssetTicketError(403, "ticket_artifact_mismatch", "Ticket does not match artifact");
  }
  const nowSeconds = Math.floor(now.getTime() / 1000);
  if (payload.exp <= nowSeconds) {
    throw new AssetTicketError(401, "ticket_expired", "Ticket expired");
  }
  return payload;
}

export async function ticketIdHash(ticket) {
  const digest = await crypto.subtle.digest("SHA-256", textEncoder.encode(String(ticket)));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function jsonResponse(status, body, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      ...headers,
    },
  });
}

export function ticketPublicArtifact(artifact) {
  return {
    id: artifact.id,
    fileName: artifact.fileName,
    sizeBytes: artifact.sizeBytes,
    sha256: artifact.sha256,
    deliveryClass: artifact.deliveryClass,
    inventoryState: artifact.inventoryState,
  };
}

export class AssetTicketError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = "AssetTicketError";
    this.status = status;
    this.code = code;
  }
}

async function hmacSha256(secret, message) {
  const key = await crypto.subtle.importKey(
    "raw",
    textEncoder.encode(requireSecret(secret)),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, textEncoder.encode(message)));
}

async function verifyHmacSha256(secret, message, signature) {
  const key = await crypto.subtle.importKey(
    "raw",
    textEncoder.encode(requireSecret(secret)),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"],
  );
  return crypto.subtle.verify("HMAC", key, signature, textEncoder.encode(message));
}

function requireSecret(secret) {
  if (!secret || String(secret).length < 32) {
    throw new AssetTicketError(500, "missing_ticket_secret", "Ticket secret is not configured");
  }
  return String(secret);
}

function base64UrlEncode(bytes) {
  const binary = String.fromCharCode(...bytes);
  const base64 = typeof btoa === "function"
    ? btoa(binary)
    : Buffer.from(bytes).toString("base64");
  return base64.replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function base64UrlDecode(value) {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/")
    .padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = typeof atob === "function"
    ? atob(padded)
    : Buffer.from(padded, "base64").toString("binary");
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}
