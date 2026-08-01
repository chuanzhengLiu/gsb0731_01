import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { MarkerType, TranscriptSegment } from "@/lib/types";
import { MARKER_TYPE_LABELS } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

const MARKER_TYPES = Object.keys(MARKER_TYPE_LABELS) as MarkerType[];

interface Props {
  audioVersionId: number;
  canAnnotate: boolean;
  onSeek: (ms: number) => void;
  onMarkerAdded: () => void;
}

/**
 * Transcript alignment panel (README §4.3): click a sentence to seek, edit text
 * (timestamps auto-adjust server-side), add a marker straight from a segment,
 * speakers shown in their assigned colour.
 */
export function TranscriptPanel({ audioVersionId, canAnnotate, onSeek, onMarkerAdded }: Props) {
  const [segments, setSegments] = useState<TranscriptSegment[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editText, setEditText] = useState("");
  const [markerType, setMarkerType] = useState<MarkerType>("MISSPEAK");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setSegments(await api<TranscriptSegment[]>(`/audio-versions/${audioVersionId}/transcript`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载转写失败");
    }
  }, [audioVersionId]);

  useEffect(() => {
    load();
  }, [load]);

  async function regenerate() {
    setBusy(true);
    setError(null);
    try {
      setSegments(await api<TranscriptSegment[]>(`/audio-versions/${audioVersionId}/transcript`, {
        method: "POST",
      }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "生成转写失败");
    } finally {
      setBusy(false);
    }
  }

  function startEdit(s: TranscriptSegment) {
    setEditingId(s.id);
    setEditText(s.text);
  }

  async function saveEdit(id: number) {
    setError(null);
    try {
      await api(`/transcript-segments/${id}`, { method: "PUT", body: { text: editText } });
      setEditingId(null);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存失败");
    }
  }

  async function addMarker(id: number, asRange: boolean) {
    setError(null);
    try {
      await api(`/transcript-segments/${id}/markers`, {
        method: "POST",
        body: { type: markerType, asRange, description: null },
      });
      onMarkerAdded();
    } catch (e) {
      setError(e instanceof Error ? e.message : "添加标记失败");
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>转写文本对齐</CardTitle>
        {canAnnotate && (
          <div className="flex items-center gap-2">
            <select value={markerType} onChange={(e) => setMarkerType(e.target.value as MarkerType)}
              className="h-8 rounded-md border border-input bg-background px-2 text-xs">
              {MARKER_TYPES.map((t) => <option key={t} value={t}>{MARKER_TYPE_LABELS[t]}</option>)}
            </select>
            <Button variant="outline" size="sm" onClick={regenerate} disabled={busy}>
              {busy ? "生成中..." : "重新生成"}
            </Button>
          </div>
        )}
      </CardHeader>
      <CardContent className="space-y-2">
        {error && <p className="text-sm text-destructive">{error}</p>}
        {segments.length === 0 && (
          <p className="text-sm text-muted-foreground">
            暂无转写文本{canAnnotate ? "，可点击「重新生成」" : ""}。
          </p>
        )}
        {segments.map((s) => (
          <div key={s.id} className="rounded-md border p-2 text-sm">
            <div className="flex items-center gap-2">
              <button className="tabular-nums text-primary hover:underline"
                onClick={() => onSeek(s.startTimeMs)}>
                {fmtMs(s.startTimeMs)}
              </button>
              {s.speaker && (
                <Badge variant="outline"
                  style={s.speakerColor ? { borderColor: s.speakerColor, color: s.speakerColor } : undefined}>
                  {s.speaker}
                </Badge>
              )}
              {s.edited && <Badge variant="secondary">已修正</Badge>}
            </div>
            {editingId === s.id ? (
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <Input value={editText} onChange={(e) => setEditText(e.target.value)}
                  className="flex-1 min-w-[240px]" />
                <Button size="sm" onClick={() => saveEdit(s.id)}>保存</Button>
                <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>取消</Button>
              </div>
            ) : (
              <div className="mt-1 flex items-start gap-2">
                <span className="flex-1">{s.text}</span>
                {canAnnotate && (
                  <div className="flex shrink-0 gap-1">
                    <Button size="sm" variant="ghost" onClick={() => addMarker(s.id, false)}>加点标记</Button>
                    <Button size="sm" variant="ghost" onClick={() => addMarker(s.id, true)}>加段标记</Button>
                    <Button size="sm" variant="ghost" onClick={() => startEdit(s)}>修正</Button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function fmtMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  return `${Math.floor(total / 60)}:${(total % 60).toString().padStart(2, "0")}`;
}
