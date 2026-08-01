import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api";
import type { TeamStats } from "@/lib/types";
import { ROLE_LABELS } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Team analytics (README §4.6): production metrics only — marker counts, cycle
 * time, member efficiency and distribution coverage. No play-count stats (§9).
 */
export function StatsPage() {
  const [stats, setStats] = useState<TeamStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<TeamStats>("/stats/team").then(setStats)
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <Link to="/" className="text-sm text-muted-foreground hover:underline">← 返回节目列表</Link>
        <h1 className="text-2xl font-bold">数据统计</h1>
        <p className="text-sm text-muted-foreground">仅制作协作指标；按需求不做播放量统计。</p>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {!stats && !error && <p className="text-sm text-muted-foreground">加载中...</p>}

      {stats && (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader><CardTitle>每集平均标记数</CardTitle></CardHeader>
              <CardContent className="text-2xl font-bold">{stats.avgMarkersPerEpisode.toFixed(1)}</CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>逾期任务总数</CardTitle></CardHeader>
              <CardContent className="text-2xl font-bold">{stats.totalOverdueTasks}</CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader><CardTitle>单集数据</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {stats.episodes.length === 0 && <p className="text-sm text-muted-foreground">暂无数据</p>}
              {stats.episodes.map((e) => (
                <div key={e.episodeId} className="flex flex-wrap items-center gap-3 rounded-md border p-2 text-sm">
                  <Link to={`/episodes/${e.episodeId}`} className="font-medium text-primary hover:underline">
                    EP{e.episodeNumber} · {e.episodeTitle}
                  </Link>
                  {e.podcastName && <span className="text-muted-foreground">{e.podcastName}</span>}
                  <span className="flex-1" />
                  <span className="text-muted-foreground">时长 {fmtMsOrNA(e.durationMs ?? null)}</span>
                  <span className="text-muted-foreground">标记 {e.markerCount}</span>
                  <span className="text-muted-foreground">版本 {e.versionCount}</span>
                  <span className="text-muted-foreground">周期 {e.cycleDays == null ? "—" : e.cycleDays + " 天"}</span>
                </div>
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>团队效率</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {stats.members.map((m) => (
                <div key={m.userId} className="flex flex-wrap items-center gap-3 rounded-md border p-2 text-sm">
                  <span className="font-medium">{m.name}</span>
                  <span className="text-muted-foreground">{ROLE_LABELS[m.role]}</span>
                  <span className="flex-1" />
                  <span className="text-muted-foreground">提出标记 {m.markersRaised}</span>
                  <span className="text-muted-foreground">进行中任务 {m.openTasks}</span>
                  <span className={m.overdueTasks > 0 ? "text-destructive" : "text-muted-foreground"}>
                    逾期 {m.overdueTasks}
                  </span>
                </div>
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>分发覆盖</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {stats.platforms.length === 0 && <p className="text-sm text-muted-foreground">暂无分发数据</p>}
              {stats.platforms.map((p) => (
                <div key={p.platformId} className="flex flex-wrap items-center gap-3 rounded-md border p-2 text-sm">
                  <span className="font-medium">{p.platformName}</span>
                  <span className="flex-1" />
                  <span className="text-muted-foreground">上架率 {(p.publishRate * 100).toFixed(0)}%（{p.published}/{p.total}）</span>
                  <span className="text-muted-foreground">
                    平均审核 {p.avgReviewHours == null ? "—" : p.avgReviewHours.toFixed(1) + " 小时"}
                  </span>
                </div>
              ))}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function fmtMsOrNA(ms: number | null): string {
  if (ms == null) return "未知";
  const total = Math.floor(ms / 1000);
  return `${Math.floor(total / 60)}:${(total % 60).toString().padStart(2, "0")}`;
}
