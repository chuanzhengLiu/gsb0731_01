import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Loader2,
  ArrowLeft,
  Mic,
  Radio,
  AlertTriangle,
  Tag,
  FileAudio,
  Clock,
} from "lucide-react";
import { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  EPISODE_STATUS_LABELS,
  MARKER_TYPE_LABELS,
  MARKER_STATUS_LABELS,
  formatTime,
} from "@/lib/utils";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

interface SharedAudioVersion {
  id: string | number;
  versionNumber: number;
  fileUrl?: string | null;
  durationMs?: number | null;
  waveformUrl?: string | null;
  createdAt?: string;
}

interface SharedMarker {
  id: string | number;
  startTimeMs: number;
  endTimeMs?: number | null;
  type: string;
  description?: string | null;
  status: string;
}

interface SharedEpisode {
  episodeId: string | number;
  number?: number | null;
  title: string;
  status: string;
  finalAudioUrl?: string | null;
  podcastName: string;
  latestAudioVersion?: SharedAudioVersion | null;
  markers: SharedMarker[];
}

const MARKER_TYPE_BADGE: Record<string, string> = {
  SLIP: "bg-red-100 text-red-700 border-red-200",
  RETAPE: "bg-orange-100 text-orange-700 border-orange-200",
  VOLUME: "bg-yellow-100 text-yellow-700 border-yellow-200",
  BGM: "bg-purple-100 text-purple-700 border-purple-200",
  SFX: "bg-fuchsia-100 text-fuchsia-700 border-fuchsia-200",
  TRANSITION: "bg-pink-100 text-pink-700 border-pink-200",
  FACT_CHECK: "bg-cyan-100 text-cyan-700 border-cyan-200",
};

const MARKER_STATUS_BADGE: Record<string, string> = {
  PENDING: "bg-gray-100 text-gray-700 border-gray-200",
  IN_PROGRESS: "bg-blue-100 text-blue-700 border-blue-200",
  RESOLVED: "bg-green-100 text-green-700 border-green-200",
  IGNORED: "bg-zinc-100 text-zinc-500 border-zinc-200 line-through",
};

function getEpisodeStatusBadgeClass(status: string): string {
  switch (status) {
    case "PLANNING":
      return "bg-gray-100 text-gray-700 border-gray-200";
    case "RECORDING":
      return "bg-blue-100 text-blue-700 border-blue-200";
    case "ROUGH_CUT":
      return "bg-orange-100 text-orange-700 border-orange-200";
    case "FINE_CUT":
      return "bg-yellow-100 text-yellow-700 border-yellow-200";
    case "REVIEW":
      return "bg-purple-100 text-purple-700 border-purple-200";
    case "FINALIZED":
      return "bg-green-100 text-green-700 border-green-200";
    case "DISTRIBUTING":
      return "bg-cyan-100 text-cyan-700 border-cyan-200";
    case "PUBLISHED":
      return "bg-emerald-700 text-white border-emerald-800";
    default:
      return "bg-gray-100 text-gray-700 border-gray-200";
  }
}

function getErrorMessage(err: unknown): string {
  const axiosError = err as ApiError;
  return (
    axiosError?.response?.data?.message ||
    "分享链接无效或已过期，请联系分享者确认。"
  );
}

export function SharePage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [episode, setEpisode] = useState<SharedEpisode | null>(null);

  useEffect(() => {
    const loadShared = async () => {
      if (!token) {
        setError("缺少分享令牌");
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const res = await api.get(`/share/token/${token}`);
        setEpisode(res.data.data);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    };
    loadShared();
  }, [token]);

  const audioUrl =
    episode?.latestAudioVersion?.id != null && token
      ? `/api/share/token/${token}/audio?versionId=${episode.latestAudioVersion.id}`
      : null;
  const markers = episode?.markers ?? [];

  return (
    <div className="min-h-screen bg-muted/30">
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8 sm:px-6">
        <Button
          variant="ghost"
          size="sm"
          className="w-fit"
          onClick={() => navigate(-1)}
        >
          <ArrowLeft className="mr-1.5 h-4 w-4" />
          返回
        </Button>

        {loading ? (
          <div className="flex min-h-[50vh] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        ) : error ? (
          <Card className="flex flex-col items-center justify-center py-20 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-red-50">
              <AlertTriangle className="h-8 w-8 text-red-500" />
            </div>
            <CardTitle className="mt-6 text-xl">无法访问分享内容</CardTitle>
            <CardDescription className="mt-2 max-w-sm">
              {error}
            </CardDescription>
          </Card>
        ) : episode ? (
          <>
            <Card>
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 items-start gap-3">
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                      <Mic className="h-6 w-6 text-primary" />
                    </div>
                    <div className="min-w-0">
                      <CardTitle className="text-xl truncate">
                        {episode.title}
                      </CardTitle>
                      <CardDescription className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
                        <span className="flex items-center gap-1">
                          <Radio className="h-3.5 w-3.5" />
                          {episode.podcastName}
                        </span>
                        {episode.number != null && (
                          <span>· 第{episode.number}期</span>
                        )}
                      </CardDescription>
                    </div>
                  </div>
                  <Badge
                    variant="outline"
                    className={
                      "shrink-0 border " +
                      getEpisodeStatusBadgeClass(episode.status)
                    }
                  >
                    {EPISODE_STATUS_LABELS[episode.status] ?? episode.status}
                  </Badge>
                </div>
              </CardHeader>
            </Card>

            {audioUrl ? (
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-base">
                    <FileAudio className="h-4 w-4" />
                    音频试听
                  </CardTitle>
                  {episode.latestAudioVersion && (
                    <CardDescription>
                      版本 v{episode.latestAudioVersion.versionNumber}
                      {episode.latestAudioVersion.durationMs
                        ? ` · ${formatTime(
                            episode.latestAudioVersion.durationMs
                          )}`
                        : ""}
                    </CardDescription>
                  )}
                </CardHeader>
                <CardContent>
                  <audio
                    src={audioUrl}
                    controls
                    className="w-full"
                    preload="metadata"
                  />
                </CardContent>
              </Card>
            ) : (
              <Card>
                <CardContent className="flex flex-col items-center justify-center py-10 text-center">
                  <FileAudio className="h-8 w-8 text-muted-foreground/50" />
                  <p className="mt-2 text-sm text-muted-foreground">
                    暂无可试听的音频
                  </p>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <Tag className="h-4 w-4" />
                  标记列表
                  <Badge variant="secondary" className="ml-1">
                    {markers.length}
                  </Badge>
                </CardTitle>
                <CardDescription>
                  分享单集中的时间轴标记（只读）
                </CardDescription>
              </CardHeader>
              <CardContent>
                {markers.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-10 text-center">
                    <Tag className="h-8 w-8 text-muted-foreground/50" />
                    <p className="mt-2 text-sm text-muted-foreground">
                      该单集暂无标记
                    </p>
                  </div>
                ) : (
                  <ul className="space-y-2">
                    {markers
                      .slice()
                      .sort((a, b) => a.startTimeMs - b.startTimeMs)
                      .map((marker) => (
                        <li
                          key={marker.id}
                          className="flex items-start gap-3 rounded-lg border p-3"
                        >
                          <div className="mt-0.5 shrink-0">
                            <Badge
                              variant="outline"
                              className={
                                "border font-mono " +
                                (MARKER_TYPE_BADGE[marker.type] ??
                                  "bg-gray-100 text-gray-700 border-gray-200")
                              }
                            >
                              <Clock className="mr-1 h-3 w-3" />
                              {formatTime(marker.startTimeMs)}
                            </Badge>
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge
                                variant="outline"
                                className={
                                  "border " +
                                  (MARKER_TYPE_BADGE[marker.type] ??
                                    "bg-gray-100 text-gray-700 border-gray-200")
                                }
                              >
                                {MARKER_TYPE_LABELS[marker.type] ??
                                  marker.type}
                              </Badge>
                              <Badge
                                variant="outline"
                                className={
                                  "border " +
                                  (MARKER_STATUS_BADGE[marker.status] ??
                                    "bg-gray-100 text-gray-700 border-gray-200")
                                }
                              >
                                {MARKER_STATUS_LABELS[marker.status] ??
                                  marker.status}
                              </Badge>
                            </div>
                            {marker.description && (
                              <p className="mt-1.5 text-sm">
                                {marker.description}
                              </p>
                            )}
                          </div>
                        </li>
                      ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </>
        ) : null}
      </div>
    </div>
  );
}
