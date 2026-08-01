import { useState, useEffect, useRef, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Play,
  Pause,
  Upload,
  Plus,
  Trash2,
  Edit,
  SkipBack,
  SkipForward,
  ArrowLeft,
  Loader2,
  Mic,
  Clock,
  Calendar,
  User,
  Check,
  X,
  Filter,
  FileAudio,
  Tag,
  ListTodo,
  FileText,
  History,
  ZoomIn,
  Save,
  MapPin,
} from "lucide-react";
import { AxiosError, AxiosProgressEvent } from "axios";
import WaveSurfer from "wavesurfer.js";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import {
  cn,
  formatTime,
  formatDate,
  formatDateTime,
  EPISODE_STATUS_LABELS,
  MARKER_TYPE_LABELS,
  MARKER_TYPE_COLORS,
  MARKER_STATUS_LABELS,
  TASK_STATUS_LABELS,
} from "@/lib/utils";
import type {
  Episode,
  AudioVersion,
  TimelineMarker,
  TranscriptSegment,
  Task,
  WaveformData,
} from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

type ProgressEvent = AxiosProgressEvent;

const EPISODE_STATUSES = [
  "PLANNING",
  "RECORDING",
  "ROUGH_CUT",
  "FINE_CUT",
  "REVIEW",
  "FINALIZED",
  "DISTRIBUTING",
  "PUBLISHED",
];

const MARKER_TYPES = [
  "SLIP",
  "RETAPE",
  "VOLUME",
  "BGM",
  "SFX",
  "TRANSITION",
  "FACT_CHECK",
];

const MARKER_STATUSES = ["PENDING", "IN_PROGRESS", "RESOLVED", "IGNORED"];

const TASK_STATUSES = ["TODO", "IN_PROGRESS", "DONE", "CANCELLED"];

const MARKER_TYPE_HEX: Record<string, string> = {
  SLIP: "#ef4444",
  RETAPE: "#f97316",
  VOLUME: "#eab308",
  BGM: "#8b5cf6",
  SFX: "#a855f7",
  TRANSITION: "#ec4899",
  FACT_CHECK: "#06b6d4",
};

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

const TASK_STATUS_BADGE: Record<string, string> = {
  TODO: "bg-gray-100 text-gray-700 border-gray-200",
  IN_PROGRESS: "bg-blue-100 text-blue-700 border-blue-200",
  DONE: "bg-green-100 text-green-700 border-green-200",
  CANCELLED: "bg-zinc-100 text-zinc-500 border-zinc-200",
};

const SPEAKER_COLORS = [
  "text-blue-600",
  "text-purple-600",
  "text-amber-600",
  "text-emerald-600",
  "text-rose-600",
  "text-cyan-600",
];

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

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

function getMarkerColor(type: string): string {
  return MARKER_TYPE_COLORS?.[type] ?? MARKER_TYPE_HEX[type] ?? "#3b82f6";
}

function getNextMarkerStatus(status: string): string {
  const idx = MARKER_STATUSES.indexOf(status);
  if (idx === -1) return "PENDING";
  return MARKER_STATUSES[(idx + 1) % MARKER_STATUSES.length];
}

interface MarkerFormState {
  startTimeMs: number;
  endTimeMs: number | null;
  type: string;
  description: string;
}

interface TaskFormState {
  title: string;
  description: string;
  assigneeId: string;
  dueDate: string;
}

interface SegmentEditState {
  id: number;
  speaker: string;
  text: string;
  startTimeMs: number;
  endTimeMs: number;
}

const EMPTY_MARKER_FORM: MarkerFormState = {
  startTimeMs: 0,
  endTimeMs: null,
  type: "SLIP",
  description: "",
};

const EMPTY_TASK_FORM: TaskFormState = {
  title: "",
  description: "",
  assigneeId: "",
  dueDate: "",
};

export function EpisodeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();

  const [episode, setEpisode] = useState<Episode | null>(null);
  const [episodeLoading, setEpisodeLoading] = useState(true);

  const [versions, setVersions] = useState<AudioVersion[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);

  const [waveformLoading, setWaveformLoading] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTimeMs, setCurrentTimeMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [zoomLevel, setZoomLevel] = useState(0);

  const [markers, setMarkers] = useState<TimelineMarker[]>([]);
  const [markersLoading, setMarkersLoading] = useState(false);
  const [markerStats, setMarkerStats] = useState<Record<string, number>>({});
  const [filterType, setFilterType] = useState("ALL");
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [filterKeyword, setFilterKeyword] = useState("");

  const [markerDialogOpen, setMarkerDialogOpen] = useState(false);
  const [markerForm, setMarkerForm] = useState<MarkerFormState>(EMPTY_MARKER_FORM);
  const [editingMarkerId, setEditingMarkerId] = useState<number | null>(null);
  const [markerSubmitting, setMarkerSubmitting] = useState(false);
  const [selectedMarkerId, setSelectedMarkerId] = useState<number | null>(null);

  const [transcript, setTranscript] = useState<TranscriptSegment[]>([]);
  const [transcriptLoading, setTranscriptLoading] = useState(false);
  const [editingSegment, setEditingSegment] = useState<SegmentEditState | null>(null);
  const [transcriptUploadOpen, setTranscriptUploadOpen] = useState(false);
  const [transcriptUploadText, setTranscriptUploadText] = useState("");
  const [transcriptSubmitting, setTranscriptSubmitting] = useState(false);
  const [transcriptGenerating, setTranscriptGenerating] = useState(false);

  const [tasks, setTasks] = useState<Task[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskDialogOpen, setTaskDialogOpen] = useState(false);
  const [taskForm, setTaskForm] = useState<TaskFormState>(EMPTY_TASK_FORM);
  const [taskSubmitting, setTaskSubmitting] = useState(false);

  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const [activeTab, setActiveTab] = useState("timeline");
  const [statusUpdating, setStatusUpdating] = useState(false);

  const wsRef = useRef<WaveSurfer | null>(null);
  const waveformContainerRef = useRef<HTMLDivElement | null>(null);
  const audioUrlRef = useRef<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const loadEpisode = useCallback(async () => {
    if (!id) return;
    setEpisodeLoading(true);
    try {
      const res = await api.get(`/episodes/${id}`);
      setEpisode(res.data.data);
    } catch (err) {
      toast.error(getErrorMessage(err, "加载单集详情失败"));
    } finally {
      setEpisodeLoading(false);
    }
  }, [id, toast]);

  const loadVersions = useCallback(async () => {
    if (!id) return;
    setVersionsLoading(true);
    try {
      const res = await api.get(`/audio/episode/${id}/versions`);
      const data: AudioVersion[] = res.data.data;
      setVersions(data);
      if (data.length > 0 && !selectedVersionId) {
        const latest = data.find((v) => v.isFinal) ?? data[0];
        setSelectedVersionId(latest.id);
      }
    } catch (err) {
      toast.error(getErrorMessage(err, "加载音频版本失败"));
    } finally {
      setVersionsLoading(false);
    }
  }, [id, toast, selectedVersionId]);

  const loadMarkers = useCallback(async () => {
    if (!selectedVersionId) return;
    setMarkersLoading(true);
    try {
      const params: Record<string, string> = {};
      if (filterType !== "ALL") params.type = filterType;
      if (filterStatus !== "ALL") params.status = filterStatus;
      if (filterKeyword.trim()) params.keyword = filterKeyword.trim();
      const [markersRes, statsRes] = await Promise.all([
        api.get(`/markers/version/${selectedVersionId}`, { params }),
        api.get(`/markers/version/${selectedVersionId}/stats`),
      ]);
      setMarkers(markersRes.data.data);
      setMarkerStats(statsRes.data.data ?? {});
    } catch (err) {
      toast.error(getErrorMessage(err, "加载标记失败"));
    } finally {
      setMarkersLoading(false);
    }
  }, [selectedVersionId, filterType, filterStatus, filterKeyword, toast]);

  const loadTranscript = useCallback(async () => {
    if (!selectedVersionId) return;
    setTranscriptLoading(true);
    try {
      const res = await api.get(`/transcripts/version/${selectedVersionId}`);
      setTranscript(res.data.data);
    } catch (err) {
      toast.error(getErrorMessage(err, "加载转写文本失败"));
    } finally {
      setTranscriptLoading(false);
    }
  }, [selectedVersionId, toast]);

  const loadTasks = useCallback(async () => {
    if (!id) return;
    setTasksLoading(true);
    try {
      const res = await api.get("/tasks", { params: { episodeId: id } });
      setTasks(res.data.data);
    } catch (err) {
      toast.error(getErrorMessage(err, "加载任务失败"));
    } finally {
      setTasksLoading(false);
    }
  }, [id, toast]);

  useEffect(() => {
    loadEpisode();
  }, [loadEpisode]);

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  useEffect(() => {
    if (activeTab === "tasks") loadTasks();
  }, [activeTab, loadTasks]);

  useEffect(() => {
    if (activeTab === "transcript" && selectedVersionId) loadTranscript();
  }, [activeTab, selectedVersionId, loadTranscript]);

  useEffect(() => {
    if (selectedVersionId) loadMarkers();
  }, [selectedVersionId, loadMarkers]);

  useEffect(() => {
    if (
      activeTab !== "timeline" ||
      !selectedVersionId ||
      !waveformContainerRef.current
    ) {
      if (wsRef.current) {
        wsRef.current.destroy();
        wsRef.current = null;
      }
      if (audioUrlRef.current) {
        URL.revokeObjectURL(audioUrlRef.current);
        audioUrlRef.current = null;
      }
      setDurationMs(0);
      setCurrentTimeMs(0);
      return;
    }

    let destroyed = false;
    setWaveformLoading(true);
    setIsPlaying(false);
    setCurrentTimeMs(0);

    const initWaveSurfer = async () => {
      try {
        const container = waveformContainerRef.current;
        if (!container) return;

        const [waveformRes, audioRes] = await Promise.all([
          api.get<{ success: boolean; data: WaveformData }>(
            `/audio/version/${selectedVersionId}/waveform`
          ),
          api.get(`/audio/version/${selectedVersionId}/stream`, {
            responseType: "blob",
          }),
        ]);

        if (destroyed) return;

        const waveformData = waveformRes.data.data;
        const blob = new Blob([audioRes.data as BlobPart], {
          type: (audioRes.headers["content-type"] as string) || "audio/mpeg",
        });
        const objectUrl = URL.createObjectURL(blob);
        audioUrlRef.current = objectUrl;

        if (wsRef.current) {
          wsRef.current.destroy();
          wsRef.current = null;
        }

        const peaks: number[][] | undefined =
          waveformData?.samples?.length ? [waveformData.samples] : undefined;

        const ws = WaveSurfer.create({
          container,
          waveColor: "#94a3b8",
          progressColor: "#475569",
          cursorColor: "#0f172a",
          height: 128,
          normalize: true,
          minPxPerSec: zoomLevel || 1,
          barWidth: 2,
          barGap: 1,
          barRadius: 2,
        });

        wsRef.current = ws;

        ws.on("ready", () => {
          if (destroyed) return;
          setDurationMs(waveformData?.duration_ms ?? waveformData?.durationMs ?? ws.getDuration() * 1000);
          setWaveformLoading(false);
        });

        ws.on("play", () => {
          if (!destroyed) setIsPlaying(true);
        });

        ws.on("pause", () => {
          if (!destroyed) setIsPlaying(false);
        });

        ws.on("finish", () => {
          if (!destroyed) setIsPlaying(false);
        });

        ws.on("timeupdate", (time: number) => {
          if (!destroyed) setCurrentTimeMs(time * 1000);
        });

        ws.load(objectUrl, peaks);
      } catch (err) {
        if (!destroyed) {
          toast.error(getErrorMessage(err, "加载音频波形失败"));
          setWaveformLoading(false);
        }
      }
    };

    initWaveSurfer();

    return () => {
      destroyed = true;
      if (wsRef.current) {
        wsRef.current.destroy();
        wsRef.current = null;
      }
      if (audioUrlRef.current) {
        URL.revokeObjectURL(audioUrlRef.current);
        audioUrlRef.current = null;
      }
    };
  }, [selectedVersionId, activeTab, toast]);

  useEffect(() => {
    if (wsRef.current && zoomLevel >= 0) {
      wsRef.current.zoom(zoomLevel);
    }
  }, [zoomLevel]);

  const seekTo = useCallback((ms: number) => {
    const ws = wsRef.current;
    if (!ws || !durationMs) return;
    const clamped = Math.max(0, Math.min(ms, durationMs));
    ws.setTime(clamped / 1000);
    setCurrentTimeMs(clamped);
  }, [durationMs]);

  const togglePlay = useCallback(() => {
    wsRef.current?.playPause();
  }, []);

  const seekRelative = useCallback((deltaMs: number) => {
    seekTo(currentTimeMs + deltaMs);
  }, [currentTimeMs, seekTo]);

  const addMarkerAtCurrentTime = useCallback(() => {
    if (!selectedVersionId) {
      toast.error("请先上传音频");
      return;
    }
    setEditingMarkerId(null);
    setMarkerForm({
      startTimeMs: Math.round(currentTimeMs),
      endTimeMs: null,
      type: "SLIP",
      description: "",
    });
    setMarkerDialogOpen(true);
  }, [selectedVersionId, currentTimeMs, toast]);

  const openEditMarker = (marker: TimelineMarker) => {
    setEditingMarkerId(marker.id);
    setMarkerForm({
      startTimeMs: marker.startTimeMs,
      endTimeMs: marker.endTimeMs ?? null,
      type: marker.type,
      description: marker.description ?? "",
    });
    setMarkerDialogOpen(true);
  };

  const handleMarkerSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVersionId) return;
    const startMs = markerForm.startTimeMs;
    if (Number.isNaN(startMs) || startMs < 0) {
      toast.error("请输入有效的开始时间");
      return;
    }
    const endMs = markerForm.endTimeMs;
    if (endMs != null && (Number.isNaN(endMs) || endMs < startMs)) {
      toast.error("结束时间必须大于开始时间");
      return;
    }
    setMarkerSubmitting(true);
    try {
      if (editingMarkerId) {
        await api.put(`/markers/${editingMarkerId}`, {
          startTimeMs: startMs,
          endTimeMs: endMs,
          type: markerForm.type,
          description: markerForm.description || null,
        });
        toast.success("标记已更新");
      } else {
        await api.post(`/markers/version/${selectedVersionId}`, {
          startTimeMs: startMs,
          endTimeMs: endMs,
          type: markerForm.type,
          description: markerForm.description || null,
        });
        toast.success("标记已添加");
      }
      setMarkerDialogOpen(false);
      setMarkerForm(EMPTY_MARKER_FORM);
      setEditingMarkerId(null);
      loadMarkers();
    } catch (err) {
      toast.error(getErrorMessage(err, "保存标记失败"));
    } finally {
      setMarkerSubmitting(false);
    }
  };

  const handleDeleteMarker = async (markerId: number) => {
    if (!window.confirm("确定要删除此标记吗？")) return;
    try {
      await api.delete(`/markers/${markerId}`);
      toast.success("标记已删除");
      if (selectedMarkerId === markerId) setSelectedMarkerId(null);
      loadMarkers();
    } catch (err) {
      toast.error(getErrorMessage(err, "删除标记失败"));
    }
  };

  const handleCycleMarkerStatus = async (marker: TimelineMarker) => {
    const next = getNextMarkerStatus(marker.status);
    try {
      await api.put(`/markers/${marker.id}`, { status: next });
      loadMarkers();
    } catch (err) {
      toast.error(getErrorMessage(err, "更新标记状态失败"));
    }
  };

  const handleUpdateMarkerStatus = async (markerId: number, status: string) => {
    try {
      await api.put(`/markers/${markerId}`, { status });
      loadMarkers();
    } catch (err) {
      toast.error(getErrorMessage(err, "更新标记状态失败"));
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !id) return;
    const formData = new FormData();
    formData.append("file", file);
    setUploading(true);
    setUploadProgress(0);
    try {
      await api.post(`/audio/upload?episodeId=${id}`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent: ProgressEvent) => {
          if (progressEvent.total) {
            const pct = Math.round(
              (progressEvent.loaded / progressEvent.total) * 100
            );
            setUploadProgress(pct);
          }
        },
      });
      toast.success("音频上传成功");
      if (fileInputRef.current) fileInputRef.current.value = "";
      const res = await api.get(`/audio/episode/${id}/versions`);
      const data: AudioVersion[] = res.data.data;
      setVersions(data);
      if (data.length > 0) {
        const latest = data.reduce((a, b) =>
          a.versionNumber > b.versionNumber ? a : b
        );
        setSelectedVersionId(latest.id);
      }
    } catch (err) {
      toast.error(getErrorMessage(err, "音频上传失败"));
    } finally {
      setUploading(false);
      setUploadProgress(0);
    }
  };

  const handleSetFinal = async (versionId: number) => {
    if (!id) return;
    try {
      await api.post(`/audio/episode/${id}/final/${versionId}`);
      toast.success("已设为最终版");
      loadVersions();
    } catch (err) {
      toast.error(getErrorMessage(err, "设置最终版失败"));
    }
  };

  const handleEpisodeStatusChange = async (status: string) => {
    if (!episode) return;
    setStatusUpdating(true);
    try {
      const res = await api.patch(`/episodes/${episode.id}/status`, { status });
      setEpisode((prev: Episode | null) =>
        prev ? { ...prev, status, ...res.data.data } : prev
      );
      toast.success("状态已更新");
    } catch (err) {
      toast.error(getErrorMessage(err, "更新状态失败"));
    } finally {
      setStatusUpdating(false);
    }
  };

  const handleSegmentEdit = (segment: TranscriptSegment) => {
    setEditingSegment({
      id: segment.id,
      speaker: segment.speaker ?? "",
      text: segment.text,
      startTimeMs: segment.startTimeMs,
      endTimeMs: segment.endTimeMs,
    });
  };

  const handleSegmentSave = async () => {
    if (!editingSegment) return;
    const startMs = editingSegment.startTimeMs;
    const endMs = editingSegment.endTimeMs;
    if (Number.isNaN(startMs) || Number.isNaN(endMs) || endMs < startMs) {
      toast.error("请输入有效的时间范围");
      return;
    }
    try {
      await api.put(`/transcripts/segment/${editingSegment.id}`, {
        speaker: editingSegment.speaker,
        text: editingSegment.text,
        startTimeMs: startMs,
        endTimeMs: endMs,
      });
      toast.success("转写片段已更新");
      setEditingSegment(null);
      loadTranscript();
    } catch (err) {
      toast.error(getErrorMessage(err, "更新转写片段失败"));
    }
  };

  const handleTranscriptUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVersionId) return;
    const raw = transcriptUploadText.trim();
    if (!raw) {
      toast.error("请输入转写内容");
      return;
    }
    setTranscriptSubmitting(true);
    try {
      let segments: {
        startTimeMs: number;
        endTimeMs: number;
        speaker: string;
        text: string;
      }[] = [];

      if (raw.startsWith("[") || raw.startsWith("{")) {
        const parsed = JSON.parse(raw);
        segments = (Array.isArray(parsed) ? parsed : [parsed]).map((s) => ({
          startTimeMs: Number(s.startTimeMs ?? s.start ?? 0),
          endTimeMs: Number(s.endTimeMs ?? s.end ?? 0),
          speaker: String(s.speaker ?? s.speakerName ?? "未知"),
          text: String(s.text ?? ""),
        }));
      } else {
        segments = raw
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean)
          .map((line) => {
            const [timePart, ...rest] = line.split("|");
            const [startStr, endStr] = (timePart ?? "").split("-");
            return {
              startTimeMs: Number(startStr?.trim() ?? 0),
              endTimeMs: Number(endStr?.trim() ?? 0),
              speaker: rest[0]?.trim() || "未知",
              text: rest.slice(1).join("|").trim(),
            };
          });
      }

      if (segments.length === 0) {
        toast.error("未解析到有效片段");
        return;
      }

      await api.post(
        `/transcripts/version/${selectedVersionId}/bulk`,
        { segments }
      );
      toast.success(`成功上传 ${segments.length} 条转写片段`);
      setTranscriptUploadOpen(false);
      setTranscriptUploadText("");
      loadTranscript();
    } catch (err) {
      toast.error(getErrorMessage(err, "上传转写失败"));
    } finally {
      setTranscriptSubmitting(false);
    }
  };

  const handleGenerateTranscript = async () => {
    if (!selectedVersionId) return;
    setTranscriptGenerating(true);
    try {
      const res = await api.post(
        `/transcripts/version/${selectedVersionId}/generate`
      );
      const count = Array.isArray(res.data.data) ? res.data.data.length : 0;
      toast.success(`已生成 ${count} 条转写片段骨架，请编辑填充内容`);
      loadTranscript();
    } catch (err) {
      toast.error(getErrorMessage(err, "自动生成转写失败"));
    } finally {
      setTranscriptGenerating(false);
    }
  };

  const handleTaskSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !taskForm.title.trim()) {
      toast.error("请输入任务标题");
      return;
    }
    setTaskSubmitting(true);
    try {
      await api.post("/tasks", {
        title: taskForm.title.trim(),
        description: taskForm.description || null,
        assigneeId: taskForm.assigneeId ? Number(taskForm.assigneeId) : null,
        dueDate: taskForm.dueDate || null,
      }, { params: { episodeId: id } });
      toast.success("任务已创建");
      setTaskDialogOpen(false);
      setTaskForm(EMPTY_TASK_FORM);
      loadTasks();
    } catch (err) {
      toast.error(getErrorMessage(err, "创建任务失败"));
    } finally {
      setTaskSubmitting(false);
    }
  };

  const handleTaskStatusChange = async (taskId: number, status: string) => {
    try {
      await api.patch(`/tasks/${taskId}/status`, {}, { params: { status } });
      loadTasks();
    } catch (err) {
      toast.error(getErrorMessage(err, "更新任务状态失败"));
    }
  };

  const handleMarkTaskDone = async (taskId: number) => {
    try {
      await api.patch(`/tasks/${taskId}/status`, {}, { params: { status: "DONE" } });
      loadTasks();
    } catch (err) {
      toast.error(getErrorMessage(err, "更新任务状态失败"));
    }
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (activeTab !== "timeline") return;
      const target = e.target as HTMLElement;
      if (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.tagName === "SELECT" ||
        target.isContentEditable
      ) {
        return;
      }
      if (e.code === "Space") {
        e.preventDefault();
        togglePlay();
      } else if (e.key === "m" || e.key === "M") {
        e.preventDefault();
        addMarkerAtCurrentTime();
      } else if (e.key === "ArrowLeft") {
        e.preventDefault();
        seekRelative(-5000);
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        seekRelative(5000);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [activeTab, togglePlay, addMarkerAtCurrentTime, seekRelative]);

  const filteredMarkers = markers;

  const selectedMarker = markers.find((m) => m.id === selectedMarkerId) ?? null;

  const selectedVersion = versions.find((v) => v.id === selectedVersionId) ?? null;

  if (episodeLoading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!episode) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-4 p-6">
        <p className="text-muted-foreground">单集不存在或已被删除</p>
        <Button variant="outline" onClick={() => navigate(-1)}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          返回
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex flex-wrap items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight truncate">
              {episode.title}
            </h1>
            {episode.number != null && (
              <Badge variant="outline" className="text-sm">
                第{episode.number}期
              </Badge>
            )}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
            {episode.podcast?.name && (
              <span className="flex items-center gap-1">
                <Mic className="h-3.5 w-3.5" />
                {episode.podcast.name}
              </span>
            )}
            {episode.recordDate && (
              <span className="flex items-center gap-1">
                <Calendar className="h-3.5 w-3.5" />
                {formatDate(episode.recordDate)}
              </span>
            )}
            <span className="flex items-center gap-1">
              <Clock className="h-3.5 w-3.5" />
              {formatDateTime(episode.createdAt)}
            </span>
          </div>
        </div>
        <Badge
          variant="outline"
          className={cn("border", getEpisodeStatusBadgeClass(episode.status))}
        >
          {EPISODE_STATUS_LABELS[episode.status] ?? episode.status}
        </Badge>
        <Select
          value={episode.status}
          onValueChange={handleEpisodeStatusChange}
          disabled={statusUpdating}
        >
          <SelectTrigger className="w-[160px]">
            <SelectValue placeholder="更改状态" />
          </SelectTrigger>
          <SelectContent>
            {EPISODE_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {EPISODE_STATUS_LABELS[s] ?? s}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1">
        <TabsList>
          <TabsTrigger value="timeline" className="flex items-center gap-1.5">
            <FileAudio className="h-4 w-4" />
            时间轴
          </TabsTrigger>
          <TabsTrigger value="transcript" className="flex items-center gap-1.5">
            <FileText className="h-4 w-4" />
            转写文本
          </TabsTrigger>
          <TabsTrigger value="tasks" className="flex items-center gap-1.5">
            <ListTodo className="h-4 w-4" />
            任务
          </TabsTrigger>
          <TabsTrigger value="versions" className="flex items-center gap-1.5">
            <History className="h-4 w-4" />
            版本
          </TabsTrigger>
        </TabsList>

        <TabsContent value="timeline" className="mt-4">
          <div className="flex flex-col gap-4 lg:flex-row">
            <div className="flex-1 min-w-0 space-y-4">
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <CardTitle className="text-base flex items-center gap-2">
                        <FileAudio className="h-4 w-4" />
                        音频播放器
                      </CardTitle>
                      {selectedVersion && (
                        <Badge variant="secondary" className="text-xs">
                          v{selectedVersion.versionNumber} ·{" "}
                          {selectedVersion.fileName}
                        </Badge>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {versions.length > 0 && (
                        <Select
                          value={selectedVersionId != null ? String(selectedVersionId) : ""}
                          onValueChange={(v) => setSelectedVersionId(Number(v))}
                        >
                          <SelectTrigger className="w-[200px]">
                            <SelectValue placeholder="选择版本" />
                          </SelectTrigger>
                          <SelectContent>
                            {versions.map((v) => (
                              <SelectItem key={v.id} value={String(v.id)}>
                                v{v.versionNumber} - {v.fileName}
                                {v.isFinal ? " (最终版)" : ""}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={uploading}
                      >
                        {uploading ? (
                          <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
                        ) : (
                          <Upload className="mr-1.5 h-4 w-4" />
                        )}
                        上传音频
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {!selectedVersionId && versions.length === 0 ? (
                    <div
                      className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/25 py-16 text-center cursor-pointer hover:border-primary/50 transition-colors"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
                        <Upload className="h-7 w-7 text-muted-foreground" />
                      </div>
                      <p className="mt-4 font-medium">上传音频文件</p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        支持 WAV、MP3、M4A 格式，点击或拖拽文件到此区域
                      </p>
                      {uploading && (
                        <div className="mt-4 w-full max-w-xs">
                          <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                            <div
                              className="h-full bg-primary transition-all"
                              style={{ width: `${uploadProgress}%` }}
                            />
                          </div>
                          <p className="mt-1 text-xs text-muted-foreground">
                            上传中 {uploadProgress}%
                          </p>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {uploading && (
                        <div className="rounded-lg border border-blue-200 bg-blue-50 p-3">
                          <div className="flex items-center gap-2 text-sm text-blue-700">
                            <Loader2 className="h-4 w-4 animate-spin" />
                            正在上传音频... {uploadProgress}%
                          </div>
                          <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-blue-100">
                            <div
                              className="h-full bg-blue-600 transition-all"
                              style={{ width: `${uploadProgress}%` }}
                            />
                          </div>
                        </div>
                      )}
                      <div className="flex items-center gap-3">
                        <Button
                          variant="outline"
                          size="icon"
                          onClick={() => seekRelative(-5000)}
                          disabled={!selectedVersionId}
                        >
                          <SkipBack className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          onClick={togglePlay}
                          disabled={waveformLoading || !selectedVersionId}
                        >
                          {isPlaying ? (
                            <Pause className="h-4 w-4" />
                          ) : (
                            <Play className="h-4 w-4" />
                          )}
                        </Button>
                        <Button
                          variant="outline"
                          size="icon"
                          onClick={() => seekRelative(5000)}
                          disabled={!selectedVersionId}
                        >
                          <SkipForward className="h-4 w-4" />
                        </Button>
                        <div className="flex items-center gap-2 font-mono text-sm tabular-nums">
                          <span className="font-medium">
                            {formatTime(currentTimeMs)}
                          </span>
                          <span className="text-muted-foreground">/</span>
                          <span className="text-muted-foreground">
                            {formatTime(durationMs)}
                          </span>
                        </div>
                        <div className="ml-auto flex items-center gap-2">
                          <ZoomIn className="h-4 w-4 text-muted-foreground" />
                          <input
                            type="range"
                            min={0}
                            max={200}
                            value={zoomLevel}
                            onChange={(e) => setZoomLevel(Number(e.target.value))}
                            className="w-28"
                          />
                        </div>
                      </div>
                      <div className="relative overflow-x-auto">
                        <div
                          ref={waveformContainerRef}
                          className="relative min-h-[128px] w-full cursor-pointer"
                        />
                        {waveformLoading && (
                          <div className="absolute inset-0 flex items-center justify-center bg-background/60">
                            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                          </div>
                        )}
                        {durationMs > 0 &&
                          filteredMarkers.map((marker) => {
                            const leftPct = (marker.startTimeMs / durationMs) * 100;
                            const widthPct =
                              marker.endTimeMs != null
                                ? ((marker.endTimeMs - marker.startTimeMs) /
                                    durationMs) *
                                  100
                                : 0;
                            const isSelected = marker.id === selectedMarkerId;
                            const color = getMarkerColor(marker.type);
                            return (
                              <div
                                key={marker.id}
                                className="absolute top-0 bottom-0 z-10 cursor-pointer group"
                                style={{
                                  left: `${leftPct}%`,
                                  width: marker.endTimeMs != null ? `${widthPct}%` : "3px",
                                  backgroundColor:
                                    marker.endTimeMs != null
                                      ? `${color}22`
                                      : color,
                                  borderLeft: `2px solid ${color}`,
                                  boxShadow: isSelected
                                    ? `0 0 0 2px ${color}`
                                    : undefined,
                                }}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  seekTo(marker.startTimeMs);
                                  setSelectedMarkerId(marker.id);
                                }}
                                title={`${MARKER_TYPE_LABELS[marker.type] ?? marker.type}: ${
                                  marker.description ?? ""
                                }`}
                              >
                                <div
                                  className="absolute -top-0 left-0 h-2 w-2 rounded-full -translate-x-1/2"
                                  style={{ backgroundColor: color }}
                                />
                              </div>
                            );
                          })}
                      </div>
                      <div className="flex items-center justify-between">
                        <Button
                          size="sm"
                          onClick={addMarkerAtCurrentTime}
                          disabled={!selectedVersionId || waveformLoading}
                        >
                          <Plus className="mr-1.5 h-4 w-4" />
                          添加标记 (M)
                        </Button>
                        <div className="flex items-center gap-3 text-xs text-muted-foreground">
                          <span className="flex items-center gap-1">
                            <kbd className="rounded border bg-muted px-1.5 py-0.5 font-mono">
                              Space
                            </kbd>
                            播放/暂停
                          </span>
                          <span className="flex items-center gap-1">
                            <kbd className="rounded border bg-muted px-1.5 py-0.5 font-mono">
                              ←/→
                            </kbd>
                            快退/快进 5s
                          </span>
                        </div>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <CardTitle className="text-base flex items-center gap-2">
                      <Tag className="h-4 w-4" />
                      标记列表
                      <Badge variant="secondary" className="ml-1">
                        {markers.length}
                      </Badge>
                    </CardTitle>
                    <div className="flex flex-wrap items-center gap-2">
                      <div className="flex items-center gap-1">
                        <Filter className="h-3.5 w-3.5 text-muted-foreground" />
                      </div>
                      <Select value={filterType} onValueChange={setFilterType}>
                        <SelectTrigger className="w-[120px] h-8 text-xs">
                          <SelectValue placeholder="类型" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ALL">全部类型</SelectItem>
                          {MARKER_TYPES.map((t) => (
                            <SelectItem key={t} value={t}>
                              {MARKER_TYPE_LABELS[t] ?? t}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Select value={filterStatus} onValueChange={setFilterStatus}>
                        <SelectTrigger className="w-[120px] h-8 text-xs">
                          <SelectValue placeholder="状态" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ALL">全部状态</SelectItem>
                          {MARKER_STATUSES.map((s) => (
                            <SelectItem key={s} value={s}>
                              {MARKER_STATUS_LABELS[s] ?? s}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Input
                        placeholder="搜索关键词"
                        value={filterKeyword}
                        onChange={(e) => setFilterKeyword(e.target.value)}
                        className="h-8 w-[160px] text-xs"
                      />
                    </div>
                  </div>
                  {Object.keys(markerStats).length > 0 && (
                    <div className="flex flex-wrap gap-2 pt-1">
                      {MARKER_STATUSES.map((s) => (
                        <Badge
                          key={s}
                          variant="outline"
                          className={cn(
                            "text-xs border",
                            MARKER_STATUS_BADGE[s]
                          )}
                        >
                          {MARKER_STATUS_LABELS[s] ?? s}:{" "}
                          {markerStats[s] ?? 0}
                        </Badge>
                      ))}
                    </div>
                  )}
                </CardHeader>
                <CardContent>
                  {markersLoading ? (
                    <div className="flex justify-center py-8">
                      <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                    </div>
                  ) : filteredMarkers.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-12 text-center">
                      <MapPin className="h-8 w-8 text-muted-foreground/50" />
                      <p className="mt-2 text-sm text-muted-foreground">
                        暂无标记，播放音频时按 M 键或点击"添加标记"
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {filteredMarkers.map((marker) => (
                        <div
                          key={marker.id}
                          className={cn(
                            "flex items-start gap-3 rounded-lg border p-3 transition-colors cursor-pointer hover:bg-muted/50",
                            selectedMarkerId === marker.id
                              ? "border-primary bg-primary/5"
                              : "border-border"
                          )}
                          onClick={() => {
                            seekTo(marker.startTimeMs);
                            setSelectedMarkerId(marker.id);
                          }}
                        >
                          <div
                            className="mt-1 h-3 w-3 shrink-0 rounded-full"
                            style={{ backgroundColor: getMarkerColor(marker.type) }}
                          />
                          <div className="flex-1 min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-mono text-xs font-medium text-muted-foreground">
                                {formatTime(marker.startTimeMs)}
                                {marker.endTimeMs != null &&
                                  ` - ${formatTime(marker.endTimeMs)}`}
                              </span>
                              <Badge
                                variant="outline"
                                className={cn(
                                  "text-xs border",
                                  MARKER_TYPE_BADGE[marker.type]
                                )}
                              >
                                {MARKER_TYPE_LABELS[marker.type] ?? marker.type}
                              </Badge>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleCycleMarkerStatus(marker);
                                }}
                                className="cursor-pointer"
                              >
                                <Badge
                                  variant="outline"
                                  className={cn(
                                    "text-xs border transition-colors hover:opacity-80",
                                    MARKER_STATUS_BADGE[marker.status]
                                  )}
                                >
                                  {MARKER_STATUS_LABELS[marker.status] ??
                                    marker.status}
                                </Badge>
                              </button>
                            </div>
                            {marker.description && (
                              <p className="mt-1 text-sm">{marker.description}</p>
                            )}
                            {marker.createdByName && (
                              <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                                <User className="h-3 w-3" />
                                {marker.createdByName}
                                {marker.createdAt &&
                                  ` · ${formatDateTime(marker.createdAt)}`}
                              </p>
                            )}
                          </div>
                          <div className="flex shrink-0 items-center gap-1">
                            <Select
                              value={marker.status}
                              onValueChange={(value) => {
                                handleUpdateMarkerStatus(marker.id, value);
                              }}
                            >
                              <SelectTrigger className="h-7 w-[100px] text-xs">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {MARKER_STATUSES.map((s) => (
                                  <SelectItem key={s} value={s}>
                                    {MARKER_STATUS_LABELS[s] ?? s}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={(e) => {
                                e.stopPropagation();
                                openEditMarker(marker);
                              }}
                            >
                              <Edit className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7 text-destructive hover:text-destructive"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDeleteMarker(marker.id);
                              }}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            <div className="w-full shrink-0 lg:w-80">
              <Card className="lg:sticky lg:top-4">
                <CardHeader className="pb-3">
                  <CardTitle className="text-base flex items-center gap-2">
                    <Tag className="h-4 w-4" />
                    标记详情
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {!selectedMarker ? (
                    <div className="flex flex-col items-center justify-center py-10 text-center">
                      <MapPin className="h-8 w-8 text-muted-foreground/40" />
                      <p className="mt-2 text-sm text-muted-foreground">
                        选择一个标记查看详情
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        点击波形或列表中的标记
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-4">
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="outline"
                          className={cn(
                            "border",
                            MARKER_TYPE_BADGE[selectedMarker.type]
                          )}
                        >
                          {MARKER_TYPE_LABELS[selectedMarker.type] ??
                            selectedMarker.type}
                        </Badge>
                        <Badge
                          variant="outline"
                          className={cn(
                            "border",
                            MARKER_STATUS_BADGE[selectedMarker.status]
                          )}
                        >
                          {MARKER_STATUS_LABELS[selectedMarker.status] ??
                            selectedMarker.status}
                        </Badge>
                      </div>
                      <div>
                        <Label className="text-xs text-muted-foreground">
                          时间范围
                        </Label>
                        <p className="mt-1 font-mono text-sm">
                          {formatTime(selectedMarker.startTimeMs)}
                          {selectedMarker.endTimeMs != null &&
                            ` - ${formatTime(selectedMarker.endTimeMs)}`}
                        </p>
                      </div>
                      <div>
                        <Label className="text-xs text-muted-foreground">
                          描述
                        </Label>
                        <p className="mt-1 text-sm whitespace-pre-wrap">
                          {selectedMarker.description || "无描述"}
                        </p>
                      </div>
                      {selectedMarker.createdByName && (
                        <div>
                          <Label className="text-xs text-muted-foreground">
                            创建者
                          </Label>
                          <p className="mt-1 flex items-center gap-1 text-sm">
                            <User className="h-3.5 w-3.5" />
                            {selectedMarker.createdByName}
                          </p>
                        </div>
                      )}
                      {selectedMarker.screenshotUrl && (
                        <div>
                          <Label className="text-xs text-muted-foreground">
                            截图
                          </Label>
                          <img
                            src={selectedMarker.screenshotUrl}
                            alt="标记截图"
                            className="mt-1 rounded-md border"
                          />
                        </div>
                      )}
                      <div className="flex gap-2 pt-2">
                        <Button
                          size="sm"
                          variant="outline"
                          className="flex-1"
                          onClick={() => seekTo(selectedMarker.startTimeMs)}
                        >
                          <Play className="mr-1 h-3.5 w-3.5" />
                          播放
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => openEditMarker(selectedMarker)}
                        >
                          <Edit className="mr-1 h-3.5 w-3.5" />
                          编辑
                        </Button>
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="transcript" className="mt-4">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <CardTitle className="text-base flex items-center gap-2">
                    <FileText className="h-4 w-4" />
                    转写文本
                  </CardTitle>
                  <CardDescription className="mt-1">
                    共 {transcript.length} 个片段，点击片段可跳转播放
                  </CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  {selectedVersionId ? (
                    <Select
                      value={selectedVersionId != null ? String(selectedVersionId) : ""}
                      onValueChange={(v) => setSelectedVersionId(Number(v))}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="选择版本" />
                      </SelectTrigger>
                      <SelectContent>
                        {versions.map((v) => (
                          <SelectItem key={v.id} value={String(v.id)}>
                            v{v.versionNumber} - {v.fileName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : null}
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={handleGenerateTranscript}
                    disabled={!selectedVersionId || transcriptGenerating}
                  >
                    {transcriptGenerating ? (
                      <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
                    ) : (
                      <FileText className="mr-1.5 h-4 w-4" />
                    )}
                    自动生成
                  </Button>
                  <Button
                    size="sm"
                    onClick={() => setTranscriptUploadOpen(true)}
                    disabled={!selectedVersionId}
                  >
                    <Upload className="mr-1.5 h-4 w-4" />
                    上传转写
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {transcriptLoading ? (
                <div className="flex justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : transcript.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                  <FileText className="h-10 w-10 text-muted-foreground/40" />
                  <p className="mt-3 font-medium">暂无转写文本</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    上传转写文件或手动添加片段
                  </p>
                  <Button
                    className="mt-4"
                    onClick={() => setTranscriptUploadOpen(true)}
                    disabled={!selectedVersionId}
                  >
                    <Upload className="mr-2 h-4 w-4" />
                    上传转写
                  </Button>
                </div>
              ) : (
                <div className="space-y-1">
                  {transcript.map((segment) => {
                    const isEditing = editingSegment?.id === segment.id;
                    const speakerColor =
                      SPEAKER_COLORS[
                        (segment.speaker || "").length % SPEAKER_COLORS.length
                      ];
                    return (
                      <div
                        key={segment.id}
                        className={cn(
                          "group flex gap-3 rounded-lg p-3 transition-colors hover:bg-muted/50",
                          isEditing && "bg-muted"
                        )}
                      >
                        <div className="w-20 shrink-0 pt-0.5">
                          <button
                            className="font-mono text-xs text-muted-foreground hover:text-primary transition-colors"
                            onClick={() => seekTo(segment.startTimeMs)}
                          >
                            {formatTime(segment.startTimeMs)}
                          </button>
                        </div>
                        <div className="flex-1 min-w-0">
                          {isEditing ? (
                            <div className="space-y-2">
                              <div className="grid grid-cols-2 gap-2">
                                <Input
                                  placeholder="说话人"
                                  value={editingSegment!.speaker}
                                  onChange={(e) =>
                                    setEditingSegment((prev) =>
                                      prev
                                        ? { ...prev, speaker: e.target.value }
                                        : prev
                                    )
                                  }
                                  className="h-8 text-sm"
                                />
                                <div className="flex gap-1">
                                  <Input
                                    type="number"
                                    placeholder="开始ms"
                                    value={String(editingSegment!.startTimeMs)}
                                    onChange={(e) =>
                                      setEditingSegment((prev) =>
                                        prev
                                          ? {
                                              ...prev,
                                              startTimeMs: Number(e.target.value),
                                            }
                                          : prev
                                      )
                                    }
                                    className="h-8 text-sm"
                                  />
                                  <Input
                                    type="number"
                                    placeholder="结束ms"
                                    value={String(editingSegment!.endTimeMs)}
                                    onChange={(e) =>
                                      setEditingSegment((prev) =>
                                        prev
                                          ? { ...prev, endTimeMs: Number(e.target.value) }
                                          : prev
                                      )
                                    }
                                    className="h-8 text-sm"
                                  />
                                </div>
                              </div>
                              <Textarea
                                value={editingSegment!.text}
                                onChange={(e) =>
                                  setEditingSegment((prev) =>
                                    prev ? { ...prev, text: e.target.value } : prev
                                  )
                                }
                                rows={2}
                                className="text-sm"
                              />
                              <div className="flex justify-end gap-2">
                                <Button
                                  size="sm"
                                  variant="ghost"
                                  onClick={() => setEditingSegment(null)}
                                >
                                  <X className="mr-1 h-3.5 w-3.5" />
                                  取消
                                </Button>
                                <Button size="sm" onClick={handleSegmentSave}>
                                  <Save className="mr-1 h-3.5 w-3.5" />
                                  保存
                                </Button>
                              </div>
                            </div>
                          ) : (
                            <div>
                              <div className="flex items-center gap-2">
                                <span
                                  className={cn(
                                    "text-sm font-semibold",
                                    speakerColor
                                  )}
                                >
                                  {segment.speaker || "未知"}
                                </span>
                                <span className="text-xs text-muted-foreground font-mono">
                                  {formatTime(segment.startTimeMs)} -{" "}
                                  {formatTime(segment.endTimeMs)}
                                </span>
                              </div>
                              <p
                                className="mt-0.5 cursor-pointer text-sm leading-relaxed hover:text-primary transition-colors"
                                onClick={() => seekTo(segment.startTimeMs)}
                              >
                                {segment.text}
                              </p>
                            </div>
                          )}
                        </div>
                        {!isEditing && (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity"
                            onClick={() => handleSegmentEdit(segment)}
                          >
                            <Edit className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="tasks" className="mt-4">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <CardTitle className="text-base flex items-center gap-2">
                    <ListTodo className="h-4 w-4" />
                    任务列表
                  </CardTitle>
                  <CardDescription className="mt-1">
                    共 {tasks.length} 个任务
                  </CardDescription>
                </div>
                <Button size="sm" onClick={() => setTaskDialogOpen(true)}>
                  <Plus className="mr-1.5 h-4 w-4" />
                  新建任务
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {tasksLoading ? (
                <div className="flex justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : tasks.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                  <ListTodo className="h-10 w-10 text-muted-foreground/40" />
                  <p className="mt-3 font-medium">暂无任务</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    创建任务来跟踪制作进度
                  </p>
                  <Button
                    className="mt-4"
                    onClick={() => setTaskDialogOpen(true)}
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    新建任务
                  </Button>
                </div>
              ) : (
                <div className="space-y-2">
                  {tasks.map((task) => (
                    <div
                      key={task.id}
                      className="flex items-start gap-3 rounded-lg border p-3 transition-colors hover:bg-muted/50"
                    >
                      <div className="flex-1 min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={cn(
                              "font-medium text-sm",
                              task.status === "DONE" &&
                                "text-muted-foreground line-through"
                            )}
                          >
                            {task.title}
                          </span>
                          <Badge
                            variant="outline"
                            className={cn(
                              "text-xs border",
                              TASK_STATUS_BADGE[task.status]
                            )}
                          >
                            {TASK_STATUS_LABELS[task.status] ?? task.status}
                          </Badge>
                        </div>
                        {task.description && (
                          <p className="mt-1 text-sm text-muted-foreground">
                            {task.description}
                          </p>
                        )}
                        <div className="mt-1.5 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                          {(task.assigneeName || task.assigneeId) && (
                            <span className="flex items-center gap-1">
                              <User className="h-3 w-3" />
                              {task.assigneeName ?? task.assigneeId}
                            </span>
                          )}
                          {task.dueDate && (
                            <span className="flex items-center gap-1">
                              <Calendar className="h-3 w-3" />
                              截止 {formatDate(task.dueDate)}
                            </span>
                          )}
                          <span className="flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {formatDateTime(task.createdAt)}
                          </span>
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-1">
                        <Select
                          value={task.status}
                          onValueChange={(value) =>
                            handleTaskStatusChange(task.id, value)
                          }
                        >
                          <SelectTrigger className="h-8 w-[110px] text-xs">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {TASK_STATUSES.map((s) => (
                              <SelectItem key={s} value={s}>
                                {TASK_STATUS_LABELS[s] ?? s}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {task.status !== "DONE" && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-8"
                            onClick={() => handleMarkTaskDone(task.id)}
                          >
                            <Check className="mr-1 h-3.5 w-3.5" />
                            完成
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="versions" className="mt-4">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <CardTitle className="text-base flex items-center gap-2">
                    <History className="h-4 w-4" />
                    音频版本
                  </CardTitle>
                  <CardDescription className="mt-1">
                    共 {versions.length} 个版本
                  </CardDescription>
                </div>
                <Button
                  size="sm"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                >
                  <Upload className="mr-1.5 h-4 w-4" />
                  上传新版本
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {versionsLoading ? (
                <div className="flex justify-center py-12">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : versions.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                  <FileAudio className="h-10 w-10 text-muted-foreground/40" />
                  <p className="mt-3 font-medium">暂无音频版本</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    上传第一个音频文件开始制作
                  </p>
                  <Button
                    className="mt-4"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Upload className="mr-2 h-4 w-4" />
                    上传音频
                  </Button>
                </div>
              ) : (
                <div className="space-y-2">
                  {versions
                    .slice()
                    .sort((a, b) => b.versionNumber - a.versionNumber)
                    .map((version) => {
                      const isSelected = version.id === selectedVersionId;
                      return (
                        <div
                          key={version.id}
                          className={cn(
                            "flex flex-wrap items-center gap-3 rounded-lg border p-3 transition-colors cursor-pointer hover:bg-muted/50",
                            isSelected
                              ? "border-primary bg-primary/5"
                              : "border-border"
                          )}
                          onClick={() => setSelectedVersionId(version.id)}
                        >
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                            <FileAudio className="h-5 w-5 text-primary" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-semibold text-sm">
                                v{version.versionNumber}
                              </span>
                              <span className="text-sm truncate">
                                {version.fileName}
                              </span>
                              {version.isFinal && (
                                <Badge className="bg-green-100 text-green-700 border-green-200 border text-xs">
                                  <Check className="mr-1 h-3 w-3" />
                                  最终版
                                </Badge>
                              )}
                              {version.isArchived && (
                                <Badge
                                  variant="outline"
                                  className="text-xs border-gray-300 text-gray-500"
                                >
                                  已归档
                                </Badge>
                              )}
                              {isSelected && !version.isFinal && (
                                <Badge
                                  variant="outline"
                                  className="text-xs border-primary text-primary"
                                >
                                  当前选中
                                </Badge>
                              )}
                            </div>
                            <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                              {version.durationMs != null && (
                                <span className="flex items-center gap-1">
                                  <Clock className="h-3 w-3" />
                                  {formatTime(version.durationMs)}
                                </span>
                              )}
                              <span className="flex items-center gap-1">
                                <Calendar className="h-3 w-3" />
                                {formatDateTime(version.uploadedAt || version.createdAt)}
                              </span>
                              {(version.uploadedByName || version.uploadedBy) && (
                                <span className="flex items-center gap-1">
                                  <User className="h-3 w-3" />
                                  {version.uploadedByName ?? version.uploadedBy}
                                </span>
                              )}
                            </div>
                          </div>
                          <div className="flex shrink-0 items-center gap-2">
                            {isSelected && (
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  togglePlay();
                                }}
                              >
                                {isPlaying ? (
                                  <Pause className="mr-1 h-3.5 w-3.5" />
                                ) : (
                                  <Play className="mr-1 h-3.5 w-3.5" />
                                )}
                                {isPlaying ? "暂停" : "播放"}
                              </Button>
                            )}
                            {!version.isFinal && !version.isArchived && (
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleSetFinal(version.id);
                                }}
                              >
                                <Check className="mr-1 h-3.5 w-3.5" />
                                设为最终版
                              </Button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog open={markerDialogOpen} onOpenChange={setMarkerDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingMarkerId ? "编辑标记" : "添加标记"}
            </DialogTitle>
            <DialogDescription>
              {editingMarkerId
                ? "修改标记信息"
                : "在当前播放位置添加一个时间轴标记"}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleMarkerSubmit}>
            <div className="space-y-4 py-2">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="marker-start">开始时间 (ms)</Label>
                  <Input
                    id="marker-start"
                    type="number"
                    min="0"
                    value={String(markerForm.startTimeMs)}
                    onChange={(e) =>
                      setMarkerForm((prev) => ({
                        ...prev,
                        startTimeMs: Number(e.target.value),
                      }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="marker-end">结束时间 (ms, 可选)</Label>
                  <Input
                    id="marker-end"
                    type="number"
                    min="0"
                    value={markerForm.endTimeMs != null ? String(markerForm.endTimeMs) : ""}
                    onChange={(e) =>
                      setMarkerForm((prev) => ({
                        ...prev,
                        endTimeMs: e.target.value ? Number(e.target.value) : null,
                      }))
                    }
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="marker-type">标记类型</Label>
                <Select
                  value={markerForm.type}
                  onValueChange={(value) =>
                    setMarkerForm((prev) => ({ ...prev, type: value }))
                  }
                >
                  <SelectTrigger id="marker-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MARKER_TYPES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {MARKER_TYPE_LABELS[t] ?? t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="marker-desc">描述</Label>
                <Textarea
                  id="marker-desc"
                  rows={3}
                  placeholder="描述此标记的内容..."
                  value={markerForm.description}
                  onChange={(e) =>
                    setMarkerForm((prev) => ({
                      ...prev,
                      description: e.target.value,
                    }))
                  }
                />
              </div>
            </div>
            <DialogFooter className="mt-4 gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setMarkerDialogOpen(false);
                  setEditingMarkerId(null);
                }}
                disabled={markerSubmitting}
              >
                取消
              </Button>
              <Button type="submit" disabled={markerSubmitting}>
                {markerSubmitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                {editingMarkerId ? "保存" : "添加"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={transcriptUploadOpen}
        onOpenChange={setTranscriptUploadOpen}
      >
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>上传转写文本</DialogTitle>
            <DialogDescription>
              每行格式：开始ms-结束ms|说话人|文本，或粘贴 JSON 数组
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleTranscriptUpload}>
            <Textarea
              rows={12}
              placeholder={`0-5000|主持人|大家好，欢迎收听本期节目\n5000-10000|嘉宾|谢谢邀请，很高兴来到这里\n\n或 JSON 格式：\n[{"startTimeMs":0,"endTimeMs":5000,"speaker":"主持人","text":"..."}]`}
              value={transcriptUploadText}
              onChange={(e) => setTranscriptUploadText(e.target.value)}
              className="font-mono text-xs"
            />
            <DialogFooter className="mt-4 gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setTranscriptUploadOpen(false)}
                disabled={transcriptSubmitting}
              >
                取消
              </Button>
              <Button type="submit" disabled={transcriptSubmitting}>
                {transcriptSubmitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                上传
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={taskDialogOpen} onOpenChange={setTaskDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>新建任务</DialogTitle>
            <DialogDescription>为当前单集创建一个协作任务</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleTaskSubmit}>
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <Label htmlFor="task-title">
                  任务标题 <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="task-title"
                  placeholder="输入任务标题"
                  value={taskForm.title}
                  onChange={(e) =>
                    setTaskForm((prev) => ({ ...prev, title: e.target.value }))
                  }
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="task-desc">任务描述</Label>
                <Textarea
                  id="task-desc"
                  rows={3}
                  placeholder="详细描述任务内容..."
                  value={taskForm.description}
                  onChange={(e) =>
                    setTaskForm((prev) => ({
                      ...prev,
                      description: e.target.value,
                    }))
                  }
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="task-assignee">负责人 ID</Label>
                  <Input
                    id="task-assignee"
                    placeholder="指派给..."
                    value={taskForm.assigneeId}
                    onChange={(e) =>
                      setTaskForm((prev) => ({
                        ...prev,
                        assigneeId: e.target.value,
                      }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="task-due">截止日期</Label>
                  <Input
                    id="task-due"
                    type="date"
                    value={taskForm.dueDate}
                    onChange={(e) =>
                      setTaskForm((prev) => ({ ...prev, dueDate: e.target.value }))
                    }
                  />
                </div>
              </div>
            </div>
            <DialogFooter className="mt-4 gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setTaskDialogOpen(false)}
                disabled={taskSubmitting}
              >
                取消
              </Button>
              <Button type="submit" disabled={taskSubmitting}>
                {taskSubmitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                创建
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <input
        ref={fileInputRef}
        type="file"
        accept=".wav,.mp3,.m4a"
        className="hidden"
        onChange={handleFileUpload}
      />
    </div>
  );
}
