import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "@/lib/api";
import type {
  AudioVersion,
  Episode,
  MarkerStatus,
  MarkerType,
  TimelineMarker,
} from "@/lib/types";
import {
  MARKER_STATUS_LABELS,
  MARKER_TYPE_LABELS,
  EPISODE_STATUS_LABELS,
  ALLOWED_STATUS_TRANSITIONS,
} from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  WaveformPlayer,
  type WaveformPlayerHandle,
  type MarkerRegion,
} from "@/components/WaveformPlayer";
import { TranscriptPanel } from "@/components/TranscriptPanel";
import { TaskBoard } from "@/components/TaskBoard";
import { StructureComparisonCard } from "@/components/StructureComparisonCard";
import { ShareLinksCard } from "@/components/ShareLinksCard";
import { DistributionCard } from "@/components/DistributionCard";
import { Upload } from "lucide-react";

const MARKER_TYPES = Object.keys(MARKER_TYPE_LABELS) as MarkerType[];
const MARKER_STATUSES = Object.keys(MARKER_STATUS_LABELS) as MarkerStatus[];

interface VersionComparison {
  fromVersionId: number;
  fromVersionNumber: number;
  fromDurationMs: number | null;
  toVersionId: number;
  toVersionNumber: number;
  toDurationMs: number | null;
  durationDiffMs: number | null;
}

export function EpisodeWorkspacePage() {
  const { episodeId } = useParams();
  const { user } = useAuth();

  const [episode, setEpisode] = useState<Episode | null>(null);
  const [versions, setVersions] = useState<AudioVersion[]>([]);
  const [activeVersion, setActiveVersion] = useState<AudioVersion | null>(null);
  const [peaks, setPeaks] = useState<number[] | null>(null);
  const [markers, setMarkers] = useState<TimelineMarker[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  // Marker filters (README §4.2)
  const [filterType, setFilterType] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [keyword, setKeyword] = useState("");

  // New marker form
  const [newType, setNewType] = useState<MarkerType>("MISSPEAK");
  const [newDesc, setNewDesc] = useState("");

  // Version comparison
  const [compareFrom, setCompareFrom] = useState<string>("");
  const [compareTo, setCompareTo] = useState<string>("");
  const [comparison, setComparison] = useState<VersionComparison | null>(null);

  const playerRef = useRef<WaveformPlayerHandle>(null);
  const canUpload = ["ADMIN", "PRODUCER", "EDITOR"].includes(user?.role ?? "");

  const loadEpisode = useCallback(async () => {
    setEpisode(await api<Episode>(`/episodes/${episodeId}`));
  }, [episodeId]);

  const loadVersions = useCallback(async () => {
    const list = await api<AudioVersion[]>(`/episodes/${episodeId}/audio-versions`);
    setVersions(list);
    setActiveVersion((prev) => prev ?? list.find((v) => !v.archived) ?? list[0] ?? null);
  }, [episodeId]);

  const loadMarkers = useCallback(async () => {
    if (!activeVersion) return;
    const params = new URLSearchParams();
    if (filterType) params.set("type", filterType);
    if (filterStatus) params.set("status", filterStatus);
    if (keyword.trim()) params.set("keyword", keyword.trim());
    const qs = params.toString();
    setMarkers(
      await api<TimelineMarker[]>(
        `/audio-versions/${activeVersion.id}/markers${qs ? `?${qs}` : ""}`
      )
    );
  }, [activeVersion, filterType, filterStatus, keyword]);

  const loadPeaks = useCallback(async () => {
    if (!activeVersion || !activeVersion.hasWaveform) {
      setPeaks(null);
      return;
    }
    try {
      const wf = await api<{ peaks: number[] }>(`/audio-versions/${activeVersion.id}/waveform`);
      setPeaks(wf?.peaks ?? null);
    } catch {
      setPeaks(null);
    }
  }, [activeVersion]);

  useEffect(() => {
    loadEpisode().catch((e) => setError(e.message));
    loadVersions().catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [episodeId]);

  useEffect(() => {
    loadPeaks();
    loadMarkers().catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeVersion]);

  useEffect(() => {
    loadMarkers().catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterType, filterStatus, keyword]);

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const created = await api<AudioVersion>(`/episodes/${episodeId}/audio-versions`, {
        method: "POST",
        body: fd,
        isForm: true,
      });
      await loadVersions();
      setActiveVersion(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : "上传失败");
    } finally {
      setUploading(false);
      e.target.value = "";
    }
  }

  async function addMarkerAtPlayhead() {
    if (!activeVersion) return;
    const ms = playerRef.current?.getCurrentTimeMs() ?? 0;
    try {
      await api<TimelineMarker>(`/audio-versions/${activeVersion.id}/markers`, {
        method: "POST",
        body: { startTimeMs: ms, type: newType, description: newDesc || null },
      });
      setNewDesc("");
      loadMarkers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "添加标记失败");
    }
  }

  // Drag-select on the waveform creates a time-range marker (README §4.2).
  async function addRangeMarker(startMs: number, endMs: number) {
    if (!activeVersion) return;
    try {
      await api<TimelineMarker>(`/audio-versions/${activeVersion.id}/markers`, {
        method: "POST",
        body: { startTimeMs: startMs, endTimeMs: endMs, type: newType, description: newDesc || null },
      });
      setNewDesc("");
      loadMarkers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "添加时间段标记失败");
    }
  }

  // Status flow uses the dedicated transition endpoint (README §4.2).
  async function changeMarkerStatus(m: TimelineMarker, status: MarkerStatus) {
    if (status === m.status) return;
    setError(null);
    try {
      await api(`/markers/${m.id}/status`, { method: "PATCH", body: { status } });
      loadMarkers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "状态流转失败");
    }
  }

  async function deleteMarker(m: TimelineMarker) {
    try {
      await api(`/markers/${m.id}`, { method: "DELETE" });
      loadMarkers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败");
    }
  }

  async function runComparison() {
    if (!compareFrom || !compareTo || compareFrom === compareTo) {
      setError("请选择两个不同的版本进行对比");
      return;
    }
    setError(null);
    try {
      setComparison(
        await api<VersionComparison>(
          `/audio-versions/compare?from=${compareFrom}&to=${compareTo}`
        )
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "版本对比失败");
    }
  }

  // "M" adds a point marker at the current playhead (README §4.2)
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      if (e.key === "m" || e.key === "M") {
        e.preventDefault();
        addMarkerAtPlayhead();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeVersion, newType, newDesc]);

  const regions: MarkerRegion[] = markers.map((m) => ({
    id: m.id,
    startTimeMs: m.startTimeMs,
    endTimeMs: m.endTimeMs,
  }));

  return (
    <div className="space-y-6">
      <div>
        {episode && (
          <Link to={`/podcasts/${episode.podcastId}`}
            className="text-sm text-muted-foreground hover:underline">
            ← 返回单集列表
          </Link>
        )}
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold">
            {episode ? `EP${episode.number} · ${episode.title}` : "加载中..."}
          </h1>
          {episode && <Badge variant="secondary">{EPISODE_STATUS_LABELS[episode.status]}</Badge>}
        </div>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {/* Audio versions + upload */}
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <CardTitle>音频版本</CardTitle>
          {canUpload && (
            <label className="inline-flex cursor-pointer items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground">
              <Upload className="h-4 w-4" />
              {uploading ? "上传中..." : "上传新版本"}
              <input type="file" accept=".wav,.mp3,.m4a" className="hidden"
                onChange={handleUpload} disabled={uploading} />
            </label>
          )}
        </CardHeader>
        <CardContent className="space-y-4">
          {versions.length === 0 ? (
            <p className="text-sm text-muted-foreground">尚无音频，请上传粗剪版本</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {versions.map((v) => (
                <button key={v.id}
                  onClick={() => setActiveVersion(v)}
                  className={`rounded-md border px-3 py-1 text-sm ${
                    activeVersion?.id === v.id ? "border-primary bg-primary/10" : ""
                  } ${v.archived ? "opacity-50" : ""}`}>
                  v{v.versionNumber}
                  {v.durationMs != null && ` · ${fmtMs(v.durationMs)}`}
                  {v.archived && "（已归档）"}
                </button>
              ))}
            </div>
          )}

          {/* Version comparison (README §4.2 版本对比) */}
          {versions.length >= 2 && (
            <div className="flex flex-wrap items-end gap-3 rounded-md border p-3">
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">旧版本</label>
                <select value={compareFrom} onChange={(e) => setCompareFrom(e.target.value)}
                  className="flex h-9 rounded-md border border-input bg-background px-2 text-sm">
                  <option value="">选择</option>
                  {versions.map((v) => <option key={v.id} value={v.id}>v{v.versionNumber}</option>)}
                </select>
              </div>
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">新版本</label>
                <select value={compareTo} onChange={(e) => setCompareTo(e.target.value)}
                  className="flex h-9 rounded-md border border-input bg-background px-2 text-sm">
                  <option value="">选择</option>
                  {versions.map((v) => <option key={v.id} value={v.id}>v{v.versionNumber}</option>)}
                </select>
              </div>
              <Button variant="outline" onClick={runComparison}>对比时长</Button>
              {comparison && (
                <div className="text-sm text-muted-foreground">
                  v{comparison.fromVersionNumber}（{fmtMsOrNA(comparison.fromDurationMs)}）→
                  v{comparison.toVersionNumber}（{fmtMsOrNA(comparison.toDurationMs)}）：
                  {comparison.durationDiffMs == null ? (
                    <span> 时长未知</span>
                  ) : (
                    <span className={comparison.durationDiffMs >= 0 ? "text-emerald-600" : "text-destructive"}>
                      {" "}{comparison.durationDiffMs >= 0 ? "+" : "-"}{fmtMs(Math.abs(comparison.durationDiffMs))}
                    </span>
                  )}
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Waveform + markers */}
      {activeVersion && !activeVersion.archived && (
        <Card>
          <CardHeader><CardTitle>时间轴标注 · v{activeVersion.versionNumber}</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <WaveformPlayer
              ref={playerRef}
              streamUrl={activeVersion.streamUrl}
              peaks={peaks}
              durationMs={activeVersion.durationMs}
              regions={regions}
              enableDragSelect={canUpload || user?.role === "HOST"}
              onRegionSelected={addRangeMarker}
              onRegionClick={(id) => {
                const m = markers.find((x) => x.id === id);
                if (m) playerRef.current?.seekMs(m.startTimeMs);
              }}
            />

            {/* Add marker */}
            <div className="flex flex-wrap items-end gap-3 rounded-md border p-3">
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">类型</label>
                <select value={newType} onChange={(e) => setNewType(e.target.value as MarkerType)}
                  className="flex h-9 rounded-md border border-input bg-background px-2 text-sm">
                  {MARKER_TYPES.map((t) => (
                    <option key={t} value={t}>{MARKER_TYPE_LABELS[t]}</option>
                  ))}
                </select>
              </div>
              <div className="flex-1 min-w-[200px] space-y-1">
                <label className="text-xs text-muted-foreground">描述</label>
                <Input value={newDesc} onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="点标记：快捷键 M / 按钮；时间段标记：波形上拖拽选段" />
              </div>
              <Button onClick={addMarkerAtPlayhead}>在播放位置加点标记</Button>
            </div>

            {/* Filters */}
            <div className="flex flex-wrap items-center gap-3">
              <select value={filterType} onChange={(e) => setFilterType(e.target.value)}
                className="h-9 rounded-md border border-input bg-background px-2 text-sm">
                <option value="">全部类型</option>
                {MARKER_TYPES.map((t) => <option key={t} value={t}>{MARKER_TYPE_LABELS[t]}</option>)}
              </select>
              <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}
                className="h-9 rounded-md border border-input bg-background px-2 text-sm">
                <option value="">全部状态</option>
                {MARKER_STATUSES.map((s) => <option key={s} value={s}>{MARKER_STATUS_LABELS[s]}</option>)}
              </select>
              <Input value={keyword} onChange={(e) => setKeyword(e.target.value)}
                placeholder="搜索描述关键词" className="w-56" />
            </div>

            {/* Marker list */}
            <div className="space-y-2">
              {markers.map((m) => (
                <div key={m.id} className="flex items-center gap-3 rounded-md border p-2 text-sm">
                  <button className="tabular-nums text-primary hover:underline"
                    onClick={() => playerRef.current?.seekMs(m.startTimeMs)}>
                    {m.endTimeMs != null
                      ? `${fmtMs(m.startTimeMs)}–${fmtMs(m.endTimeMs)}`
                      : fmtMs(m.startTimeMs)}
                  </button>
                  <Badge>{MARKER_TYPE_LABELS[m.type]}</Badge>
                  {m.endTimeMs != null && <Badge variant="outline">段</Badge>}
                  <span className="flex-1">{m.description || <span className="text-muted-foreground">（无描述）</span>}</span>
                  {/* Status flow: only offer valid next states (+ current). */}
                  <select value={m.status}
                    onChange={(e) => changeMarkerStatus(m, e.target.value as MarkerStatus)}
                    className="h-8 rounded-md border border-input bg-background px-2 text-xs">
                    {statusOptionsFor(m.status).map((s) => (
                      <option key={s} value={s}>{MARKER_STATUS_LABELS[s]}</option>
                    ))}
                  </select>
                  <Button variant="ghost" size="sm" onClick={() => deleteMarker(m)}>删除</Button>
                </div>
              ))}
              {markers.length === 0 && <p className="text-sm text-muted-foreground">暂无标记</p>}
            </div>
          </CardContent>
        </Card>
      )}

      {activeVersion && !activeVersion.archived && (
        <TranscriptPanel
          audioVersionId={activeVersion.id}
          canAnnotate={canUpload || user?.role === "HOST"}
          onSeek={(ms) => playerRef.current?.seekMs(ms)}
          onMarkerAdded={loadMarkers}
        />
      )}

      {/* Structure template comparison (README §4.1) */}
      <StructureComparisonCard episodeId={Number(episodeId)} />

      {/* Task board (README §4.1) */}
      <TaskBoard episodeId={Number(episodeId)} />

      {/* Distribution status (README §4.4) */}
      <DistributionCard episodeId={Number(episodeId)} podcastId={episode?.podcastId} />

      {/* Guest share links (README §3.1) */}
      {canUpload && <ShareLinksCard episodeId={Number(episodeId)} />}

      {activeVersion?.archived && (
        <p className="text-sm text-muted-foreground">该版本已归档，仅支持下载，不在线播放。</p>
      )}
    </div>
  );
}

/** Current status plus its allowed transition targets (mirrors backend). */
function statusOptionsFor(current: MarkerStatus): MarkerStatus[] {
  const set = new Set<MarkerStatus>([current, ...ALLOWED_STATUS_TRANSITIONS[current]]);
  return MARKER_STATUSES.filter((s) => set.has(s));
}

function fmtMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function fmtMsOrNA(ms: number | null): string {
  return ms == null ? "未知" : fmtMs(ms);
}
