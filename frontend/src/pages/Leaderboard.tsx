import { useState } from "react";
import { Users } from "lucide-react";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useLeaderboard } from "@/lib/api/hooks";
import type { LeaderboardWindow } from "@/lib/api/types";
import { maskEmail } from "@/lib/utils";

function LeaderboardTable({ window }: { window: LeaderboardWindow }) {
  const { data, isLoading, error } = useLeaderboard(window);

  if (isLoading) {
    return (
      <div className="space-y-2 pt-4">
        {Array.from({ length: 10 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <p className="py-8 text-center text-sm text-red-500">
        Failed to load leaderboard.
      </p>
    );
  }

  if (!data || data.length === 0) {
    if (window === "week") {
      return (
        <div className="space-y-2 py-16 text-center">
          <Users className="mx-auto h-10 w-10 text-muted-foreground" />
          <p className="font-medium">No activity this week yet</p>
          <p className="text-sm text-muted-foreground">
            Referral points reset each ISO week. Invite friends to appear here.
          </p>
        </div>
      );
    }
    return (
      <div className="space-y-2 py-16 text-center">
        <Users className="mx-auto h-10 w-10 text-muted-foreground" />
        <p className="font-medium">No entries yet</p>
        <p className="text-sm text-muted-foreground">
          Be the first to earn referral points.
        </p>
      </div>
    );
  }

  return (
    <ol className="divide-y divide-border">
      {data.slice(0, 10).map((entry, idx) => (
        <li
          key={entry.email}
          className="flex items-center justify-between px-4 py-3 transition-colors hover:bg-muted/30"
        >
          <div className="flex items-center gap-4">
            <span
              className={`w-6 text-right font-mono text-sm tabular-nums ${
                idx === 0
                  ? "font-bold text-yellow-400"
                  : idx === 1
                    ? "font-bold text-slate-300"
                    : idx === 2
                      ? "font-bold text-amber-600"
                      : "text-muted-foreground"
              }`}
            >
              {idx + 1}
            </span>
            <span className="font-mono text-sm">{maskEmail(entry.email)}</span>
            {entry.badge && (
              <Badge
                variant={
                  entry.badge === "GOLD"
                    ? "gold"
                    : entry.badge === "SILVER"
                      ? "silver"
                      : "bronze"
                }
              >
                {entry.badge}
              </Badge>
            )}
          </div>
          <span className="text-sm font-semibold tabular-nums">
            {entry.points} pts
          </span>
        </li>
      ))}
    </ol>
  );
}

export function LeaderboardPage() {
  const [tab, setTab] = useState<LeaderboardWindow>("week");

  return (
    <div className="container mx-auto max-w-2xl px-4 py-12">
      <div className="mb-8 space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">Leaderboard</h1>
        <p className="text-muted-foreground">
          Top referrers earn badges and recognition.
        </p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">Rankings</CardTitle>
            <Tabs
              value={tab}
              onValueChange={(v) => { setTab(v as LeaderboardWindow); }}
            >
              <TabsList>
                <TabsTrigger value="week">This week</TabsTrigger>
                <TabsTrigger value="all">All time</TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          <Tabs value={tab}>
            <TabsContent value="week" className="mt-0">
              <LeaderboardTable window="week" />
            </TabsContent>
            <TabsContent value="all" className="mt-0">
              <LeaderboardTable window="all" />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
