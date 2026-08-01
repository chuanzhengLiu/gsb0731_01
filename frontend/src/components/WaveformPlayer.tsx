import { useEffect, useRef, useState, forwardRef, useImperativeHandle } from "react";
import WaveSurfer from "wavesurfer.js";
import RegionsPlugin, { type Region } from "wavesurfer.js/dist/plugins/regions.js";

export interface WaveformPlayerHandle {
  getCurrentTimeMs: () => number;
  seekMs: (ms: number) => void;
  playPause: () => void;
}

export interface MarkerRegion {
  id: number;
  startTimeMs: number;
  endTimeMs?: number | null;
  color?: string;
}

interface WaveformPlayerProps {
  streamUrl: string;
  peaks?: number[] | null;
  durationMs?: number | null;
  /** Existing markers to render as regions/points on the waveform. */
  regions?: MarkerRegion[];
  /** Enable drag-select to create a new time-range marker (README §4.2). */
  enableDragSelect?: boolean;
  /** Called when the user finishes dragging out a new region. */
  onRegionSelected?: (startMs: number, endMs: number) => void;
  /** Called when clicking an existing region/point. */
  onRegionClick?: (markerId: number) => void;
  onReady?: () => void;
}

/**
 * wavesurfer.js player (README §4.2). Uses server pre-generated peaks when
 * available, supports drag-select to create time-range markers, and renders
 * existing markers as regions (ranges) or markers (points).
 */
export const WaveformPlayer = forwardRef<WaveformPlayerHandle, WaveformPlayerProps>(
  ({ streamUrl, peaks, durationMs, regions, enableDragSelect, onRegionSelected, onRegionClick, onReady }, ref) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const wsRef = useRef<WaveSurfer | null>(null);
    const regionsPluginRef = useRef<ReturnType<typeof RegionsPlugin.create> | null>(null);
    const [playing, setPlaying] = useState(false);
    const [currentTime, setCurrentTime] = useState(0);
    const [duration, setDuration] = useState(0);

    // Keep latest callbacks without re-initialising wavesurfer.
    const cbRef = useRef({ onRegionSelected, onRegionClick });
    cbRef.current = { onRegionSelected, onRegionClick };

    useImperativeHandle(ref, () => ({
      getCurrentTimeMs: () => Math.round((wsRef.current?.getCurrentTime() ?? 0) * 1000),
      seekMs: (ms: number) => {
        const ws = wsRef.current;
        if (!ws) return;
        const total = ws.getDuration();
        if (total > 0) ws.seekTo(Math.min(1, ms / 1000 / total));
      },
      playPause: () => wsRef.current?.playPause(),
    }));

    useEffect(() => {
      if (!containerRef.current) return;

      const regionsPlugin = RegionsPlugin.create();
      regionsPluginRef.current = regionsPlugin;

      const options: Record<string, unknown> = {
        container: containerRef.current,
        waveColor: "#94a3b8",
        progressColor: "#2563eb",
        cursorColor: "#1e293b",
        height: 96,
        url: streamUrl,
        plugins: [regionsPlugin],
      };
      if (peaks && peaks.length > 0) {
        options.peaks = [peaks];
        if (durationMs) options.duration = durationMs / 1000;
      }

      const ws = WaveSurfer.create(options as never);
      wsRef.current = ws;

      ws.on("ready", () => {
        setDuration(ws.getDuration());
        onReady?.();
      });
      ws.on("play", () => setPlaying(true));
      ws.on("pause", () => setPlaying(false));
      ws.on("timeupdate", (t: number) => setCurrentTime(t));

      // Drag-select to create a new region (README §4.2 拖拽选择时间段).
      if (enableDragSelect) {
        regionsPlugin.enableDragSelection({ color: "rgba(37, 99, 235, 0.15)" });
      }

      // Fires when a *new* user-drawn region is created; existing ones are
      // added programmatically before user interaction so we guard with a flag.
      regionsPlugin.on("region-created", (region: Region) => {
        if ((region as unknown as { _fromMarker?: boolean })._fromMarker) return;
        const startMs = Math.round(region.start * 1000);
        const endMs = Math.round(region.end * 1000);
        // A pure click (start≈end) is not a range selection; ignore it here.
        if (endMs - startMs >= 200 && cbRef.current.onRegionSelected) {
          cbRef.current.onRegionSelected(startMs, endMs);
        }
        // Remove the transient drawing; the parent re-renders from server state.
        region.remove();
      });

      regionsPlugin.on("region-clicked", (region: Region, e: MouseEvent) => {
        e.stopPropagation();
        const markerId = Number((region as unknown as { _markerId?: number })._markerId);
        if (markerId && cbRef.current.onRegionClick) {
          cbRef.current.onRegionClick(markerId);
        }
      });

      return () => {
        ws.destroy();
        wsRef.current = null;
        regionsPluginRef.current = null;
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [streamUrl]);

    // Render existing markers as regions whenever they change.
    useEffect(() => {
      const rp = regionsPluginRef.current;
      const ws = wsRef.current;
      if (!rp || !ws) return;

      const paint = () => {
        rp.clearRegions();
        (regions ?? []).forEach((m) => {
          const startSec = m.startTimeMs / 1000;
          const isRange = m.endTimeMs != null && m.endTimeMs > m.startTimeMs;
          const region = rp.addRegion({
            start: startSec,
            end: isRange ? (m.endTimeMs as number) / 1000 : startSec + 0.05,
            color: m.color ?? "rgba(234, 88, 12, 0.25)",
            drag: false,
            resize: false,
          });
          (region as unknown as { _fromMarker?: boolean; _markerId?: number })._fromMarker = true;
          (region as unknown as { _markerId?: number })._markerId = m.id;
        });
      };

      if (ws.getDuration() > 0) {
        paint();
      } else {
        ws.once("ready", paint);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [regions]);

    // Keyboard shortcuts: space play/pause, arrows nudge (README §4.2)
    useEffect(() => {
      function onKey(e: KeyboardEvent) {
        const ws = wsRef.current;
        if (!ws) return;
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        if (e.code === "Space") {
          e.preventDefault();
          ws.playPause();
        } else if (e.code === "ArrowLeft") {
          ws.setTime(Math.max(0, ws.getCurrentTime() - 2));
        } else if (e.code === "ArrowRight") {
          ws.setTime(Math.min(ws.getDuration(), ws.getCurrentTime() + 2));
        }
      }
      window.addEventListener("keydown", onKey);
      return () => window.removeEventListener("keydown", onKey);
    }, []);

    return (
      <div className="space-y-2">
        <div ref={containerRef} className="rounded-md border bg-background p-2" />
        <div className="flex items-center gap-3 text-sm">
          <button
            className="rounded bg-primary px-3 py-1 text-primary-foreground"
            onClick={() => wsRef.current?.playPause()}
          >
            {playing ? "暂停" : "播放"}
          </button>
          <span className="tabular-nums text-muted-foreground">
            {fmt(currentTime)} / {fmt(duration)}
          </span>
          <span className="text-xs text-muted-foreground">
            快捷键：空格 播放/暂停，←/→ 微调 2 秒{enableDragSelect ? "；波形上拖拽选段可加时间段标记" : ""}
          </span>
        </div>
      </div>
    );
  }
);
WaveformPlayer.displayName = "WaveformPlayer";

function fmt(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}
