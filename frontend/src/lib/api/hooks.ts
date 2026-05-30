import {
  useQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import {
  signup,
  getLeaderboard,
  getProfile,
  getEntryStatus,
  adminLogin,
  getEntries,
  updateEntryStatus,
  bulkUpdateStatus,
  verifyEmail,
  resendVerification,
  getFlaggedReferrers,
  getAuditLog,
  blacklistReferrer,
  whitelistReferrer,
  ApiError,
} from "./client";
import type {
  SignupInput,
  SignupResponse,
  ProfileResponse,
  PublicStatusResponse,
  VerifyResponse,
  FlaggedReferrer,
  AuditLogEntry,
  LeaderboardWindow,
  EntryStatus,
  WaitlistEntry,
  LeaderboardEntry,
} from "./types";

// ── Query keys ────────────────────────────────────────────────────────────────

export const queryKeys = {
  leaderboard: (window: LeaderboardWindow) => ["leaderboard", window] as const,
  entries: (status?: EntryStatus) => ["entries", status] as const,
  entry: (id: number) => ["entry", id] as const,
  flaggedReferrers: () => ["flaggedReferrers"] as const,
  auditLog: (params?: object) => ["auditLog", params] as const,
};

// ── Public hooks ──────────────────────────────────────────────────────────────

export function useLeaderboard(window: LeaderboardWindow) {
  return useQuery<LeaderboardEntry[]>({
    queryKey: queryKeys.leaderboard(window),
    queryFn: () => getLeaderboard(window),
    staleTime: 10_000,
    refetchInterval: 30_000,
  });
}

export function useSignup() {
  return useMutation<SignupResponse, Error, SignupInput>({
    mutationFn: signup,
  });
}

export function useProfile() {
  return useMutation<ProfileResponse, Error, string>({
    mutationFn: getProfile,
  });
}

export function useEntryStatus() {
  return useMutation<PublicStatusResponse, Error, string>({
    mutationFn: getEntryStatus,
  });
}

export function useVerifyEmail() {
  return useMutation<VerifyResponse, Error, string>({
    mutationFn: verifyEmail,
  });
}

export function useResendVerification() {
  return useMutation<unknown, Error, string>({
    mutationFn: resendVerification,
  });
}

// ── Admin hooks ───────────────────────────────────────────────────────────────

export function useAdminLogin() {
  return useMutation({
    mutationFn: adminLogin,
  });
}

export function useEntries(status?: EntryStatus) {
  return useQuery<WaitlistEntry[]>({
    queryKey: queryKeys.entries(status),
    queryFn: () => getEntries(status),
    staleTime: 10_000,
  });
}

export function useEntry(id: number) {
  const queryClient = useQueryClient();
  return useQuery<WaitlistEntry>({
    queryKey: queryKeys.entry(id),
    queryFn: async () => {
      // The admin service has no GET /entries/:id endpoint.
      // Try the cached list first; if missing, fetch the full list.
      for (const status of [undefined, "PENDING", "APPROVED", "REJECTED", "INVITED"] as const) {
        const cached = queryClient.getQueryData<WaitlistEntry[]>(queryKeys.entries(status));
        const hit = cached?.find((e) => e.id === id);
        if (hit) return hit;
      }
      const all = await getEntries();
      const entry = all.find((e) => e.id === id);
      if (!entry) throw new ApiError(404, `Entry ${String(id)} not found`);
      return entry;
    },
    staleTime: 10_000,
  });
}

export function useUpdateEntryStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: EntryStatus }) =>
      updateEntryStatus(id, status),
    onSuccess: async (_data, { id }) => {
      await queryClient.invalidateQueries({ queryKey: ["entries"] });
      await queryClient.invalidateQueries({ queryKey: queryKeys.entry(id) });
      // Status changes can affect referral points → refresh leaderboard
      await queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
    },
  });
}

export function useBulkUpdateStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      ids,
      newStatus,
    }: {
      ids: number[];
      newStatus: EntryStatus;
    }) => bulkUpdateStatus(ids, newStatus),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["entries"] });
      // Bulk approvals / rejections change leaderboard scores
      await queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
    },
  });
}

export function useOptimisticEntryUpdate() {
  const queryClient = useQueryClient();
  return (id: number, updates: Partial<WaitlistEntry>) => {
    queryClient.setQueryData<WaitlistEntry[]>(queryKeys.entries(), (old) =>
      old?.map((e) => (e.id === id ? { ...e, ...updates } : e)),
    );
  };
}

// ── Admin fraud hooks ─────────────────────────────────────────────────────────

export function useFlaggedReferrers() {
  return useQuery<FlaggedReferrer[]>({
    queryKey: queryKeys.flaggedReferrers(),
    queryFn: getFlaggedReferrers,
    staleTime: 30_000,
  });
}

export function useAuditLog(params?: {
  email?: string;
  eventType?: string;
  startDate?: string;
  endDate?: string;
}) {
  return useQuery<AuditLogEntry[]>({
    queryKey: queryKeys.auditLog(params),
    queryFn: () => getAuditLog(params),
    staleTime: 15_000,
  });
}

export function useBlacklistReferrer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, reason }: { email: string; reason: string }) =>
      blacklistReferrer(email, reason),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["flaggedReferrers"] });
      await queryClient.invalidateQueries({ queryKey: ["auditLog"] });
      await queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
    },
  });
}

export function useWhitelistReferrer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, reason }: { email: string; reason: string }) =>
      whitelistReferrer(email, reason),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["flaggedReferrers"] });
      await queryClient.invalidateQueries({ queryKey: ["auditLog"] });
      await queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
    },
  });
}
