import { useParams, useNavigate, Link } from "react-router-dom";
import { toast } from "sonner";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "@/components/StatusBadge";
import { useEntry, useUpdateEntryStatus } from "@/lib/api/hooks";
import { type EntryStatus, LEGAL_TRANSITIONS } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { formatRelativeTime } from "@/lib/utils";

export function AdminEntryDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const entryId = Number(id);

  const { data: entry, isLoading, error } = useEntry(entryId);
  const updateStatus = useUpdateEntryStatus();

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-lg space-y-4 px-4 py-8">
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (error || !entry) {
    return (
      <div className="container mx-auto max-w-lg px-4 py-8">
        <p className="text-red-500">Entry not found.</p>
        <Button variant="ghost" size="sm" asChild className="mt-4">
          <Link to="/admin">Back to dashboard</Link>
        </Button>
      </div>
    );
  }

  const transitions = LEGAL_TRANSITIONS[entry.status];

  const handleAction = async (status: EntryStatus) => {
    try {
      await updateStatus.mutateAsync({ id: entry.id, status });
      toast.success(`Status updated to ${status}`, { duration: 3000 });
      navigate("/admin");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Update failed");
    }
  };

  return (
    <div className="container mx-auto max-w-lg space-y-6 px-4 py-8">
      <Button variant="ghost" size="sm" asChild>
        <Link to="/admin">
          <ArrowLeft className="mr-1 h-4 w-4" />
          Back
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-4">
            <CardTitle>Entry #{String(entry.id)}</CardTitle>
            <StatusBadge status={entry.status} />
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <p className="mb-1 text-muted-foreground">Email</p>
              <p className="font-mono">{entry.email}</p>
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">Name</p>
              <p>{entry.name ?? <span className="text-muted-foreground">—</span>}</p>
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">Company</p>
              <p>{entry.company ?? <span className="text-muted-foreground">—</span>}</p>
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">Status</p>
              <StatusBadge status={entry.status} />
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">Created</p>
              <p>{formatRelativeTime(entry.createdAt)}</p>
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">Updated</p>
              <p>{formatRelativeTime(entry.updatedAt)}</p>
            </div>
          </div>

          {transitions.length > 0 && (
            <div className="border-t pt-4">
              <p className="mb-3 text-sm text-muted-foreground">Actions</p>
              <div className="flex flex-wrap gap-2">
                {transitions.map((s) => (
                  <Button
                    key={s}
                    variant="outline"
                    size="sm"
                    onClick={() => { void handleAction(s); }}
                    disabled={updateStatus.isPending}
                  >
                    Move to {s}
                  </Button>
                ))}
              </div>
            </div>
          )}

          {transitions.length === 0 && (
            <p className="border-t pt-4 text-sm text-muted-foreground">
              This entry is in a terminal state and cannot be transitioned further.
            </p>
          )}

        </CardContent>
      </Card>
    </div>
  );
}
