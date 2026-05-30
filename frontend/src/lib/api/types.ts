import { z } from "zod";

// ── Shared enums ─────────────────────────────────────────────────────────────

export const EntryStatus = z.enum([
  "PENDING",
  "APPROVED",
  "REJECTED",
  "INVITED",
]);
export type EntryStatus = z.infer<typeof EntryStatus>;

export const BadgeType = z.enum(["BRONZE", "SILVER", "GOLD"]).nullable();
export type BadgeType = z.infer<typeof BadgeType>;

// ── Request schemas ───────────────────────────────────────────────────────────

export const SignupSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Enter a valid email address"),
  name: z
    .string()
    .min(1, "Name is required")
    .max(120, "Name must be 120 characters or fewer"),
  company: z
    .string()
    .max(120, "Company must be 120 characters or fewer")
    .optional()
    .or(z.literal("")),
  referralCode: z
    .string()
    .regex(/^[a-zA-Z0-9]{8}$/, "Referral code must be exactly 8 alphanumeric characters")
    .optional()
    .or(z.literal(""))
    .nullable(),
  website: z.string().optional(),
});
export type SignupInput = z.infer<typeof SignupSchema>;

export const LoginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(1, "Password is required"),
});
export type LoginInput = z.infer<typeof LoginSchema>;

export const ProfileLookupSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Enter a valid email address"),
});
export type ProfileLookupInput = z.infer<typeof ProfileLookupSchema>;

// ── Response schemas ──────────────────────────────────────────────────────────

export const SignupResponseSchema = z.object({
  message: z.string(),
  /** Null for newly registered unverified users; revealed after email verification. */
  referralCode: z.string().nullable(),
  duplicate: z.boolean(),
  /** False for new signups until the user clicks the verification link. */
  verified: z.boolean().optional().default(false),
});
export type SignupResponse = z.infer<typeof SignupResponseSchema>;

export const LoginResponseSchema = z.object({
  token: z.string(),
});
export type LoginResponse = z.infer<typeof LoginResponseSchema>;

export const LeaderboardEntrySchema = z.object({
  email: z.string(),
  points: z.number(),
  badge: BadgeType,
});
export type LeaderboardEntry = z.infer<typeof LeaderboardEntrySchema>;

export const WaitlistEntrySchema = z.object({
  id: z.number(),
  email: z.string(),
  name: z.string().nullable().optional(),
  company: z.string().nullable().optional(),
  status: EntryStatus,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type WaitlistEntry = z.infer<typeof WaitlistEntrySchema>;

export const PublicStatusResponseSchema = z.object({
  email: z.string(),
  status: EntryStatus,
});
export type PublicStatusResponse = z.infer<typeof PublicStatusResponseSchema>;

export const ProfileResponseSchema = z.object({
  email: z.string(),
  /** Null when the user has not yet verified their email address. */
  referralCode: z.string().nullable(),
  /** False when the user has not yet clicked the verification link. */
  verified: z.boolean().optional().default(true),
});
export type ProfileResponse = z.infer<typeof ProfileResponseSchema>;

export const VerifyResponseSchema = z.object({
  message: z.string(),
  email: z.string(),
  referralCode: z.string(),
});
export type VerifyResponse = z.infer<typeof VerifyResponseSchema>;

export const BulkActionResponseSchema = z.object({
  successCount: z.number(),
  failures: z.array(z.string()),
});
export type BulkActionResponse = z.infer<typeof BulkActionResponseSchema>;

export const LeaderboardWindow = z.enum(["all", "week"]);
export type LeaderboardWindow = z.infer<typeof LeaderboardWindow>;

// ── Fraud / audit schemas ──────────────────────────────────────────────────

export const FlaggedReferrerSchema = z.object({
  email: z.string(),
  referrerStatus: z.string(),
  totalPoints: z.number(),
  referralCount: z.number(),
  uniqueIpCount: z.number(),
  flaggedAt: z.string().nullable(),
});
export type FlaggedReferrer = z.infer<typeof FlaggedReferrerSchema>;

export const AuditLogEntrySchema = z.object({
  id: z.number(),
  eventType: z.string(),
  referrerEmail: z.string(),
  refereeEmail: z.string().nullable().optional(),
  ipHash: z.string().nullable().optional(),
  delta: z.number().nullable().optional(),
  totalPoints: z.number().nullable().optional(),
  reason: z.string().nullable().optional(),
  adminUser: z.string().nullable().optional(),
  createdAt: z.string(),
});
export type AuditLogEntry = z.infer<typeof AuditLogEntrySchema>;

export const AbuseActionSchema = z.object({
  reason: z.string().min(1, "Reason is required"),
});
export type AbuseActionInput = z.infer<typeof AbuseActionSchema>;

// Legal status transitions
export const LEGAL_TRANSITIONS: Record<EntryStatus, EntryStatus[]> = {
  PENDING: ["APPROVED", "REJECTED"],
  APPROVED: ["INVITED", "REJECTED"],
  REJECTED: ["PENDING"],
  INVITED: [],
};
