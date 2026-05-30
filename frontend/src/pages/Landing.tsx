import { useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { CheckCircle2, Clock, ArrowRight } from "lucide-react";
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
import { StatusBadge } from "@/components/StatusBadge";
import { useEntryStatus } from "@/lib/api/hooks";
import { ProfileLookupSchema, type ProfileLookupInput, type PublicStatusResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";

export function LandingPage() {
  const [result, setResult] = useState<PublicStatusResponse | null>(null);
  const checkStatus = useEntryStatus();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProfileLookupInput>({
    resolver: zodResolver(ProfileLookupSchema),
  });

  const onSubmit = async (data: ProfileLookupInput) => {
    try {
      const res = await checkStatus.mutateAsync(data.email);
      setResult(res);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 404) {
          toast.error(
            "Email not found on the waitlist. Sign up from the home page first.",
            { duration: 5000 },
          );
          setResult(null);
          return;
        }
        toast.error(err.message);
      } else {
        toast.error("Something went wrong. Please try again.");
      }
    }
  };

  const isInvited = result?.status === "INVITED";

  return (
    <div className="container mx-auto max-w-md px-4 py-12">
      <div className="mb-8 space-y-2 text-center">
        <h1 className="text-3xl font-bold tracking-tight">Early Access</h1>
        <p className="text-muted-foreground">
          Enter your email to check whether you have been granted access.
        </p>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Check your access</CardTitle>
          <CardDescription>Use the email address you signed up with.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={(e) => { void handleSubmit(onSubmit)(e); }}
            noValidate
          >
            <div className="space-y-4">
              <div className="space-y-1">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="you@example.com"
                  autoComplete="email"
                  {...register("email")}
                  aria-invalid={!!errors.email}
                />
                {errors.email && (
                  <p className="text-sm text-red-500">{errors.email.message}</p>
                )}
              </div>
              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? "Checking…" : "Check access"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {result && (
        isInvited ? (
          /* ── Access granted ── */
          <Card className="border-green-500/40 bg-green-500/5">
            <CardHeader>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-5 w-5 text-green-400" />
                <CardTitle className="text-green-400">Access Granted</CardTitle>
              </div>
              <CardDescription>
                Welcome! Your account has been approved for early access.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">
                Signed in as{" "}
                <span className="font-mono text-foreground">{result.email}</span>
              </p>
              <div className="rounded-md border border-green-500/20 bg-green-500/10 p-4 text-sm leading-relaxed">
                🎉 You have been granted early access. Thank you for being one of
                our first supporters — the platform is ready for you!
              </div>
            </CardContent>
          </Card>
        ) : (
          /* ── Not yet invited ── */
          <Card>
            <CardHeader>
              <div className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-yellow-400" />
                <CardTitle>Still on the waiting list</CardTitle>
              </div>
              <CardDescription>
                Your application has not been approved for access yet.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted-foreground">Your current status:</span>
                <StatusBadge status={result.status} />
              </div>
              <p className="text-sm text-muted-foreground">
                We review applications regularly. Refer friends to move up the
                list faster.
              </p>
              <Button variant="outline" size="sm" asChild>
                <Link to="/profile">
                  View my referral code
                  <ArrowRight className="ml-1 h-4 w-4" />
                </Link>
              </Button>
            </CardContent>
          </Card>
        )
      )}
    </div>
  );
}
