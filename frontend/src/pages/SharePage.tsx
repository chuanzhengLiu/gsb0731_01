import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { SharedEpisodeView } from "@/lib/types";
import { WaveformPlayer } from "@/components/WaveformPlayer";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

/**
 * Public guest view of a shared episode (README §3.1). No login required; the
 * link's token is validated server-side (7-day expiry) and the access logged.
 * Read-only: audio playback, markers and transcript.
 */
export function SharePage() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const [view, setView] = useState<SharedEpisodeView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setError("缺少分享令牌");
      return;
    }
    fetch(`/api/share/${encodeURIComponent(token)}`)
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.message || "分享链接无效或已过期");
        }
        return res.json();
      })
      .then(setView)
      .catch((e) => setError(e instanceof Error ? e.message : "加载失败"));
  }, [token]);

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
        <Card className="max-w-md"><CardContent className="py-8 text-center text-sm text-destructive">{error}</CardContent></Card>
      </div>
    );
  }

  if (!view) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/30">
        <p className="text-sm text-muted-foreground">加载中...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-muted/30 py-8">
      <div className="container max-w-3xl space-y-6">
        <div>
          <p className="text-sm text-muted-foreground">{view.podcastName}（访客分享）</p>
          <h1 className="text-2xl font-bold">EP{view.number} · {view.title}</h1>
          {view.theme && <p className="text-muted-foreground">{view.theme}</p>}
        </div>

        {view.audio ? (
          <Card>
            <CardHeader><CardTitle>音频 v{view.audio.versionNumber}</CardTitle></CardHeader>
            <CardContent>
              <WaveformPlayer
                streamUrl={view.audio.streamUrl}
                durationMs={view.audio.durationMs}
                regions={view.markers.map((m) => ({
                  id: m.id, startTimeMs: m.startTimeMs, endTimeMs: m.endTimeMs,
                }))}
              />
            </CardContent>
          </Card>
        ) : (
          <p className="text-sm text-muted-foreground">该单集暂无可播放音频。</p>
        )}

        {view.markers.length > 0 && (
          <Card>
            <CardHeader><CardTitle>标记</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {view.markers.map((m) => (
                <div key={m.id} className="flex items-center gap-3 rounded-md border p-2 text-sm">
                  <span className="tabular-nums text-muted-foreground">
                    {m.endTimeMs != null ? `${fmtMs(m.startTimeMs)}–${fmtMs(m.endTimeMs)}` : fmtMs(m.startTimeMs)}
                  </span>
                  <Badge variant="outline">{m.type}</Badge>
                  <span className="flex-1">{m.description}</span>
                </div>
              ))}
            </CardContent>
          </Card>
        )}

        {view.transcript.length > 0 && (
          <Card>
            <CardHeader><CardTitle>转写文本</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {view.transcript.map((s) => (
                <div key={s.id} className="text-sm">
                  {s.speaker && (
                    <span className="mr-2 font-medium"
                      style={s.speakerColor ? { color: s.speakerColor } : undefined}>
                      {s.speaker}
                    </span>
                  )}
                  {s.text}
                </div>
              ))}
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}

function fmtMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  return `${Math.floor(total / 60)}:${(total % 60).toString().padStart(2, "0")}`;
}
