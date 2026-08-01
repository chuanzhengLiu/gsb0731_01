import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Distribution, DistributionStatus, Platform } from "@/lib/types";
import { DISTRIBUTION_STATUS_LABELS } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

const STATUSES: DistributionStatus[] = ["NOT_STARTED", "SUBMITTED", "IN_REVIEW", "PUBLISHED", "REJECTED"];

interface Props {
  episodeId: number;
  podcastId?: number;
}

/**
 * Per-episode distribution status (README §4.4): pick platforms and track each
 * one's status independently. Writes require OPERATOR/producer-level; the RSS
 * feed link is exposed here too.
 */
export function DistributionCard({ episodeId, podcastId }: Props) {
  const { user } = useAuth();
  const [platforms, setPlatforms] = useState<Platform[]>([]);
  const [dists, setDists] = useState<Distribution[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [addPlatform, setAddPlatform] = useState("");

  const canManage = ["ADMIN", "PRODUCER", "OPERATOR"].includes(user?.role ?? "");

  const load = useCallback(async () => {
    try {
      setDists(await api<Distribution[]>(`/episodes/${episodeId}/distributions`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }, [episodeId]);

  useEffect(() => {
    load();
    api<Platform[]>("/platforms").then(setPlatforms).catch(() => {});
  }, [load]);

  async function addDistribution() {
    if (!addPlatform) return;
    setError(null);
    try {
      await api(`/episodes/${episodeId}/distributions`, {
        method: "POST",
        body: { platformId: Number(addPlatform), platformDataJson: null },
      });
      setAddPlatform("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "添加平台失败");
    }
  }

  async function changeStatus(d: Distribution, status: DistributionStatus) {
    if (status === d.status) return;
    setError(null);
    try {
      await api(`/distributions/${d.id}/status`, { method: "PATCH", body: { status } });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "更新状态失败");
    }
  }

  const usedPlatformIds = new Set(dists.map((d) => d.platformId));
  const available = platforms.filter((p) => !usedPlatformIds.has(p.id));

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>分发状态</CardTitle>
        {podcastId != null && (
          <a href={`/api/podcasts/${podcastId}/rss`} target="_blank" rel="noreferrer"
            className="text-sm text-primary hover:underline">查看 RSS Feed</a>
        )}
      </CardHeader>
      <CardContent className="space-y-3">
        {error && <p className="text-sm text-destructive">{error}</p>}

        {canManage && available.length > 0 && (
          <div className="flex items-center gap-2">
            <select value={addPlatform} onChange={(e) => setAddPlatform(e.target.value)}
              className="h-9 rounded-md border border-input bg-background px-2 text-sm">
              <option value="">选择要分发的平台</option>
              {available.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            <Button size="sm" variant="outline" onClick={addDistribution}>添加</Button>
          </div>
        )}

        {dists.length === 0 && <p className="text-sm text-muted-foreground">尚未选择分发平台</p>}
        {dists.map((d) => (
          <div key={d.id} className="flex flex-wrap items-center gap-3 rounded-md border p-2 text-sm">
            <Badge variant="outline">{d.platformName}</Badge>
            <span className="flex-1" />
            {canManage ? (
              <select value={d.status}
                onChange={(e) => changeStatus(d, e.target.value as DistributionStatus)}
                className="h-8 rounded-md border border-input bg-background px-2 text-xs">
                {STATUSES.map((s) => <option key={s} value={s}>{DISTRIBUTION_STATUS_LABELS[s]}</option>)}
              </select>
            ) : (
              <Badge variant="secondary">{DISTRIBUTION_STATUS_LABELS[d.status]}</Badge>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
