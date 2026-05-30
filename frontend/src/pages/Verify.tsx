import { useEffect, useState } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { CheckCircle2, XCircle, Clock, Copy, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useVerifyEmail } from "@/lib/api/hooks";
import type { VerifyResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";

type VerifyState = "loading" | "success" | "expired" | "invalid" | "error";

export function VerifyPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const [state, setState] = useState<VerifyState>("loading");
  const [data, setData] = useState<VerifyResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [copied, setCopied] = useState(false);

  const verifyMutation = useVerifyEmail();

  useEffect(() => {
    if (!token) {
      setState("invalid");
      return;
    }
    verifyMutation.mutateAsync(token)
      .then((res) => {
        setData(res);
        setState("success");
      })
      .catch((err: unknown) => {
        if (err instanceof ApiError) {
          if (err.status === 400) {
            const msg = err.message.toLowerCase();
            if (msg.includes("expired")) {
              setState("expired");
            } else {
              setState("invalid");
            }
            setErrorMessage(err.message);
          } else {
            setState("error");
            setErrorMessage(err.message);
          }
        } else {
          setState("error");
          setErrorMessage("An unexpected error occurred");
        }
      });
  // Run only once on mount — token won't change
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const referralLink = data
    ? `${window.location.origin}/?ref=${data.referralCode}`
    : "";

  const handleCopy = () => {
    void navigator.clipboard.writeText(referralLink).then(() => {
      setCopied(true);
      setTimeout(() => { setCopied(false); }, 2000);
    });
  };

  if (state === "loading") {
    return (
      <div className="container mx-auto max-w-md px-4 py-20 text-center">
        <Clock className="mx-auto mb-4 h-12 w-12 animate-spin text-primary" />
        <p className="text-muted-foreground">Verifying your email…</p>
      </div>
    );
  }

  if (state === "success" && data) {
    return (
      <div className="container mx-auto max-w-md px-4 py-12">
        <Card className="border-green-500/40 bg-green-500/5">
          <CardHeader>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-6 w-6 text-green-400" />
              <CardTitle className="text-green-400">Email verified!</CardTitle>
            </div>
            <CardDescription>
              Welcome to the waitlist, {data.email}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="mb-1 text-sm text-muted-foreground">Your referral code</p>
              <p className="font-mono text-2xl font-bold tracking-widest">
                {data.referralCode}
              </p>
            </div>
            <div>
              <p className="mb-2 text-sm text-muted-foreground">Referral link</p>
              <div className="flex items-center gap-2">
                <code className="flex-1 truncate rounded bg-muted px-3 py-2 font-mono text-sm">
                  {referralLink}
                </code>
                <Button size="sm" variant="outline" onClick={handleCopy} className="shrink-0">
                  {copied
                    ? <><Check className="mr-1 h-4 w-4 text-green-400" />Copied</>
                    : <><Copy className="mr-1 h-4 w-4" />Copy</>}
                </Button>
              </div>
            </div>
            <p className="text-sm text-muted-foreground">
              Share your referral link with friends to earn points and move up the list faster.
            </p>
            <Button asChild className="w-full">
              <Link to="/profile">View my profile</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (state === "expired") {
    return (
      <div className="container mx-auto max-w-md px-4 py-12">
        <Card className="border-yellow-500/40 bg-yellow-500/5">
          <CardHeader>
            <div className="flex items-center gap-2">
              <Clock className="h-6 w-6 text-yellow-400" />
              <CardTitle className="text-yellow-400">Link expired</CardTitle>
            </div>
            <CardDescription>
              Your verification link has expired (links are valid for 24 hours).
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-sm text-muted-foreground">{errorMessage}</p>
            <Button asChild className="w-full">
              <Link to="/">Sign up again to get a new link</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  // invalid / error
  return (
    <div className="container mx-auto max-w-md px-4 py-12">
      <Card className="border-red-500/40 bg-red-500/5">
        <CardHeader>
          <div className="flex items-center gap-2">
            <XCircle className="h-6 w-6 text-red-400" />
            <CardTitle className="text-red-400">
              {state === "invalid" ? "Invalid link" : "Something went wrong"}
            </CardTitle>
          </div>
          <CardDescription>
            {state === "invalid"
              ? "This verification link is invalid or has already been used."
              : "We could not verify your email right now. Please try again later."}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {errorMessage && (
            <p className="text-sm text-muted-foreground">{errorMessage}</p>
          )}
          <Button asChild variant="outline" className="w-full">
            <Link to="/">Go to home page</Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
