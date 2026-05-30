import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Copy, Check, Info, Mail } from "lucide-react";
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
import { RateLimitInterstitial } from "@/components/RateLimitInterstitial";
import { useProfile } from "@/lib/api/hooks";
import {
  ProfileLookupSchema,
  type ProfileLookupInput,
  type ProfileResponse,
} from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";

export function ProfilePage() {
  const [result, setResult] = useState<ProfileResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [rateLimitSeconds, setRateLimitSeconds] = useState<number | null>(null);

  const checkProfile = useProfile();

  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ProfileLookupInput>({
    resolver: zodResolver(ProfileLookupSchema),
  });

  const email = watch("email");

  const onSubmit = async (data: ProfileLookupInput) => {
    try {
      const res = await checkProfile.mutateAsync(data.email);
      setResult(res);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 404) {
          toast.error(
            "Email not found on the waitlist. Sign up from the home page to get your referral code.",
            { duration: 5000 },
          );
          return;
        }
        if (err.status === 429) {
          setRateLimitSeconds(err.retryAfter ?? 60);
          return;
        }
        if (err.errors && err.errors.length > 0 && err.errors[0]?.toLowerCase().includes("email")) {
          setError("email", { message: err.errors[0] });
          return;
        }
        toast.error(err.message);
      } else {
        toast.error("Something went wrong.");
      }
    }
  };

  const referralLink = result?.referralCode
    ? `${window.location.origin}/?ref=${result.referralCode}`
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
    <div className="container mx-auto max-w-md px-4 py-12">
      <div className="mb-8 space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">Look up your spot</h1>
        <p className="text-muted-foreground">
          Enter your email to retrieve your referral code.
        </p>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Find my referral</CardTitle>
          <CardDescription>
            Enter the email you signed up with.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={(e) => { void handleSubmit(onSubmit)(e); }} noValidate>
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
                {isSubmitting ? "Looking up..." : "Look up"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {result && (
        result.verified && result.referralCode ? (
          <Card>
            <CardHeader>
              <CardTitle className="text-green-400">
                Found your registration
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <p className="mb-1 text-sm text-muted-foreground">Email</p>
                <p className="font-mono text-sm">{email}</p>
              </div>
              <div>
                <p className="mb-1 text-sm text-muted-foreground">
                  Referral code
                </p>
                <p className="font-mono text-xl font-bold tracking-widest">
                  {result.referralCode}
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
              <div className="flex items-start gap-2 rounded-md border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
                <Info className="mt-0.5 h-4 w-4 shrink-0" />
                <span>
                  Conversion stats (how many referrals converted) are visible to
                  admins only.
                </span>
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card className="border-yellow-500/40 bg-yellow-500/5">
            <CardHeader>
              <div className="flex items-center gap-2">
                <Mail className="h-5 w-5 text-yellow-400" />
                <CardTitle className="text-yellow-400">Email not verified</CardTitle>
              </div>
              <CardDescription>
                You are registered but haven&apos;t verified your email yet.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Check your inbox for the verification link. Your referral code will
                appear here once you verify your email address.
              </p>
            </CardContent>
          </Card>
        )
      )}
    </div>
  );
}
