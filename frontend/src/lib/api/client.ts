import type {
  SignupInput,
  SignupResponse,
  LoginInput,
  LoginResponse,
  LeaderboardEntry,
  WaitlistEntry,
  BulkActionResponse,
  LeaderboardWindow,
  EntryStatus,
  PublicStatusResponse,
  ProfileResponse,
  VerifyResponse,
  FlaggedReferrer,
  AuditLogEntry,
} from "./types";
import {
  SignupResponseSchema,
  LoginResponseSchema,
  WaitlistEntrySchema,
  LeaderboardEntrySchema,
  BulkActionResponseSchema,
  PublicStatusResponseSchema,
  ProfileResponseSchema,
  VerifyResponseSchema,
  FlaggedReferrerSchema,
  AuditLogEntrySchema,
} from "./types";
import { z } from "zod";

const INGESTION_BASE =
  (import.meta.env["VITE_INGESTION_BASE"] as string | undefined) ??
  "/api/public";
const ADMIN_BASE =
  (import.meta.env["VITE_ADMIN_BASE"] as string | undefined) ?? "/api/admin";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly errors?: string[],
    public readonly correlationId?: string,
    public readonly retryAfter?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function getToken(): string | null {
  return sessionStorage.getItem("admin_token");
}

function dispatchAuthExpired(): void {
  window.dispatchEvent(new CustomEvent("auth:expired"));
}

async function parseErrorBody(
  res: Response,
): Promise<{ message: string; errors?: string[]; correlationId?: string }> {
  try {
    const body = (await res.json()) as unknown;
    if (typeof body === "object" && body !== null) {
      const obj = body as Record<string, unknown>;
      // Handle Spring's {"error":"..."} format in addition to our {"message":"..."}
      const msg = typeof obj["message"] === "string"
        ? obj["message"]
        : typeof obj["error"] === "string"
          ? obj["error"]
          : res.statusText;
      return {
        message: msg,
        errors: Array.isArray(obj["errors"])
          ? (obj["errors"] as string[])
          : undefined,
        correlationId:
          typeof obj["correlationId"] === "string"
            ? obj["correlationId"]
            : undefined,
      };
    }
  } catch {
    // body not JSON
  }
  return { message: res.statusText || `HTTP ${String(res.status)}` };
}

async function request(
  url: string,
  options: RequestInit & { isAdmin?: boolean } = {},
): Promise<{ data: unknown; status: number }> {
  const { isAdmin = false, ...fetchOptions } = options;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(fetchOptions.headers as Record<string, string> | undefined),
  };

  if (isAdmin) {
    const token = getToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }

  let res: Response;
  try {
    res = await fetch(url, { ...fetchOptions, headers });
  } catch (err) {
    throw new ApiError(0, err instanceof Error ? err.message : "Network error");
  }

  if (res.status === 401 && isAdmin) {
    dispatchAuthExpired();
    throw new ApiError(401, "Session expired");
  }

  if (!res.ok) {
    const retryAfter = res.headers.get("Retry-After");
    const parsed = await parseErrorBody(res);
    throw new ApiError(
      res.status,
      parsed.message,
      parsed.errors,
      parsed.correlationId,
      retryAfter !== null ? parseInt(retryAfter, 10) : undefined,
    );
  }

  const text = await res.text();
  const data: unknown = text.length > 0 ? (JSON.parse(text) as unknown) : null;
  return { data, status: res.status };
}

// ── Public endpoints ──────────────────────────────────────────────────────────

export async function signup(input: SignupInput): Promise<SignupResponse> {
  const body: Record<string, unknown> = {
    email: input.email,
  };
  // Only include `website` when non-empty: backend treats ANY non-null value
  // (including "") as a filled honeypot and returns a fake 00000000 response.
  if (input.website && input.website.length > 0) body["website"] = input.website;
  if (input.name && input.name.length > 0) body["name"] = input.name;
  if (input.company && input.company.length > 0) body["company"] = input.company;
  body["referralCode"] =
    input.referralCode && input.referralCode.length > 0
      ? input.referralCode
      : null;

  const { data } = await request(`${INGESTION_BASE}/signup`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return SignupResponseSchema.parse(data);
}

export async function getLeaderboard(
  window: LeaderboardWindow,
): Promise<LeaderboardEntry[]> {
  const { data } = await request(
    `${INGESTION_BASE}/leaderboard?window=${window}`,
  );
  return z.array(LeaderboardEntrySchema).parse(data);
}

export async function getProfile(email: string): Promise<ProfileResponse> {
  const { data } = await request(
    `${INGESTION_BASE}/profile?email=${encodeURIComponent(email)}`,
  );
  return ProfileResponseSchema.parse(data);
}

export async function getEntryStatus(email: string): Promise<PublicStatusResponse> {
  const { data } = await request(
    `${ADMIN_BASE}/public/status?email=${encodeURIComponent(email)}`,
  );
  return PublicStatusResponseSchema.parse(data);
}

export async function verifyEmail(token: string): Promise<VerifyResponse> {
  const { data } = await request(
    `${INGESTION_BASE}/verify?token=${encodeURIComponent(token)}`,
    { method: "POST" },
  );
  return VerifyResponseSchema.parse(data);
}

export async function resendVerification(email: string): Promise<void> {
  await request(
    `${INGESTION_BASE}/resend-verification?email=${encodeURIComponent(email)}`,
    { method: "POST" },
  );
}

// ── Admin endpoints ───────────────────────────────────────────────────────────

export async function adminLogin(input: LoginInput): Promise<LoginResponse> {
  const { data } = await request(`${ADMIN_BASE}/auth/login`, {
    method: "POST",
    body: JSON.stringify(input),
    isAdmin: false,
  });
  return LoginResponseSchema.parse(data);
}

export async function getEntries(status?: EntryStatus): Promise<WaitlistEntry[]> {
  const url = status
    ? `${ADMIN_BASE}/entries?status=${status}`
    : `${ADMIN_BASE}/entries`;
  const { data } = await request(url, { isAdmin: true });
  return z.array(WaitlistEntrySchema).parse(data);
}

export async function updateEntryStatus(
  id: number,
  status: EntryStatus,
): Promise<void> {
  await request(`${ADMIN_BASE}/entries/${String(id)}?status=${status}`, {
    method: "PATCH",
    isAdmin: true,
  });
}

export async function bulkUpdateStatus(
  ids: number[],
  newStatus: EntryStatus,
): Promise<{ data: BulkActionResponse; status: number }> {
  const result = await request(`${ADMIN_BASE}/entries/bulk`, {
    method: "POST",
    body: JSON.stringify({ ids, newStatus }),
    isAdmin: true,
  });
  return {
    data: BulkActionResponseSchema.parse(result.data),
    status: result.status,
  };
}

// ── Admin fraud endpoints ─────────────────────────────────────────────────────

export async function getFlaggedReferrers(): Promise<FlaggedReferrer[]> {
  const { data } = await request(`${ADMIN_BASE}/flagged-referrers`, { isAdmin: true });
  return z.array(FlaggedReferrerSchema).parse(data);
}

export async function getAuditLog(params?: {
  email?: string;
  eventType?: string;
  startDate?: string;
  endDate?: string;
}): Promise<AuditLogEntry[]> {
  const qs = new URLSearchParams();
  if (params?.email)     qs.set("email", params.email);
  if (params?.eventType) qs.set("eventType", params.eventType);
  if (params?.startDate) qs.set("startDate", params.startDate);
  if (params?.endDate)   qs.set("endDate", params.endDate);
  const url = `${ADMIN_BASE}/audit-log${qs.toString() ? `?${qs.toString()}` : ""}`;
  const { data } = await request(url, { isAdmin: true });
  return z.array(AuditLogEntrySchema).parse(data);
}

export async function blacklistReferrer(email: string, reason: string): Promise<void> {
  await request(`${ADMIN_BASE}/referrers/${encodeURIComponent(email)}/blacklist`, {
    method: "POST",
    body: JSON.stringify({ reason }),
    isAdmin: true,
  });
}

export async function whitelistReferrer(email: string, reason: string): Promise<void> {
  await request(`${ADMIN_BASE}/referrers/${encodeURIComponent(email)}/whitelist`, {
    method: "POST",
    body: JSON.stringify({ reason }),
    isAdmin: true,
  });
}
