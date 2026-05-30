import { Badge } from "@/components/ui/badge";
import type { EntryStatus } from "@/lib/api/types";

const variantMap: Record<
  EntryStatus,
  "pending" | "approved" | "rejected" | "invited"
> = {
  PENDING: "pending",
  APPROVED: "approved",
  REJECTED: "rejected",
  INVITED: "invited",
};

export function StatusBadge({ status }: { status: EntryStatus }) {
  return <Badge variant={variantMap[status]}>{status}</Badge>;
}
