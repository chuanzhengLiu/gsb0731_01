import type { TokenResponse } from "./types";

const ACCESS_KEY = "pc_access_token";
const REFRESH_KEY = "pc_refresh_token";
const USER_KEY = "pc_user";

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  getUser: () => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  },
  save: (t: TokenResponse) => {
    localStorage.setItem(ACCESS_KEY, t.accessToken);
    localStorage.setItem(REFRESH_KEY, t.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(t.user));
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let refreshing: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) return false;
  try {
    const res = await fetch("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = (await res.json()) as TokenResponse;
    tokenStore.save(data);
    return true;
  } catch {
    return false;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  isForm?: boolean;
  retry?: boolean;
}

/** Fetch wrapper: attaches JWT, transparently refreshes on 401 once. */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, isForm = false, retry = true } = opts;
  const headers: Record<string, string> = {};
  const access = tokenStore.getAccess();
  if (access) headers["Authorization"] = `Bearer ${access}`;

  let payload: BodyInit | undefined;
  if (body !== undefined) {
    if (isForm) {
      payload = body as FormData;
    } else {
      headers["Content-Type"] = "application/json";
      payload = JSON.stringify(body);
    }
  }

  const res = await fetch(`/api${path}`, { method, headers, body: payload });

  if (res.status === 401 && retry && access) {
    if (!refreshing) refreshing = doRefresh();
    const ok = await refreshing;
    refreshing = null;
    if (ok) return api<T>(path, { ...opts, retry: false });
    tokenStore.clear();
    throw new ApiError(401, "会话已过期，请重新登录");
  }

  if (!res.ok) {
    let message = `请求失败 (${res.status})`;
    try {
      const err = await res.json();
      message = err.message || message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
