import { useState, useEffect, useCallback } from "react";
import {
  Loader2,
  Radio,
  Mic,
  Tag,
  Clock,
  AlertTriangle,
  BarChart3,
  Users,
  Share2,
  TrendingUp,
} from "lucide-react";
import { AxiosError } from "axios";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
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
import type { Team, Podcast, Episode } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

interface MarkersPerUser {
  userId: string | number;
  name: string;
  count: number;
}

interface PlatformCoverage {
  platformName: string;
  count: number;
  publishedCount: number;
}

interface TeamStats {
  totalPodcasts: number;
  totalEpisodes: number;
  totalMarkers: number;
  avgResolutionTimeHours: number | null;
  overdueTasks: number;
  markersPerUser: MarkersPerUser[];
  platformCoverage: PlatformCoverage[];
}

interface EpisodeStat {
  episodeId: string | number;
  title: string;
  durationMs: number | null;
  totalMarkers: number;
  pendingMarkers: number;
  resolvedMarkers: number;
  taskCount: number;
  daysFromRecordToPublish: number | null;
  distributionCount: number;
  publishedPlatformCount: number;
}

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

function formatDuration(ms?: number | null): string {
  if (!ms || ms <= 0) return "-";
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}小时${minutes}分`;
  }
  if (minutes > 0) {
    return `${minutes}分${seconds}秒`;
  }
  return `${seconds}秒`;
}

export function StatsPage() {
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<Team[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState<string>("");
  const [stats, setStats] = useState<TeamStats | null>(null);
  const [episodeStats, setEpisodeStats] = useState<EpisodeStat[]>([]);
  const [episodesLoading, setEpisodesLoading] = useState(false);

  useEffect(() => {
    const loadTeams = async () => {
      try {
        const res = await api.get("/teams");
        const data: Team[] = res.data.data;
        setTeams(data);
        if (data.length > 0) setSelectedTeamId(String(data[0].id));
      } catch (err) {
        toast.error(getErrorMessage(err, "加载团队列表失败"));
      }
    };
    loadTeams();
  }, [toast]);

  const loadStats = useCallback(
    async (teamId: string) => {
      setLoading(true);
      setStats(null);
      setEpisodeStats([]);
      try {
        const [statsRes, podcastsRes] = await Promise.all([
          api.get(`/stats/team/${teamId}`),
          api.get("/podcasts", { params: { teamId } }),
        ]);
        const statsData: TeamStats = statsRes.data.data;
        setStats(statsData);

        const podcastsData: Podcast[] = podcastsRes.data.data;
        if (podcastsData.length === 0) {
          return;
        }

        setEpisodesLoading(true);
        const episodeResults = await Promise.all(
          podcastsData.map((p) =>
            api
              .get("/episodes", { params: { podcastId: p.id } })
              .then((r) => r.data.data as Episode[])
              .catch(() => [] as Episode[])
          )
        );
        const allEpisodes = episodeResults
          .flat()
          .sort((a, b) => {
            const da = new Date(a.createdAt).getTime();
            const db = new Date(b.createdAt).getTime();
            return db - da;
          })
          .slice(0, 8);

        if (allEpisodes.length === 0) return;

        const episodeStatResults = await Promise.all(
          allEpisodes.map((ep) =>
            api
              .get(`/stats/episode/${ep.id}`)
              .then((r) => r.data.data as EpisodeStat)
              .catch(() => null)
          )
        );
        setEpisodeStats(
          episodeStatResults.filter(
            (s): s is EpisodeStat => s !== null
          )
        );
      } catch (err) {
        toast.error(getErrorMessage(err, "加载统计数据失败"));
      } finally {
        setLoading(false);
        setEpisodesLoading(false);
      }
    },
    [toast]
  );

  useEffect(() => {
    if (!selectedTeamId) {
      setStats(null);
      setEpisodeStats([]);
      return;
    }
    loadStats(selectedTeamId);
  }, [selectedTeamId, loadStats]);

  const maxMarkerCount =
    stats && stats.markersPerUser.length > 0
      ? Math.max(...stats.markersPerUser.map((u) => u.count), 1)
      : 1;

  const totalPlatformDistributions =
    stats?.platformCoverage.reduce((sum, p) => sum + p.count, 0) ?? 0;

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">数据统计</h1>
          <p className="mt-1 text-muted-foreground">
            查看团队制作数据、平台覆盖与成员贡献
          </p>
        </div>
        {teams.length > 1 && (
          <Select value={selectedTeamId} onValueChange={setSelectedTeamId}>
            <SelectTrigger className="w-[200px]">
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
      </div>

      {loading ? (
        <div className="flex h-[60vh] items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : !stats ? (
        <Card className="flex flex-col items-center justify-center py-20 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
            <BarChart3 className="h-8 w-8 text-muted-foreground" />
          </div>
          <CardTitle className="mt-6 text-xl">暂无统计数据</CardTitle>
          <CardDescription className="mt-2 max-w-sm">
            当前团队还没有可统计的制作数据，开始创建节目与单集后即可查看。
          </CardDescription>
        </Card>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  节目总数
                </CardTitle>
                <Radio className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats.totalPodcasts}</div>
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
                <div className="text-2xl font-bold">{stats.totalEpisodes}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  标记总数
                </CardTitle>
                <Tag className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats.totalMarkers}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  平均解决时长
                </CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stats.avgResolutionTimeHours != null
                    ? `${stats.avgResolutionTimeHours.toFixed(1)}h`
                    : "-"}
                </div>
                <p className="mt-1 text-xs text-muted-foreground">标记平均解决小时数</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  逾期任务
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div
                  className={`text-2xl font-bold ${
                    stats.overdueTasks > 0 ? "text-red-600" : ""
                  }`}
                >
                  {stats.overdueTasks}
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <Share2 className="h-4 w-4" />
                  平台覆盖
                </CardTitle>
                <CardDescription>
                  各分发平台的发布情况，共 {totalPlatformDistributions} 条分发记录
                </CardDescription>
              </CardHeader>
              <CardContent>
                {stats.platformCoverage.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-10 text-center">
                    <Share2 className="h-8 w-8 text-muted-foreground/50" />
                    <p className="mt-2 text-sm text-muted-foreground">
                      暂无平台分发数据
                    </p>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-left text-xs text-muted-foreground">
                          <th className="pb-2 font-medium">平台</th>
                          <th className="pb-2 text-center font-medium">分发数</th>
                          <th className="pb-2 text-center font-medium">已发布</th>
                          <th className="pb-2 text-right font-medium">覆盖率</th>
                        </tr>
                      </thead>
                      <tbody>
                        {stats.platformCoverage.map((p) => {
                          const coverage =
                            p.count > 0
                              ? (p.publishedCount / p.count) * 100
                              : 0;
                          return (
                            <tr key={p.platformName} className="border-b last:border-0">
                              <td className="py-2.5 font-medium">
                                {p.platformName}
                              </td>
                              <td className="py-2.5 text-center text-muted-foreground">
                                {p.count}
                              </td>
                              <td className="py-2.5 text-center">
                                <Badge
                                  variant="outline"
                                  className="border-green-200 bg-green-50 text-green-700"
                                >
                                  {p.publishedCount}
                                </Badge>
                              </td>
                              <td className="py-2.5 text-right">
                                <span className="font-mono font-medium">
                                  {coverage.toFixed(1)}%
                                </span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <Users className="h-4 w-4" />
                  成员标记数
                </CardTitle>
                <CardDescription>
                  团队成员创建的时间轴标记数量
                </CardDescription>
              </CardHeader>
              <CardContent>
                {stats.markersPerUser.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-10 text-center">
                    <Users className="h-8 w-8 text-muted-foreground/50" />
                    <p className="mt-2 text-sm text-muted-foreground">
                      暂无成员标记数据
                    </p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {stats.markersPerUser
                      .slice()
                      .sort((a, b) => b.count - a.count)
                      .map((u) => {
                        const widthPct = (u.count / maxMarkerCount) * 100;
                        return (
                          <div key={u.userId} className="space-y-1">
                            <div className="flex items-center justify-between text-sm">
                              <span className="truncate font-medium">
                                {u.name}
                              </span>
                              <span className="ml-2 shrink-0 font-mono text-muted-foreground">
                                {u.count}
                              </span>
                            </div>
                            <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                              <div
                                className="h-full rounded-full bg-primary transition-all"
                                style={{ width: `${widthPct}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <TrendingUp className="h-4 w-4" />
                近期单集统计
              </CardTitle>
              <CardDescription>
                最近创建的单集及其制作进度数据
              </CardDescription>
            </CardHeader>
            <CardContent>
              {episodesLoading ? (
                <div className="flex justify-center py-10">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : episodeStats.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <Mic className="h-8 w-8 text-muted-foreground/50" />
                  <p className="mt-2 text-sm text-muted-foreground">
                    暂无单集统计数据
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left text-xs text-muted-foreground">
                        <th className="pb-2 font-medium">单集标题</th>
                        <th className="pb-2 text-center font-medium">时长</th>
                        <th className="pb-2 text-center font-medium">标记</th>
                        <th className="pb-2 text-center font-medium">任务</th>
                        <th className="pb-2 text-center font-medium">已发布平台</th>
                        <th className="pb-2 text-right font-medium">制作天数</th>
                      </tr>
                    </thead>
                    <tbody>
                      {episodeStats.map((ep) => (
                        <tr
                          key={ep.episodeId}
                          className="border-b last:border-0 hover:bg-muted/40"
                        >
                          <td className="py-3 max-w-[260px]">
                            <span className="block truncate font-medium">
                              {ep.title}
                            </span>
                          </td>
                          <td className="py-3 text-center text-muted-foreground">
                            {formatDuration(ep.durationMs)}
                          </td>
                          <td className="py-3 text-center">
                            <Badge variant="secondary" className="font-mono">
                              {ep.totalMarkers}
                            </Badge>
                          </td>
                          <td className="py-3 text-center text-muted-foreground">
                            {ep.taskCount}
                          </td>
                          <td className="py-3 text-center">
                            <span className="font-mono">
                              {ep.publishedPlatformCount}/{ep.distributionCount}
                            </span>
                          </td>
                          <td className="py-3 text-right">
                            {ep.daysFromRecordToPublish != null ? (
                              <Badge
                                variant="outline"
                                className={
                                  ep.daysFromRecordToPublish > 14
                                    ? "border-orange-200 bg-orange-50 text-orange-700"
                                    : "border-gray-200"
                                }
                              >
                                {ep.daysFromRecordToPublish} 天
                              </Badge>
                            ) : (
                              <span className="text-muted-foreground">-</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
