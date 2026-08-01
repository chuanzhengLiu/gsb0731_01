import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Mic,
  Radio,
  Loader2,
  Clock,
  ListTodo,
  Plus,
  ChevronRight,
  CalendarDays,
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
import { useToast } from "@/components/ui/toast";
import { cn, formatDate, EPISODE_STATUS_LABELS } from "@/lib/utils";
import type { Team, Podcast, Episode, Task } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

interface EpisodeWithTasks extends Episode {
  tasks?: Task[];
  taskCount?: number;
  _count?: { tasks?: number };
}

interface PodcastWithCount extends Podcast {
  _count?: { episodes?: number };
  episodeCount?: number;
}

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

function isTaskPending(task: Task): boolean {
  const status = (task.status || "").toUpperCase();
  return status !== "DONE" && status !== "CANCELLED";
}

export function DashboardPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<Team[]>([]);
  const [podcasts, setPodcasts] = useState<PodcastWithCount[]>([]);
  const [episodes, setEpisodes] = useState<EpisodeWithTasks[]>([]);

  useEffect(() => {
    const loadDashboard = async () => {
      setLoading(true);
      try {
        const teamsRes = await api.get("/teams");
        const teamsData: Team[] = teamsRes.data.data;
        setTeams(teamsData);

        if (teamsData.length === 0) {
          setLoading(false);
          return;
        }

        const teamId = teamsData[0].id;
        const podcastsRes = await api.get("/podcasts", {
          params: { teamId },
        });
        const podcastsData: PodcastWithCount[] = podcastsRes.data.data;
        setPodcasts(podcastsData);

        if (podcastsData.length === 0) {
          setEpisodes([]);
          setLoading(false);
          return;
        }

        const episodeResults = await Promise.all(
          podcastsData.map((p) =>
            api.get("/episodes", { params: { podcastId: p.id } })
          )
        );
        const allEpisodes: EpisodeWithTasks[] = episodeResults.flatMap(
          (res) => res.data.data
        );
        allEpisodes.sort((a, b) => {
          const da = new Date(a.createdAt).getTime();
          const db = new Date(b.createdAt).getTime();
          return db - da;
        });
        setEpisodes(allEpisodes);
      } catch (err) {
        toast.error(getErrorMessage(err, "加载工作台数据失败"));
      } finally {
        setLoading(false);
      }
    };

    loadDashboard();
  }, [toast]);

  const totalPodcasts = podcasts.length;
  const totalEpisodes = episodes.length;
  const episodesInProgress = episodes.filter(
    (ep) =>
      ep.status !== "PLANNING" &&
      ep.status !== "PUBLISHED" &&
      ep.status !== "FINALIZED"
  ).length;
  const pendingTasks = episodes.reduce((sum, ep) => {
    if (ep.tasks && Array.isArray(ep.tasks)) {
      return sum + ep.tasks.filter(isTaskPending).length;
    }
    if (ep._count?.tasks != null) {
      return sum + ep._count.tasks;
    }
    if (ep.taskCount != null) return sum + ep.taskCount;
    return sum;
  }, 0);

  const recentEpisodes = episodes.slice(0, 8);

  if (loading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (teams.length === 0) {
    return (
      <div className="flex flex-1 flex-col gap-6 p-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">工作台</h1>
          <p className="text-muted-foreground mt-1">
            欢迎使用播客制作协作系统
          </p>
        </div>
        <Card className="flex flex-col items-center justify-center py-20 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
            <Mic className="h-8 w-8 text-muted-foreground" />
          </div>
          <CardTitle className="mt-6 text-xl">暂无团队</CardTitle>
          <CardDescription className="mt-2 max-w-sm">
            您还没有加入任何团队，请联系管理员创建团队或接受团队邀请后开始协作。
          </CardDescription>
          <Button
            className="mt-6"
            onClick={() => toast.info("团队创建功能即将上线")}
          >
            <Plus className="mr-2 h-4 w-4" />
            创建团队
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">工作台</h1>
          <p className="text-muted-foreground mt-1">
            {teams[0]?.name} · 数据概览
          </p>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              节目总数
            </CardTitle>
            <Radio className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalPodcasts}</div>
            <p className="text-xs text-muted-foreground mt-1">
              团队下的所有节目
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              单集总数
            </CardTitle>
            <Mic className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalEpisodes}</div>
            <p className="text-xs text-muted-foreground mt-1">
              累计制作单集
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              制作中
            </CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{episodesInProgress}</div>
            <p className="text-xs text-muted-foreground mt-1">
              正在制作的单集
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              待办任务
            </CardTitle>
            <ListTodo className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{pendingTasks}</div>
            <p className="text-xs text-muted-foreground mt-1">
              未完成的任务数
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>最近单集</CardTitle>
          <CardDescription>团队中最新创建的单集</CardDescription>
        </CardHeader>
        <CardContent>
          {recentEpisodes.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <CalendarDays className="h-10 w-10 text-muted-foreground/50" />
              <p className="mt-3 text-sm text-muted-foreground">
                暂无单集，去创建您的第一期节目吧
              </p>
            </div>
          ) : (
            <ul className="divide-y">
              {recentEpisodes.map((episode) => {
                const podcast = podcasts.find(
                  (p) => p.id === episode.podcastId
                );
                return (
                  <li
                    key={episode.id}
                    className="flex cursor-pointer items-center justify-between py-3 transition-colors hover:bg-muted/50 -mx-2 px-2 rounded"
                    onClick={() => navigate(`/episodes/${episode.id}`)}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                        <Mic className="h-5 w-5 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <p className="font-medium truncate">
                          {episode.title}
                        </p>
                        <p className="text-sm text-muted-foreground truncate">
                          {podcast?.name ?? "未知节目"}
                          {episode.number != null && ` · 第${episode.number}期`}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 shrink-0 ml-4">
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
                      <span className="hidden text-sm text-muted-foreground sm:inline">
                        {formatDate(episode.createdAt)}
                      </span>
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
