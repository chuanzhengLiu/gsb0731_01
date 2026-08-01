import { createContext, useContext, useState, type ReactNode } from "react";
import { api, tokenStore } from "@/lib/api";
import type { TokenResponse, User } from "@/lib/types";

interface AuthContextValue {
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, name: string, password: string, teamName: string) => Promise<void>;
  /** Persist an already-obtained token pair (e.g. after accepting an invite). */
  applyTokens: (t: TokenResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(tokenStore.getUser());

  async function login(email: string, password: string) {
    const t = await api<TokenResponse>("/auth/login", {
      method: "POST",
      body: { email, password },
    });
    tokenStore.save(t);
    setUser(t.user);
  }

  async function register(email: string, name: string, password: string, teamName: string) {
    const t = await api<TokenResponse>("/auth/register", {
      method: "POST",
      body: { email, name, password, teamName },
    });
    tokenStore.save(t);
    setUser(t.user);
  }

  function applyTokens(t: TokenResponse) {
    tokenStore.save(t);
    setUser(t.user);
  }

  function logout() {
    const refreshToken = tokenStore.getRefresh();
    api("/auth/logout", { method: "POST", body: { refreshToken } }).catch(() => {});
    tokenStore.clear();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, login, register, applyTokens, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
