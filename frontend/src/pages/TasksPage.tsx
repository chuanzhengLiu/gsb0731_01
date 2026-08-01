import { useState, useEffect, useMemo } from "react";
import {
  Loader2,
  ListTodo,
  User,
  CalendarClock,
  AlertCircle,
  Mic,
  Inbox,
} from "lucide-react";
import { AxiosError } from "axios";
import {
  Card,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/toast";
import { cn, formatDate, TASK_STATUS_LABELS } from "@/lib/utils";
import type { Task, Episode, Team, Podcast } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE" | "CANCELLED";

interface TaskResponse extends Omit<Task, 'assigneeName' | 'creatorName'> {
  assigneeName?: string | null;
  creatorName?: string | null;
}

const TASK_STATUSES: TaskStatus[] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
  "CANCELLED",
];

const COLUMN_META: Record<
  TaskStatus,
  { label: string; dotClass: string; accentClass: string }
> = {
  TODO: {
    label: TASK_STATUS_LABELS.TODO ?? "待办",
    dotClass: "bg-gray-400",
    accentClass: "border-t-gray-300",
  },
  IN_PROGRESS: {
    label: TASK_STATUS_LABELS.IN_PROGRESS ?? "进行中",
    dotClass: "bg-blue-500",
    accentClass: "border-t-blue-400",
  },
  DONE: {
    label: TASK_STATUS_LABELS.DONE ?? "已完成",
    dotClass: "bg-green-500",
    accentClass: "border-t-green-400",
  },
  CANCELLED: {
    label: TASK_STATUS_LABELS.CANCELLED ?? "已取消",
    dotClass: "bg-zinc-400",
    accentClass: "border-t-zinc-300",
  },
};

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

function isOverdue(dueDate: string | null | undefined): boolean {
  if (!dueDate) return false;
  const time = new Date(dueDate).getTime();
  if (Number.isNaN(time)) return false;
  return time < Date.now();
}

export function TasksPage() {
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [episodeMap, setEpisodeMap] = useState<Record<string, Episode>>({});
  const [podcastMap, setPodcastMap] = useState<Record<string, string>>({});
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [tasksRes, teamsRes] = await Promise.all([
          api.get("/tasks/my"),
          api.get("/teams"),
        ]);

        const tasksData: TaskResponse[] = tasksRes.data.data;
        const teamsData: Team[] = teamsRes.data.data;

        setTasks(tasksData);

        if (teamsData.length === 0) {
          setEpisodeMap({});
          setPodcastMap({});
          return;
        }

        const podcastResults = await Promise.all(
          teamsData.map((team) =>
            api
              .get("/podcasts", { params: { teamId: team.id } })
              .then((res) => res.data.data as Podcast[])
              .catch(() => [] as Podcast[])
          )
        );
        const allPodcasts = podcastResults.flat();
        const pMap: Record<string, string> = {};
        for (const p of allPodcasts) {
          pMap[String(p.id)] = p.name;
        }
        setPodcastMap(pMap);

        if (allPodcasts.length === 0) {
          setEpisodeMap({});
          return;
        }

        const episodeResults = await Promise.all(
          allPodcasts.map((podcast) =>
            api
              .get("/episodes", { params: { podcastId: podcast.id } })
              .then((res) => res.data.data as Episode[])
              .catch(() => [] as Episode[])
          )
        );
        const allEpisodes = episodeResults.flat();
        const map: Record<string, Episode> = {};
        for (const ep of allEpisodes) {
          map[String(ep.id)] = ep;
        }
        setEpisodeMap(map);
      } catch (err) {
        toast.error(getErrorMessage(err, "加载任务失败"));
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [toast]);

  const handleStatusChange = async (taskId: string, status: string) => {
    setUpdatingId(taskId);
    try {
      const res = await api.patch(
        `/tasks/${taskId}/status`,
        {},
        { params: { status } }
      );
      const updated: TaskResponse = res.data.data;
      setTasks((prev) =>
        prev.map((t) => (String(t.id) === taskId ? { ...t, ...updated } : t))
      );
      toast.success("任务状态已更新");
    } catch (err) {
      toast.error(getErrorMessage(err, "更新任务状态失败"));
    } finally {
      setUpdatingId(null);
    }
  };

  const grouped = useMemo(() => {
    const result: Record<TaskStatus, TaskResponse[]> = {
      TODO: [],
      IN_PROGRESS: [],
      DONE: [],
      CANCELLED: [],
    };
    for (const task of tasks) {
      const key = (task.status as TaskStatus) ?? "TODO";
      if (result[key]) {
        result[key].push(task);
      } else {
        result.TODO.push(task);
      }
    }
    return result;
  }, [tasks]);

  if (loading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">任务看板</h1>
          <p className="text-muted-foreground mt-1">
            管理分配给我的任务，拖动或更改状态以推进进度
          </p>
        </div>
        <Badge variant="secondary" className="gap-1.5">
          <ListTodo className="h-3.5 w-3.5" />
          共 {tasks.length} 个任务
        </Badge>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {TASK_STATUSES.map((status) => {
          const columnTasks = grouped[status];
          const meta = COLUMN_META[status];
          return (
            <div
              key={status}
              className="flex flex-col rounded-xl border bg-muted/30"
            >
              <div className="flex items-center justify-between border-b px-4 py-3">
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      "h-2.5 w-2.5 rounded-full",
                      meta.dotClass
                    )}
                  />
                  <h2 className="text-sm font-semibold">{meta.label}</h2>
                </div>
                <Badge variant="outline" className="text-xs">
                  {columnTasks.length}
                </Badge>
              </div>

              <div className="flex flex-1 flex-col gap-3 p-3 min-h-[200px]">
                {columnTasks.length === 0 ? (
                  <div className="flex flex-1 flex-col items-center justify-center rounded-lg border border-dashed py-10 text-center">
                    <Inbox className="h-7 w-7 text-muted-foreground/40" />
                    <p className="mt-2 text-xs text-muted-foreground">
                      暂无任务
                    </p>
                  </div>
                ) : (
                  columnTasks.map((task) => {
                    const episode = episodeMap[String(task.episodeId)];
                    const overdue =
                      status !== "DONE" &&
                      status !== "CANCELLED" &&
                      isOverdue(task.dueDate);
                    const isUpdating = updatingId === String(task.id);
                    return (
                      <Card
                        key={task.id}
                        className={cn(
                          "border-t-4 shadow-sm transition-shadow hover:shadow-md",
                          meta.accentClass,
                          overdue && "border-red-400 bg-red-50/40"
                        )}
                      >
                        <CardContent className="space-y-3 p-4">
                          <div className="flex items-start justify-between gap-2">
                            <p className="text-sm font-medium leading-snug">
                              {task.title}
                            </p>
                            {overdue && (
                              <AlertCircle className="h-4 w-4 shrink-0 text-red-500" />
                            )}
                          </div>

                          {episode && (
                            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                              <Mic className="h-3 w-3 shrink-0" />
                              <span className="truncate">
                                {podcastMap[String(episode.podcastId)]
                                  ? `${podcastMap[String(episode.podcastId)]} · `
                                  : ""}
                                {episode.title}
                              </span>
                            </div>
                          )}

                          <div className="flex flex-col gap-1.5 text-xs text-muted-foreground">
                            {task.assigneeName && (
                              <span className="flex items-center gap-1.5">
                                <User className="h-3 w-3" />
                                {task.assigneeName}
                              </span>
                            )}
                            <span
                              className={cn(
                                "flex items-center gap-1.5",
                                overdue && "text-red-600 font-medium"
                              )}
                            >
                              <CalendarClock className="h-3 w-3" />
                              {task.dueDate
                                ? formatDate(task.dueDate)
                                : "未设置截止日期"}
                              {overdue && "（已逾期）"}
                            </span>
                          </div>

                          <div
                            onClick={(e) => e.stopPropagation()}
                            className="pt-1"
                          >
                            <Select
                              value={task.status as string}
                              onValueChange={(value) =>
                                handleStatusChange(String(task.id), value)
                              }
                              disabled={isUpdating}
                            >
                              <SelectTrigger className="h-8 w-full text-xs">
                                {isUpdating ? (
                                  <span className="flex items-center gap-1.5 text-muted-foreground">
                                    <Loader2 className="h-3 w-3 animate-spin" />
                                    更新中...
                                  </span>
                                ) : (
                                  <SelectValue />
                                )}
                              </SelectTrigger>
                              <SelectContent>
                                {TASK_STATUSES.map((s) => (
                                  <SelectItem key={s} value={s}>
                                    {COLUMN_META[s].label}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>
                        </CardContent>
                      </Card>
                    );
                  })
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
