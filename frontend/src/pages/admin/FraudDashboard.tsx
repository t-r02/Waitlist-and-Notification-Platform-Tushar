import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { ShieldAlert, ShieldCheck, ShieldBan, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import {
  useFlaggedReferrers,
  useAuditLog,
  useBlacklistReferrer,
  useWhitelistReferrer,
} from "@/lib/api/hooks";
import {
  AbuseActionSchema,
  type AbuseActionInput,
  type FlaggedReferrer,
} from "@/lib/api/types";
import { maskEmail } from "@/lib/utils";

type ActionType = "blacklist" | "whitelist";

interface ActionDialogProps {
  open: boolean;
  target: FlaggedReferrer | null;
  actionType: ActionType;
  onClose: () => void;
}

function ActionDialog({ open, target, actionType, onClose }: ActionDialogProps) {
  const blacklist = useBlacklistReferrer();
  const whitelist = useWhitelistReferrer();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AbuseActionInput>({
    resolver: zodResolver(AbuseActionSchema),
  });

  const onSubmit = async (data: AbuseActionInput) => {
    if (!target) return;
    try {
      if (actionType === "blacklist") {
        await blacklist.mutateAsync({ email: target.email, reason: data.reason });
        toast.success(`${target.email} blacklisted`);
      } else {
        await whitelist.mutateAsync({ email: target.email, reason: data.reason });
        toast.success(`${target.email} whitelisted`);
      }
      reset();
      onClose();
    } catch {
      toast.error(`Failed to ${actionType} referrer`);
    }
  };

  const isBlacklist = actionType === "blacklist";

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) { reset(); onClose(); } }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className={isBlacklist ? "text-red-400" : "text-green-400"}>
            {isBlacklist ? "Blacklist referrer" : "Whitelist referrer"}
          </DialogTitle>
          <DialogDescription>
            {isBlacklist
              ? `This will zero out all points for ${target?.email ?? ""} and block future awards.`
              : `This will restore ${target?.email ?? ""} to active status and restore original points if available.`}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={(e) => { void handleSubmit(onSubmit)(e); }} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="reason">Reason *</Label>
            <Input
              id="reason"
              placeholder={isBlacklist ? "e.g. Confirmed bot traffic" : "e.g. False positive — legitimate user"}
              {...register("reason")}
              aria-invalid={!!errors.reason}
            />
            {errors.reason && (
              <p className="text-sm text-red-500">{errors.reason.message}</p>
            )}
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => { reset(); onClose(); }}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting}
              variant={isBlacklist ? "destructive" : "default"}
            >
              {isSubmitting ? "Processing…" : isBlacklist ? "Blacklist" : "Whitelist"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function StatusChip({ status }: { status: string }) {
  if (status === "BLACKLISTED") return <Badge variant="destructive">BLACKLISTED</Badge>;
  if (status === "WHITELISTED") return <Badge className="bg-green-500/20 text-green-400 hover:bg-green-500/30">WHITELISTED</Badge>;
  return <Badge variant="secondary">FLAGGED</Badge>;
}

export function FraudDashboardPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogTarget, setDialogTarget] = useState<FlaggedReferrer | null>(null);
  const [dialogAction, setDialogAction] = useState<ActionType>("blacklist");

  // Audit log filters
  const [filterEmail, setFilterEmail] = useState("");
  const [filterType, setFilterType] = useState("");

  const { data: flagged, isLoading: flaggedLoading, refetch: refetchFlagged } = useFlaggedReferrers();
  const { data: auditLog, isLoading: auditLoading, refetch: refetchAudit } = useAuditLog({
    email: filterEmail || undefined,
    eventType: filterType || undefined,
  });

  const openDialog = (referrer: FlaggedReferrer, action: ActionType) => {
    setDialogTarget(referrer);
    setDialogAction(action);
    setDialogOpen(true);
  };

  const eventTypeBadge = (type: string) => {
    const colors: Record<string, string> = {
      REFERRAL_CREATED: "bg-blue-500/20 text-blue-400",
      FLAGGED:          "bg-yellow-500/20 text-yellow-400",
      POINTS_AWARDED:   "bg-green-500/20 text-green-400",
      POINTS_REJECTED:  "bg-red-500/20 text-red-400",
      BLACKLIST_ACTION: "bg-red-600/20 text-red-500",
      WHITELIST_ACTION: "bg-green-600/20 text-green-500",
    };
    const cls = colors[type] ?? "bg-muted text-muted-foreground";
    return (
      <span className={`rounded px-1.5 py-0.5 font-mono text-xs ${cls}`}>
        {type}
      </span>
    );
  };

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Fraud Monitor</h1>
          <p className="text-sm text-muted-foreground">
            Review flagged referrers and the full audit trail
          </p>
        </div>
      </div>

      <Tabs defaultValue="flagged">
        <TabsList className="mb-6">
          <TabsTrigger value="flagged" className="flex items-center gap-1.5">
            <ShieldAlert className="h-4 w-4" />
            Flagged Referrers
            {flagged && flagged.length > 0 && (
              <span className="ml-1 rounded-full bg-red-500/20 px-1.5 py-0.5 text-xs text-red-400">
                {flagged.length}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="audit" className="flex items-center gap-1.5">
            <ShieldCheck className="h-4 w-4" />
            Audit Log
          </TabsTrigger>
        </TabsList>

        {/* ── Flagged Referrers Tab ─────────────────────────────────── */}
        <TabsContent value="flagged">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <div>
                <CardTitle className="text-base">Flagged &amp; blacklisted referrers</CardTitle>
                <CardDescription>
                  Users who triggered the IP-rate heuristic or were manually actioned.
                </CardDescription>
              </div>
              <Button variant="ghost" size="icon" onClick={() => { void refetchFlagged(); }}>
                <RefreshCw className="h-4 w-4" />
              </Button>
            </CardHeader>
            <CardContent>
              {flaggedLoading ? (
                <p className="py-8 text-center text-sm text-muted-foreground">Loading…</p>
              ) : !flagged || flagged.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  No flagged referrers found.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left text-muted-foreground">
                        <th className="pb-2 pr-4 font-medium">Email</th>
                        <th className="pb-2 pr-4 font-medium">Status</th>
                        <th className="pb-2 pr-4 font-medium text-right">Points</th>
                        <th className="pb-2 pr-4 font-medium text-right">Referrals</th>
                        <th className="pb-2 pr-4 font-medium text-right">Unique IPs</th>
                        <th className="pb-2 pr-4 font-medium">Flagged at</th>
                        <th className="pb-2 font-medium">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {flagged.map((r) => (
                        <tr key={r.email} className="border-b last:border-0 hover:bg-muted/30">
                          <td className="py-3 pr-4 font-mono">{maskEmail(r.email)}</td>
                          <td className="py-3 pr-4"><StatusChip status={r.referrerStatus} /></td>
                          <td className="py-3 pr-4 text-right tabular-nums">{r.totalPoints}</td>
                          <td className="py-3 pr-4 text-right tabular-nums">{r.referralCount}</td>
                          <td className="py-3 pr-4 text-right tabular-nums">{r.uniqueIpCount}</td>
                          <td className="py-3 pr-4 text-xs text-muted-foreground">
                            {r.flaggedAt
                              ? new Date(r.flaggedAt).toLocaleString()
                              : "—"}
                          </td>
                          <td className="py-3">
                            <div className="flex gap-1">
                              {r.referrerStatus !== "BLACKLISTED" && (
                                <Button
                                  size="sm"
                                  variant="destructive"
                                  className="h-7 px-2 text-xs"
                                  onClick={() => { openDialog(r, "blacklist"); }}
                                >
                                  <ShieldBan className="mr-1 h-3 w-3" />
                                  Blacklist
                                </Button>
                              )}
                              {r.referrerStatus !== "WHITELISTED" && (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  className="h-7 px-2 text-xs"
                                  onClick={() => { openDialog(r, "whitelist"); }}
                                >
                                  <ShieldCheck className="mr-1 h-3 w-3" />
                                  Whitelist
                                </Button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Audit Log Tab ─────────────────────────────────────────── */}
        <TabsContent value="audit">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex flex-row items-center justify-between">
                <div>
                  <CardTitle className="text-base">Audit trail</CardTitle>
                  <CardDescription>
                    Immutable log of all fraud-related events.
                  </CardDescription>
                </div>
                <Button variant="ghost" size="icon" onClick={() => { void refetchAudit(); }}>
                  <RefreshCw className="h-4 w-4" />
                </Button>
              </div>
              <div className="mt-3 flex flex-wrap gap-3">
                <div className="flex-1 min-w-[180px] space-y-1">
                  <Label htmlFor="filterEmail" className="text-xs">Filter by email</Label>
                  <Input
                    id="filterEmail"
                    placeholder="referrer@example.com"
                    value={filterEmail}
                    onChange={(e) => { setFilterEmail(e.target.value); }}
                    className="h-8 text-sm"
                  />
                </div>
                <div className="flex-1 min-w-[180px] space-y-1">
                  <Label htmlFor="filterType" className="text-xs">Filter by event type</Label>
                  <Input
                    id="filterType"
                    placeholder="e.g. FLAGGED"
                    value={filterType}
                    onChange={(e) => { setFilterType(e.target.value.toUpperCase()); }}
                    className="h-8 text-sm"
                  />
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {auditLoading ? (
                <p className="py-8 text-center text-sm text-muted-foreground">Loading…</p>
              ) : !auditLog || auditLog.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  No audit events found.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left text-muted-foreground">
                        <th className="pb-2 pr-3 font-medium">Event</th>
                        <th className="pb-2 pr-3 font-medium">Referrer</th>
                        <th className="pb-2 pr-3 font-medium">Referee / Detail</th>
                        <th className="pb-2 pr-3 font-medium text-right">Δ pts</th>
                        <th className="pb-2 pr-3 font-medium">Admin / Reason</th>
                        <th className="pb-2 font-medium">Time</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditLog.map((a) => (
                        <tr key={a.id} className="border-b last:border-0 hover:bg-muted/30">
                          <td className="py-2 pr-3">{eventTypeBadge(a.eventType)}</td>
                          <td className="py-2 pr-3 font-mono text-xs">
                            {maskEmail(a.referrerEmail)}
                          </td>
                          <td className="py-2 pr-3 text-xs text-muted-foreground">
                            {a.refereeEmail ? maskEmail(a.refereeEmail) : a.ipHash ?? "—"}
                          </td>
                          <td className="py-2 pr-3 text-right tabular-nums font-mono text-xs">
                            {a.delta != null
                              ? (a.delta > 0 ? `+${String(a.delta)}` : String(a.delta))
                              : "—"}
                          </td>
                          <td className="py-2 pr-3 text-xs text-muted-foreground max-w-[200px] truncate">
                            {a.adminUser
                              ? <span><span className="text-foreground">{a.adminUser}</span>{a.reason ? ` — ${a.reason}` : ""}</span>
                              : a.reason ?? "—"}
                          </td>
                          <td className="py-2 text-xs text-muted-foreground whitespace-nowrap">
                            {new Date(a.createdAt).toLocaleString()}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <ActionDialog
        open={dialogOpen}
        target={dialogTarget}
        actionType={dialogAction}
        onClose={() => { setDialogOpen(false); setDialogTarget(null); }}
      />
    </div>
  );
}
