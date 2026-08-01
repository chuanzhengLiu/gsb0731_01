import { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Plus,
  Loader2,
  ArrowLeft,
  Pencil,
  Mic,
  Calendar,
  Clock,
  ListTodo,
  Radio,
} from "lucide-react";
import { AxiosError } from "axios";
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
import { useToast } from "@/components/ui/toast";
import {
  cn,
  formatDate,
  formatDateTime,
  EPISODE_STATUS_LABELS,
  PODCAST_TYPE_LABELS,
} from "@/lib/utils";
import type { Podcast, Episode, Task } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

interface EpisodeWithMeta extends Episode {
  tasks?: Task[];
  taskCount?: number;
  _count?: { tasks?: number };
}

interface PodcastFormState {
  name: string;
  type: string;
  updateFrequency: string;
  targetDuration: string;
  description: string;
}

interface EpisodeFormState {
  title: string;
  theme: string;
  recordDate: string;
  number: string;
  scheduledAt: string;
}

const PODCAST_TYPES = ["INTERVIEW", "NARRATIVE", "KNOWLEDGE", "NEWS"];

const TYPE_BADGE_CLASSES: Record<string, string> = {
  INTERVIEW: "bg-blue-100 text-blue-700 border-blue-200",
  NARRATIVE: "bg-purple-100 text-purple-700 border-purple-200",
  KNOWLEDGE: "bg-amber-100 text-amber-700 border-amber-200",
  NEWS: "bg-rose-100 text-rose-700 border-rose-200",
};

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

function getStatusBadgeClass(status: string): string {
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

function getTaskCount(episode: EpisodeWithMeta): number {
  if (episode._count?.tasks != null) return episode._count.tasks;
  if (episode.taskCount != null) return episode.taskCount;
  if (episode.tasks && Array.isArray(episode.tasks)) return episode.tasks.length;
  return 0;
}

const EMPTY_PODCAST_FORM: PodcastFormState = {
  name: "",
  type: "INTERVIEW",
  updateFrequency: "",
  targetDuration: "",
  description: "",
};

const EMPTY_EPISODE_FORM: EpisodeFormState = {
  title: "",
  theme: "",
  recordDate: "",
  number: "",
  scheduledAt: "",
};

export function PodcastDetailPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { id } = useParams<{ id: string }>();

  const [loading, setLoading] = useState(true);
  const [podcast, setPodcast] = useState<Podcast | null>(null);
  const [episodes, setEpisodes] = useState<EpisodeWithMeta[]>([]);

  const [episodeDialogOpen, setEpisodeDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [podcastForm, setPodcastForm] =
    useState<PodcastFormState>(EMPTY_PODCAST_FORM);
  const [episodeForm, setEpisodeForm] =
    useState<EpisodeFormState>(EMPTY_EPISODE_FORM);

  const loadData = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [podcastRes, episodesRes] = await Promise.all([
        api.get(`/podcasts/${id}`),
        api.get("/episodes", { params: { podcastId: id } }),
      ]);
      const podcastData: Podcast = podcastRes.data.data;
      setPodcast(podcastData);
      const episodesData: EpisodeWithMeta[] = episodesRes.data.data;
      episodesData.sort((a, b) => {
        const na = a.number ?? 0;
        const nb = b.number ?? 0;
        return nb - na;
      });
      setEpisodes(episodesData);
    } catch (err) {
      toast.error(getErrorMessage(err, "加载节目详情失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [id]);

  const openEditDialog = () => {
    if (!podcast) return;
    setPodcastForm({
      name: podcast.name ?? "",
      type: podcast.type ?? "INTERVIEW",
      updateFrequency: podcast.updateFrequency ?? "",
      targetDuration:
        podcast.targetDuration != null ? String(podcast.targetDuration) : "",
      description: podcast.description ?? "",
    });
    setEditDialogOpen(true);
  };

  const handleUpdatePodcast = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!podcast) return;
    if (!podcastForm.name.trim()) {
      toast.error("请输入节目名称");
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.put(`/podcasts/${podcast.id}`, {
        name: podcastForm.name.trim(),
        type: podcastForm.type,
        updateFrequency: podcastForm.updateFrequency || null,
        targetDuration: podcastForm.targetDuration
          ? Number(podcastForm.targetDuration)
          : null,
        description: podcastForm.description || null,
      });
      setPodcast(res.data.data);
      toast.success("节目更新成功");
      setEditDialogOpen(false);
    } catch (err) {
      toast.error(getErrorMessage(err, "更新节目失败"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateEpisode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!podcast) return;
    if (!episodeForm.title.trim()) {
      toast.error("请输入单集标题");
      return;
    }
    setSubmitting(true);
    try {
      await api.post("/episodes", {
        title: episodeForm.title.trim(),
        theme: episodeForm.theme || null,
        recordDate: episodeForm.recordDate || null,
        number: episodeForm.number ? Number(episodeForm.number) : null,
        scheduledAt: episodeForm.scheduledAt || null,
      }, { params: { podcastId: podcast.id } });
      toast.success("单集创建成功");
      setEpisodeDialogOpen(false);
      setEpisodeForm(EMPTY_EPISODE_FORM);
      const episodesRes = await api.get("/episodes", {
        params: { podcastId: podcast.id },
      });
      const episodesData: EpisodeWithMeta[] = episodesRes.data.data;
      episodesData.sort((a, b) => {
        const na = a.number ?? 0;
        const nb = b.number ?? 0;
        return nb - na;
      });
      setEpisodes(episodesData);
    } catch (err) {
      toast.error(getErrorMessage(err, "创建单集失败"));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!podcast) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-4 p-6">
        <p className="text-muted-foreground">节目不存在或已被删除</p>
        <Button variant="outline" onClick={() => navigate("/podcasts")}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          返回节目列表
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex items-center gap-4">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => navigate("/podcasts")}
        >
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div className="flex-1">
          <h1 className="text-3xl font-bold tracking-tight">{podcast.name}</h1>
          <p className="text-muted-foreground mt-1">节目详情与单集管理</p>
        </div>
        <Button variant="outline" onClick={openEditDialog}>
          <Pencil className="mr-2 h-4 w-4" />
          编辑节目
        </Button>
        <Button onClick={() => setEpisodeDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          新建单集
        </Button>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-start gap-4">
              <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                <Radio className="h-7 w-7 text-primary" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <CardTitle className="text-xl">{podcast.name}</CardTitle>
                  <Badge
                    variant="outline"
                    className={cn(
                      "border",
                      TYPE_BADGE_CLASSES[podcast.type] ??
                        "bg-gray-100 text-gray-700 border-gray-200"
                    )}
                  >
                    {PODCAST_TYPE_LABELS[podcast.type] ?? podcast.type}
                  </Badge>
                </div>
                <CardDescription className="mt-1 max-w-2xl">
                  {podcast.description || "暂无简介"}
                </CardDescription>
              </div>
            </div>
            <div className="flex flex-wrap gap-6 text-sm">
              {podcast.targetDuration != null && (
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-muted-foreground" />
                  <span className="text-muted-foreground">目标时长</span>
                  <span className="font-medium">
                    {podcast.targetDuration} 分钟
                  </span>
                </div>
              )}
              {podcast.updateFrequency && (
                <div className="flex items-center gap-2">
                  <Calendar className="h-4 w-4 text-muted-foreground" />
                  <span className="text-muted-foreground">更新频率</span>
                  <span className="font-medium">
                    {podcast.updateFrequency}
                  </span>
                </div>
              )}
              <div className="flex items-center gap-2">
                <Mic className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground">单集数</span>
                <span className="font-medium">{episodes.length}</span>
              </div>
            </div>
          </div>
        </CardHeader>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>单集列表</CardTitle>
          <CardDescription>该节目下的所有单集</CardDescription>
        </CardHeader>
        <CardContent>
          {episodes.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
                <Mic className="h-7 w-7 text-muted-foreground" />
              </div>
              <p className="mt-4 font-medium">暂无单集</p>
              <p className="mt-1 text-sm text-muted-foreground">
                点击右上角"新建单集"开始制作
              </p>
              <Button
                className="mt-4"
                onClick={() => setEpisodeDialogOpen(true)}
              >
                <Plus className="mr-2 h-4 w-4" />
                新建单集
              </Button>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="py-3 pr-4 font-medium w-16">期数</th>
                    <th className="py-3 pr-4 font-medium">标题</th>
                    <th className="py-3 pr-4 font-medium w-28">状态</th>
                    <th className="py-3 pr-4 font-medium w-32">录制日期</th>
                    <th className="py-3 pr-4 font-medium w-20">任务数</th>
                    <th className="py-3 pr-4 font-medium w-36">创建时间</th>
                  </tr>
                </thead>
                <tbody>
                  {episodes.map((episode) => (
                    <tr
                      key={episode.id}
                      className="border-b last:border-0 cursor-pointer transition-colors hover:bg-muted/50"
                      onClick={() => navigate(`/episodes/${episode.id}`)}
                    >
                      <td className="py-3 pr-4">
                        {episode.number != null ? (
                          <span className="font-medium">
                            第{episode.number}期
                          </span>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </td>
                      <td className="py-3 pr-4">
                        <span className="font-medium hover:text-primary transition-colors">
                          {episode.title}
                        </span>
                        {episode.theme && (
                          <p className="text-xs text-muted-foreground truncate max-w-md">
                            {episode.theme}
                          </p>
                        )}
                      </td>
                      <td className="py-3 pr-4">
                        <Badge
                          variant="outline"
                          className={cn(
                            "border",
                            getStatusBadgeClass(episode.status)
                          )}
                        >
                          {EPISODE_STATUS_LABELS[episode.status] ??
                            episode.status}
                        </Badge>
                      </td>
                      <td className="py-3 pr-4 text-muted-foreground">
                        {episode.recordDate
                          ? formatDate(episode.recordDate)
                          : "-"}
                      </td>
                      <td className="py-3 pr-4">
                        <span className="inline-flex items-center gap-1 text-muted-foreground">
                          <ListTodo className="h-3.5 w-3.5" />
                          {getTaskCount(episode)}
                        </span>
                      </td>
                      <td className="py-3 pr-4 text-muted-foreground">
                        {formatDateTime(episode.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>编辑节目</DialogTitle>
            <DialogDescription>修改节目的基本信息</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdatePodcast}>
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <Label htmlFor="edit-podcast-name">
                  节目名称 <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="edit-podcast-name"
                  value={podcastForm.name}
                  onChange={(e) =>
                    setPodcastForm((prev) => ({
                      ...prev,
                      name: e.target.value,
                    }))
                  }
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-podcast-type">节目类型</Label>
                  <Select
                    value={podcastForm.type}
                    onValueChange={(value) =>
                      setPodcastForm((prev) => ({ ...prev, type: value }))
                    }
                  >
                    <SelectTrigger id="edit-podcast-type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PODCAST_TYPES.map((t) => (
                        <SelectItem key={t} value={t}>
                          {PODCAST_TYPE_LABELS[t] ?? t}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="edit-podcast-frequency">更新频率</Label>
                  <Input
                    id="edit-podcast-frequency"
                    value={podcastForm.updateFrequency}
                    onChange={(e) =>
                      setPodcastForm((prev) => ({
                        ...prev,
                        updateFrequency: e.target.value,
                      }))
                    }
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-podcast-duration">
                  目标时长（分钟）
                </Label>
                <Input
                  id="edit-podcast-duration"
                  type="number"
                  min="1"
                  value={podcastForm.targetDuration}
                  onChange={(e) =>
                    setPodcastForm((prev) => ({
                      ...prev,
                      targetDuration: e.target.value,
                    }))
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-podcast-description">节目简介</Label>
                <Textarea
                  id="edit-podcast-description"
                  rows={3}
                  value={podcastForm.description}
                  onChange={(e) =>
                    setPodcastForm((prev) => ({
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
                onClick={() => setEditDialogOpen(false)}
                disabled={submitting}
              >
                取消
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                保存
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={episodeDialogOpen}
        onOpenChange={setEpisodeDialogOpen}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>新建单集</DialogTitle>
            <DialogDescription>
              为「{podcast.name}」创建一期新的单集
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateEpisode}>
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <Label htmlFor="episode-title">
                  单集标题 <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="episode-title"
                  placeholder="输入单集标题"
                  value={episodeForm.title}
                  onChange={(e) =>
                    setEpisodeForm((prev) => ({
                      ...prev,
                      title: e.target.value,
                    }))
                  }
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="episode-theme">主题</Label>
                <Input
                  id="episode-theme"
                  placeholder="本期讨论的主题"
                  value={episodeForm.theme}
                  onChange={(e) =>
                    setEpisodeForm((prev) => ({
                      ...prev,
                      theme: e.target.value,
                    }))
                  }
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="episode-number">期数</Label>
                  <Input
                    id="episode-number"
                    type="number"
                    min="1"
                    placeholder="如：1"
                    value={episodeForm.number}
                    onChange={(e) =>
                      setEpisodeForm((prev) => ({
                        ...prev,
                        number: e.target.value,
                      }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="episode-record-date">录制日期</Label>
                  <Input
                    id="episode-record-date"
                    type="date"
                    value={episodeForm.recordDate}
                    onChange={(e) =>
                      setEpisodeForm((prev) => ({
                        ...prev,
                        recordDate: e.target.value,
                      }))
                    }
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="episode-scheduled">计划发布时间</Label>
                <Input
                  id="episode-scheduled"
                  type="datetime-local"
                  value={episodeForm.scheduledAt}
                  onChange={(e) =>
                    setEpisodeForm((prev) => ({
                      ...prev,
                      scheduledAt: e.target.value,
                    }))
                  }
                />
              </div>
            </div>
            <DialogFooter className="mt-4 gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setEpisodeDialogOpen(false)}
                disabled={submitting}
              >
                取消
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                创建
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
