import { useState, useMemo } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { RefreshCw, Search, InboxIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "@/components/StatusBadge";
import {
  useEntries,
  useUpdateEntryStatus,
  useBulkUpdateStatus,
} from "@/lib/api/hooks";
import {
  type EntryStatus,
  type WaitlistEntry,
  LEGAL_TRANSITIONS,
} from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { formatRelativeTime } from "@/lib/utils";

const STATUS_OPTIONS: Array<{ label: string; value: EntryStatus | "ALL" }> = [
  { label: "All", value: "ALL" },
  { label: "Pending", value: "PENDING" },
  { label: "Approved", value: "APPROVED" },
  { label: "Rejected", value: "REJECTED" },
  { label: "Invited", value: "INVITED" },
];

function EntryRow({
  entry,
  selected,
  onToggle,
}: {
  entry: WaitlistEntry;
  selected: boolean;
  onToggle: () => void;
}) {
  const updateStatus = useUpdateEntryStatus();
  const transitions = LEGAL_TRANSITIONS[entry.status];

  const handleAction = async (status: EntryStatus) => {
    try {
      await updateStatus.mutateAsync({ id: entry.id, status });
      toast.success(`Entry ${String(entry.id)} moved to ${status}`, { duration: 3000 });
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Action failed";
      toast.error(msg);
    }
  };

  return (
    <TableRow data-state={selected ? "selected" : undefined}>
      <TableCell>
        <input
          type="checkbox"
          checked={selected}
          onChange={onToggle}
          className="h-4 w-4 rounded border-border"
          aria-label={`Select entry ${String(entry.id)}`}
        />
      </TableCell>
      <TableCell className="font-mono text-sm">{entry.id}</TableCell>
      <TableCell className="max-w-[200px] truncate font-mono text-sm">
        {entry.email}
      </TableCell>
      <TableCell className="max-w-[120px] truncate text-sm">
        {entry.name ?? <span className="text-muted-foreground">—</span>}
      </TableCell>
      <TableCell>
        <StatusBadge status={entry.status} />
      </TableCell>
      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
        {formatRelativeTime(entry.createdAt)}
      </TableCell>
      <TableCell>
        <div className="flex gap-1">
          {transitions.map((s) => (
            <Button
              key={s}
              size="sm"
              variant="outline"
              className="h-7 px-2 text-xs"
              onClick={() => { void handleAction(s); }}
              disabled={updateStatus.isPending}
            >
              {s}
            </Button>
          ))}
          <Button size="sm" variant="ghost" className="h-7 px-2 text-xs" asChild>
            <Link to={`/admin/entries/${String(entry.id)}`}>View</Link>
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
}

function MobileEntryCard({
  entry,
  selected,
  onToggle,
}: {
  entry: WaitlistEntry;
  selected: boolean;
  onToggle: () => void;
}) {
  const updateStatus = useUpdateEntryStatus();
  const transitions = LEGAL_TRANSITIONS[entry.status];

  const handleAction = async (status: EntryStatus) => {
    try {
      await updateStatus.mutateAsync({ id: entry.id, status });
      toast.success(`Entry ${String(entry.id)} moved to ${status}`, { duration: 3000 });
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Action failed";
      toast.error(msg);
    }
  };

  return (
    <div
      className={`space-y-3 rounded-lg border p-4 ${selected ? "border-primary/50 bg-muted/30" : "border-border"}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={selected}
            onChange={onToggle}
            className="h-4 w-4 rounded border-border"
          />
          <span className="font-mono text-xs text-muted-foreground">
            #{String(entry.id)}
          </span>
        </div>
        <StatusBadge status={entry.status} />
      </div>
      <div>
        <p className="truncate font-mono text-sm">{entry.email}</p>
        {entry.name && (
          <p className="text-sm text-muted-foreground">{entry.name}</p>
        )}
        <p className="mt-1 text-xs text-muted-foreground">
          {formatRelativeTime(entry.createdAt)}
        </p>
      </div>
      <div className="flex flex-wrap gap-1">
        {transitions.map((s) => (
          <Button
            key={s}
            size="sm"
            variant="outline"
            className="h-7 px-2 text-xs"
            onClick={() => { void handleAction(s); }}
            disabled={updateStatus.isPending}
          >
            {s}
          </Button>
        ))}
        <Button size="sm" variant="ghost" className="h-7 px-2 text-xs" asChild>
          <Link to={`/admin/entries/${String(entry.id)}`}>View</Link>
        </Button>
      </div>
    </div>
  );
}

interface BulkResultDialog {
  open: boolean;
  successCount: number;
  failures: string[];
}

export function AdminDashboardPage() {
  const [statusFilter, setStatusFilter] = useState<EntryStatus | "ALL">("ALL");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkTarget, setBulkTarget] = useState<EntryStatus>("APPROVED");
  const [bulkDialog, setBulkDialog] = useState<BulkResultDialog>({
    open: false,
    successCount: 0,
    failures: [],
  });

  const queryStatus =
    statusFilter === "ALL" ? undefined : statusFilter;
  const { data: entries, isLoading, refetch } = useEntries(queryStatus);
  const bulkUpdate = useBulkUpdateStatus();

  const filtered = useMemo(() => {
    if (!entries) return [];
    const q = search.toLowerCase().trim();
    if (!q) return entries;
    return entries.filter((e) => e.email.toLowerCase().includes(q));
  }, [entries, search]);

  const allSelected =
    filtered.length > 0 && filtered.every((e) => selected.has(e.id));

  const toggleAll = () => {
    if (allSelected) {
      setSelected(new Set());
    } else {
      setSelected(new Set(filtered.map((e) => e.id)));
    }
  };

  const toggleOne = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleBulk = async () => {
    const ids = Array.from(selected);
    if (ids.length === 0) return;
    try {
      const res = await bulkUpdate.mutateAsync({ ids, newStatus: bulkTarget });
      setBulkDialog({
        open: true,
        successCount: res.data.successCount,
        failures: res.data.failures,
      });
      setSelected(new Set());
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Bulk action failed");
    }
  };

  return (
    <div className="container mx-auto space-y-6 px-4 py-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold">Entries</h1>
        <Button
          variant="outline"
          size="sm"
          onClick={() => { void refetch(); }}
        >
          <RefreshCw className="mr-1 h-4 w-4" />
          Refresh
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Select
          value={statusFilter}
          onValueChange={(v) => {
            setStatusFilter(v as EntryStatus | "ALL");
            setSelected(new Set());
          }}
        >
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="relative min-w-[200px] flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => { setSearch(e.target.value); }}
            placeholder="Search by email..."
            className="pl-9"
          />
        </div>
      </div>

      {/* Desktop table */}
      <div className="hidden rounded-lg border md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={toggleAll}
                  className="h-4 w-4 rounded border-border"
                  aria-label="Select all"
                />
              </TableHead>
              <TableHead className="w-16">ID</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 7 }).map((__, j) => (
                    <TableCell key={j}>
                      <Skeleton className="h-5 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-16 text-center">
                  <InboxIcon className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
                  <p className="text-muted-foreground">No entries found</p>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((entry) => (
                <EntryRow
                  key={entry.id}
                  entry={entry}
                  selected={selected.has(entry.id)}
                  onToggle={() => { toggleOne(entry.id); }}
                />
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Mobile cards */}
      <div className="space-y-3 md:hidden">
        {isLoading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-32 w-full rounded-lg" />
          ))
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center">
            <InboxIcon className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
            <p className="text-muted-foreground">No entries found</p>
          </div>
        ) : (
          filtered.map((entry) => (
            <MobileEntryCard
              key={entry.id}
              entry={entry}
              selected={selected.has(entry.id)}
              onToggle={() => { toggleOne(entry.id); }}
            />
          ))
        )}
      </div>

      {/* Bulk action toolbar */}
      {selected.size > 0 && (
        <div className="sticky bottom-4 flex flex-wrap items-center gap-3 rounded-lg border bg-background p-3 shadow-lg">
          <span className="text-sm text-muted-foreground">
            {selected.size} selected
          </span>
          <Select
            value={bulkTarget}
            onValueChange={(v) => { setBulkTarget(v as EntryStatus); }}
          >
            <SelectTrigger className="w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="APPROVED">APPROVED</SelectItem>
              <SelectItem value="REJECTED">REJECTED</SelectItem>
              <SelectItem value="INVITED">INVITED</SelectItem>
              <SelectItem value="PENDING">PENDING</SelectItem>
            </SelectContent>
          </Select>
          <Button
            onClick={() => { void handleBulk(); }}
            disabled={bulkUpdate.isPending}
            size="sm"
          >
            {bulkUpdate.isPending ? "Applying..." : "Apply"}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => { setSelected(new Set()); }}
          >
            Cancel
          </Button>
        </div>
      )}

      {/* Bulk result dialog */}
      <Dialog
        open={bulkDialog.open}
        onOpenChange={(o) => { setBulkDialog((p) => ({ ...p, open: o })); }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Bulk action result</DialogTitle>
            <DialogDescription>
              {bulkDialog.successCount} entries updated successfully.
            </DialogDescription>
          </DialogHeader>
          {bulkDialog.failures.length > 0 && (
            <div className="space-y-2">
              <p className="text-sm font-medium text-red-500">
                {bulkDialog.failures.length} failures:
              </p>
              <ul className="max-h-60 space-y-1 overflow-y-auto">
                {bulkDialog.failures.map((f, i) => (
                  <li key={i} className="font-mono text-sm text-muted-foreground">
                    {f}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <Button
            onClick={() => { setBulkDialog((p) => ({ ...p, open: false })); }}
          >
            Close
          </Button>
        </DialogContent>
      </Dialog>
    </div>
  );
}
