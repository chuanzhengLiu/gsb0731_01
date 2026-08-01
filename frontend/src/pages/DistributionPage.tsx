import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api";
import type { CalendarEntry, Platform, PlatformAccount } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

/**
 * Distribution management (README §4.4): maintain platform accounts and view
 * the publish calendar. Per-episode distribution status is edited inside each
 * episode's workspace. Writes require OPERATOR/producer-level.
 */
export function DistributionPage() {
  const { user } = useAuth();
  const [platforms, setPlatforms] = useState<Platform[]>([]);
  const [accounts, setAccounts] = useState<PlatformAccount[]>([]);
  const [calendar, setCalendar] = useState<CalendarEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [platformId, setPlatformId] = useState("");
  const [accountName, setAccountName] = useState("");
  const [accountUrl, setAccountUrl] = useState("");

  const canManage = ["ADMIN", "PRODUCER", "OPERATOR"].includes(user?.role ?? "");

  const load = useCallback(async () => {
    try {
      setPlatforms(await api<Platform[]>("/platforms"));
      setAccounts(await api<PlatformAccount[]>("/platform-accounts"));
      setCalendar(await api<CalendarEntry[]>("/distributions/calendar"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function saveAccount(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/platform-accounts", {
        method: "POST",
        body: { platformId: Number(platformId), accountName, accountUrl: accountUrl || null },
      });
      setAccountName("");
      setAccountUrl("");
      setPlatformId("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存账号失败");
    }
  }

  async function removeAccount(id: number) {
    setError(null);
    try {
      await api(`/platform-accounts/${id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/" className="text-sm text-muted-foreground hover:underline">← 返回节目列表</Link>
        <h1 className="text-2xl font-bold">分发管理</h1>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {canManage && (
        <Card>
          <CardHeader><CardTitle>平台账号维护</CardTitle></CardHeader>
          <CardContent>
            <form onSubmit={saveAccount} className="flex flex-wrap items-end gap-3">
              <div className="space-y-1">
                <Label>平台</Label>
                <select value={platformId} onChange={(e) => setPlatformId(e.target.value)} required
                  className="flex h-10 rounded-md border border-input bg-background px-3 text-sm">
                  <option value="">选择平台</option>
                  {platforms.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </div>
              <div className="flex-1 min-w-[180px] space-y-1">
                <Label>账号名称</Label>
                <Input value={accountName} onChange={(e) => setAccountName(e.target.value)} required />
              </div>
              <div className="flex-1 min-w-[180px] space-y-1">
                <Label>主页链接</Label>
                <Input value={accountUrl} onChange={(e) => setAccountUrl(e.target.value)} placeholder="可选" />
              </div>
              <Button type="submit">保存账号</Button>
            </form>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader><CardTitle>已维护账号</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {accounts.length === 0 && <p className="text-sm text-muted-foreground">暂无平台账号</p>}
          {accounts.map((a) => (
            <div key={a.id} className="flex items-center gap-3 rounded-md border p-2 text-sm">
              <Badge variant="outline">{a.platformName}</Badge>
              <span className="font-medium">{a.accountName}</span>
              {a.accountUrl && (
                <a href={a.accountUrl} target="_blank" rel="noreferrer"
                  className="text-primary hover:underline">主页</a>
              )}
              <span className="flex-1" />
              {canManage && <Button variant="ghost" size="sm" onClick={() => removeAccount(a.id)}>删除</Button>}
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>发布日历</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {calendar.length === 0 && <p className="text-sm text-muted-foreground">暂无已排期的发布计划</p>}
          {calendar.map((c) => (
            <Link key={c.episodeId} to={`/episodes/${c.episodeId}`}
              className="flex items-center gap-3 rounded-md border p-2 text-sm hover:bg-muted/50">
              <span className="tabular-nums text-muted-foreground">{c.date}</span>
              <span className="font-medium">EP{c.episodeNumber} · {c.episodeTitle}</span>
              {c.podcastName && <span className="text-muted-foreground">{c.podcastName}</span>}
              <span className="flex-1" />
              <Badge variant="secondary">{c.status}</Badge>
            </Link>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
