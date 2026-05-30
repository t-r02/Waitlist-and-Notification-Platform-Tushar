/* eslint-disable react-refresh/only-export-components */
import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

interface AuthContextValue {
  token: string | null;
  login: (token: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(
    () => sessionStorage.getItem("admin_token"),
  );
  const navigate = useNavigate();

  const logout = useCallback(() => {
    sessionStorage.removeItem("admin_token");
    setToken(null);
  }, []);

  const login = useCallback((t: string) => {
    sessionStorage.setItem("admin_token", t);
    setToken(t);
  }, []);

  useEffect(() => {
    const handler = () => {
      logout();
      toast.error("Session expired. Please log in again.");
      navigate("/admin/login");
    };
    window.addEventListener("auth:expired", handler);
    return () => { window.removeEventListener("auth:expired", handler); };
  }, [logout, navigate]);

  return (
    <AuthContext.Provider
      value={{ token, login, logout, isAuthenticated: !!token }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
