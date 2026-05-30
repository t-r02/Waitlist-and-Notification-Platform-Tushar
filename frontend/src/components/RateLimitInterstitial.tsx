import { useState, useEffect } from "react";
import { Clock } from "lucide-react";

interface RateLimitInterstitialProps {
  retryAfter: number;
  onExpire: () => void;
}

export function RateLimitInterstitial({
  retryAfter,
  onExpire,
}: RateLimitInterstitialProps) {
  const [remaining, setRemaining] = useState(retryAfter);

  useEffect(() => {
    setRemaining(retryAfter);
    const interval = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          onExpire();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => { clearInterval(interval); };
  }, [retryAfter, onExpire]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/95 backdrop-blur">
      <div className="mx-auto max-w-sm space-y-4 p-8 text-center">
        <Clock className="mx-auto h-12 w-12 text-muted-foreground" />
        <h2 className="text-xl font-semibold">Too many requests</h2>
        <p className="text-muted-foreground">
          You have been rate limited. Please wait before trying again.
        </p>
        <div className="font-mono text-4xl font-bold tabular-nums">
          {String(remaining)}s
        </div>
        <p className="text-sm text-muted-foreground">
          The form will unlock automatically.
        </p>
      </div>
    </div>
  );
}
