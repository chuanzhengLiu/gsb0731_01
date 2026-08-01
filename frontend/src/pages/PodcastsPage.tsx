import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Plus,
  Loader2,
  Radio,
  ChevronRight,
  Mic,
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
  CardFooter,
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
import { cn, PODCAST_TYPE_LABELS } from "@/lib/utils";
import type { Team, Podcast } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

interface PodcastWithCount extends Podcast {
  _count?: { episodes?: number };
  episodeCount?: number;
}

interface PodcastFormState {
  name: string;
  type: string;
  updateFrequency: string;
  targetDuration: string;
  description: string;
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

function getEpisodeCount(podcast: PodcastWithCount): number {
  if (podcast._count?.episodes != null) return podcast._count.episodes;
  if (podcast.episodeCount != null) return podcast.episodeCount;
  return 0;
}

const EMPTY_FORM: PodcastFormState = {
  name: "",
  type: "INTERVIEW",
  updateFrequency: "",
  targetDuration: "",
  description: "",
};

export function PodcastsPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<Team[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState<string>("");
  const numericTeamId = selectedTeamId ? Number(selectedTeamId) : 0;
  const [podcasts, setPodcasts] = useState<PodcastWithCount[]>([]);

  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<PodcastFormState>(EMPTY_FORM);

  useEffect(() => {
    const loadTeams = async () => {
      try {
        const res = await api.get("/teams");
        const data: Team[] = res.data.data;
        setTeams(data);
        if (data.length > 0) {
          setSelectedTeamId(String(data[0].id));
        } else {
          setLoading(false);
        }
      } catch (err) {
        toast.error(getErrorMessage(err, "加载团队列表失败"));
        setLoading(false);
      }
    };
    loadTeams();
  }, [toast]);

  useEffect(() => {
    if (!selectedTeamId) {
      setPodcasts([]);
      return;
    }
    const loadPodcasts = async () => {
      setLoading(true);
      try {
        const res = await api.get("/podcasts", {
          params: { teamId: selectedTeamId },
        });
        setPodcasts(res.data.data);
      } catch (err) {
        toast.error(getErrorMessage(err, "加载节目列表失败"));
      } finally {
        setLoading(false);
      }
    };
    loadPodcasts();
  }, [selectedTeamId, toast]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast.error("请输入节目名称");
      return;
    }
    if (!selectedTeamId) {
      toast.error("请先选择团队");
      return;
    }
    setSubmitting(true);
    try {
      await api.post("/podcasts", {
        name: form.name.trim(),
        type: form.type,
        updateFrequency: form.updateFrequency || null,
        targetDuration: form.targetDuration
          ? Number(form.targetDuration)
          : null,
        description: form.description || null,
        structureTemplateJson: null,
      }, { params: { teamId: Number(selectedTeamId) } });
      toast.success("节目创建成功");
      setCreateOpen(false);
      setForm(EMPTY_FORM);
      const res = await api.get("/podcasts", {
        params: { teamId: selectedTeamId },
      });
      setPodcasts(res.data.data);
    } catch (err) {
      toast.error(getErrorMessage(err, "创建节目失败"));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading && teams.length === 0) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">节目管理</h1>
          <p className="text-muted-foreground mt-1">管理您团队的所有播客节目</p>
        </div>
        <div className="flex items-center gap-3">
          {teams.length > 1 && (
            <Select value={selectedTeamId} onValueChange={setSelectedTeamId}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="选择团队" />
              </SelectTrigger>
              <SelectContent>
                {teams.map((team) => (
                  <SelectItem key={team.id} value={String(team.id)}>
                    {team.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            创建节目
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="flex h-[40vh] items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : podcasts.length === 0 ? (
        <Card className="flex flex-col items-center justify-center py-20 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
            <Radio className="h-8 w-8 text-muted-foreground" />
          </div>
          <CardTitle className="mt-6 text-xl">暂无节目</CardTitle>
          <CardDescription className="mt-2 max-w-sm">
            您还没有创建任何播客节目，点击右上角按钮开始创建您的第一档节目。
          </CardDescription>
          <Button className="mt-6" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            创建节目
          </Button>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {podcasts.map((podcast) => (
            <Card
              key={podcast.id}
              className="cursor-pointer transition-all hover:shadow-md hover:border-primary/30 group"
              onClick={() => navigate(`/podcasts/${podcast.id}`)}
            >
              <CardHeader>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                    <Mic className="h-5 w-5 text-primary" />
                  </div>
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
                <CardTitle className="mt-3 text-lg group-hover:text-primary transition-colors">
                  {podcast.name}
                </CardTitle>
                <CardDescription className="line-clamp-2 min-h-[2.5rem]">
                  {podcast.description || "暂无简介"}
                </CardDescription>
              </CardHeader>
              <CardFooter className="flex items-center justify-between border-t pt-4">
                <span className="text-sm text-muted-foreground">
                  {getEpisodeCount(podcast)} 期单集
                </span>
                <span className="flex items-center text-sm text-primary font-medium">
                  查看详情
                  <ChevronRight className="ml-1 h-4 w-4" />
                </span>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>创建节目</DialogTitle>
            <DialogDescription>
              创建一档新的播客节目，填写基本信息后即可开始制作单集。
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <Label htmlFor="podcast-name">
                  节目名称 <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="podcast-name"
                  placeholder="输入节目名称"
                  value={form.name}
                  onChange={(e) =>
                    setForm((prev) => ({ ...prev, name: e.target.value }))
                  }
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="podcast-type">节目类型</Label>
                  <Select
                    value={form.type}
                    onValueChange={(value) =>
                      setForm((prev) => ({ ...prev, type: value }))
                    }
                  >
                    <SelectTrigger id="podcast-type">
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
                  <Label htmlFor="podcast-frequency">更新频率</Label>
                  <Input
                    id="podcast-frequency"
                    placeholder="如：每周更新"
                    value={form.updateFrequency}
                    onChange={(e) =>
                      setForm((prev) => ({
                        ...prev,
                        updateFrequency: e.target.value,
                      }))
                    }
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="podcast-duration">目标时长（分钟）</Label>
                <Input
                  id="podcast-duration"
                  type="number"
                  min="1"
                  placeholder="如：30"
                  value={form.targetDuration}
                  onChange={(e) =>
                    setForm((prev) => ({
                      ...prev,
                      targetDuration: e.target.value,
                    }))
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="podcast-description">节目简介</Label>
                <Textarea
                  id="podcast-description"
                  placeholder="简要描述这档节目的内容和定位"
                  rows={3}
                  value={form.description}
                  onChange={(e) =>
                    setForm((prev) => ({
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
                onClick={() => setCreateOpen(false)}
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
