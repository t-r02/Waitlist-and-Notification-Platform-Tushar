import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useAdminLogin } from "@/lib/api/hooks";
import { LoginSchema, type LoginInput } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/providers/AuthProvider";

export function AdminLoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const adminLogin = useAdminLogin();

  useEffect(() => {
    if (isAuthenticated) navigate("/admin");
  }, [isAuthenticated, navigate]);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({
    resolver: zodResolver(LoginSchema),
  });

  const onSubmit = async (data: LoginInput) => {
    try {
      const res = await adminLogin.mutateAsync(data);
      login(res.token);
      navigate("/admin");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError("password", { message: "Invalid credentials" });
      } else {
        setError("password", {
          message:
            err instanceof ApiError ? err.message : "Login failed",
        });
      }
    }
  };

  return (
    <div className="container mx-auto flex min-h-[calc(100vh-3.5rem)] items-center justify-center px-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
          <ShieldCheck className="mx-auto mb-2 h-8 w-8 text-primary" />
          <CardTitle>Admin login</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={(e) => { void handleSubmit(onSubmit)(e); }} noValidate>
            <div className="space-y-4">
              <div className="space-y-1">
                <Label htmlFor="username">Username</Label>
                <Input
                  id="username"
                  type="text"
                  autoComplete="username"
                  {...register("username")}
                  aria-invalid={!!errors.username}
                />
                {errors.username && (
                  <p className="text-sm text-red-500">
                    {errors.username.message}
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  {...register("password")}
                  aria-invalid={!!errors.password}
                />
                {errors.password && (
                  <p className="text-sm text-red-500">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting}
              >
                {isSubmitting ? "Logging in..." : "Log in"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
