import { useState, useEffect, useRef } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Copy, Check, Trophy, ArrowRight, Mail } from "lucide-react";
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
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { RateLimitInterstitial } from "@/components/RateLimitInterstitial";
import { useSignup, useLeaderboard, useResendVerification } from "@/lib/api/hooks";
import { SignupSchema, type SignupInput, type SignupResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { maskEmail } from "@/lib/utils";

// Omit `website` from the form — handled separately via ref to avoid autofill
type FormValues = Omit<SignupInput, "website">;
const FormSchema = SignupSchema.omit({ website: true });

export function HomePage() {
  const [searchParams] = useSearchParams();
  const refParam = searchParams.get("ref");
  const [successData, setSuccessData] = useState<SignupResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [rateLimitSeconds, setRateLimitSeconds] = useState<number | null>(null);

  // Honeypot: injected via JS after mount so browsers never autofill it
  // (autofill only targets fields present in the initial HTML parse)
  const honeypotRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const el = document.createElement("input");
    el.type = "text";
    el.tabIndex = -1;
    el.setAttribute("aria-hidden", "true");
    el.setAttribute("autocomplete", "off");
    el.style.cssText =
      "position:absolute;left:-9999px;top:-9999px;opacity:0;pointer-events:none;width:0;height:0;";
    document.body.appendChild(el);
    honeypotRef.current = el;
    return () => {
      document.body.removeChild(el);
      honeypotRef.current = null;
    };
  }, []);

  const { data: leaderboard, isLoading: lbLoading } = useLeaderboard("week");
  const signup = useSignup();
  const resend = useResendVerification();

  const {
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(FormSchema),
    defaultValues: {
      email: "",
      name: "",        // required — user must fill before submit
      company: "",
      referralCode: refParam ?? "",
    },
  });

  useEffect(() => {
    if (refParam) setValue("referralCode", refParam);
  }, [refParam, setValue]);

  const onSubmit = async (data: FormValues) => {
    try {
      const result = await signup.mutateAsync({
        ...data,
        website: honeypotRef.current?.value ?? "",
      });
      setSuccessData(result);
      if (result.duplicate) {
        toast.info("Already registered — here is your referral code.");
      } else {
        toast.success("You are on the waitlist.", { duration: 3000 });
      }
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 429) {
          setRateLimitSeconds(err.retryAfter ?? 60);
          return;
        }
        if (err.errors && err.errors.length > 0) {
          err.errors.forEach((msg) => {
            if (msg.toLowerCase().includes("email")) {
              setError("email", { message: msg });
            } else if (msg.toLowerCase().includes("name")) {
              setError("name", { message: msg });
            } else if (msg.toLowerCase().includes("company")) {
              setError("company", { message: msg });
            } else if (msg.toLowerCase().includes("referral")) {
              setError("referralCode", { message: msg });
            }
          });
          return;
        }
        const corrId = err.correlationId ? ` (${err.correlationId})` : "";
        toast.error(`${err.message}${corrId}`);
      } else {
        toast.error("Something went wrong. Please try again.");
      }
    }
  };

  const referralLink = successData?.referralCode
    ? `${window.location.origin}/?ref=${successData.referralCode}`
    : "";

  const handleCopy = () => {
    void navigator.clipboard.writeText(referralLink).then(() => {
      setCopied(true);
      setTimeout(() => { setCopied(false); }, 2000);
    });
  };

  if (rateLimitSeconds !== null) {
    return (
      <RateLimitInterstitial
        retryAfter={rateLimitSeconds}
        onExpire={() => { setRateLimitSeconds(null); }}
      />
    );
  }

  return (
    <div className="container mx-auto max-w-2xl px-4 py-16">
      <div className="mb-12 space-y-4 text-center">
        <h1 className="text-4xl font-bold tracking-tight">Join the waitlist</h1>
        <p className="text-lg text-muted-foreground">
          Sign up to get early access. Refer friends to move up the list.
        </p>
      </div>

      {successData ? (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle className={successData.verified ? "text-green-400" : "text-yellow-400"}>
              {!successData.verified
                ? "Check your email"
                : successData.duplicate
                  ? "You are already registered"
                  : "You have been registered"}
            </CardTitle>
            <CardDescription>
              {!successData.verified
                ? successData.duplicate
                  ? "You are registered but haven't verified your email yet. Check your inbox for the verification link."
                  : "We sent a verification link to your inbox. Click it to unlock your referral code and get on the leaderboard."
                : successData.duplicate
                  ? "This email was already registered. Here is your referral code."
                  : "You are on the waitlist. Share your referral link to move up faster."}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {successData.verified && successData.referralCode ? (
              <>
                <div>
                  <p className="mb-1 text-sm text-muted-foreground">
                    Your referral code
                  </p>
                  <p className="font-mono text-xl font-bold tracking-widest">
                    {successData.referralCode}
                  </p>
                </div>
                <div>
                  <p className="mb-2 text-sm text-muted-foreground">
                    Referral link
                  </p>
                  <div className="flex items-center gap-2">
                    <code className="flex-1 truncate rounded bg-muted px-3 py-2 font-mono text-sm">
                      {referralLink}
                    </code>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={handleCopy}
                      className="shrink-0"
                    >
                      {copied ? (
                        <Check className="h-4 w-4 text-green-400" />
                      ) : (
                        <Copy className="h-4 w-4" />
                      )}
                      {copied ? "Copied" : "Copy"}
                    </Button>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex items-center gap-3 rounded-md border bg-muted/40 p-4">
                <Mail className="h-5 w-5 shrink-0 text-yellow-400" />
                <div className="space-y-1 text-sm">
                  <p className="font-medium">Verification email sent</p>
                  <p className="text-muted-foreground">
                    Didn&apos;t receive it?{" "}
                    <button
                      type="button"
                      className="text-primary underline underline-offset-2 hover:no-underline disabled:opacity-50"
                      disabled={resend.isPending}
                      onClick={() => {
                        void resend.mutateAsync(
                          /* Derive email from the form field — SignupResponse doesn't carry it */
                          (document.getElementById("email") as HTMLInputElement | null)?.value ?? ""
                        ).catch(() => { /* ignore */ });
                      }}
                    >
                      {resend.isPending ? "Sending…" : "Resend verification email"}
                    </button>
                  </p>
                </div>
              </div>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => { setSuccessData(null); }}
            >
              Sign up another email
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle>Request access</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={(e) => { void handleSubmit(onSubmit)(e); }} noValidate>
              <div className="space-y-4">
                <div className="space-y-1">
                  <Label htmlFor="email">Email *</Label>
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

                <div className="space-y-1">
                  <Label htmlFor="name">Name *</Label>
                  <Input
                    id="name"
                    type="text"
                    placeholder="Your name"
                    autoComplete="name"
                    {...register("name")}
                    aria-invalid={!!errors.name}
                  />
                  {errors.name && (
                    <p className="text-sm text-red-500">{errors.name.message}</p>
                  )}
                </div>

                <div className="space-y-1">
                  <Label htmlFor="company">Company</Label>
                  <Input
                    id="company"
                    type="text"
                    placeholder="Your company"
                    autoComplete="organization"
                    {...register("company")}
                    aria-invalid={!!errors.company}
                  />
                  {errors.company && (
                    <p className="text-sm text-red-500">
                      {errors.company.message}
                    </p>
                  )}
                </div>

                <div className="space-y-1">
                  <Label htmlFor="referralCode">Referral code</Label>
                  <Input
                    id="referralCode"
                    type="text"
                    placeholder="8-character code"
                    autoComplete="off"
                    maxLength={8}
                    {...register("referralCode")}
                    aria-invalid={!!errors.referralCode}
                  />
                  {errors.referralCode && (
                    <p className="text-sm text-red-500">
                      {errors.referralCode.message}
                    </p>
                  )}
                </div>

                <Button
                  type="submit"
                  className="w-full"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "Submitting..." : "Request access"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <div className="flex items-center gap-2">
            <Trophy className="h-5 w-5 text-yellow-400" />
            <CardTitle className="text-base">Top referrers this week</CardTitle>
          </div>
          <Button variant="ghost" size="sm" asChild>
            <Link to="/leaderboard">
              View all
              <ArrowRight className="ml-1 h-4 w-4" />
            </Link>
          </Button>
        </CardHeader>
        <CardContent>
          {lbLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
          ) : !leaderboard || leaderboard.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">
              No referrals recorded this week yet.
            </p>
          ) : (
            <ol className="space-y-2">
              {leaderboard.slice(0, 5).map((entry, idx) => (
                <li
                  key={entry.email}
                  className="flex items-center justify-between rounded-md px-3 py-2 text-sm hover:bg-muted/50"
                >
                  <div className="flex items-center gap-3">
                    <span className="w-5 text-right font-mono text-muted-foreground">
                      {idx + 1}
                    </span>
                    <span className="font-mono">{maskEmail(entry.email)}</span>
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
                  <span className="font-semibold tabular-nums">
                    {entry.points} pts
                  </span>
                </li>
              ))}
            </ol>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
