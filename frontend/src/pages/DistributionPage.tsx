import { useState, useEffect, useMemo, useCallback } from "react";
import {
  Loader2,
  Radio,
  Plus,
  Trash2,
  Save,
  Copy,
  ExternalLink,
  Rss,
  Share2,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Send,
  Eye,
} from "lucide-react";
import { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
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
  DISTRIBUTION_STATUS_LABELS,
} from "@/lib/utils";
import type {
  Distribution,
  Platform,
  Team,
  Podcast,
  Episode,
} from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

type DistributionStatus =
  | "NOT_STARTED"
  | "SUBMITTED"
  | "IN_REVIEW"
  | "PUBLISHED"
  | "REJECTED";

interface DistributionResponse extends Distribution {
  platformName?: string;
  platformDisplayName?: string;
}

const DISTRIBUTION_STATUSES: DistributionStatus[] = [
  "NOT_STARTED",
  "SUBMITTED",
  "IN_REVIEW",
  "PUBLISHED",
  "REJECTED",
];

const STATUS_BADGE_CLASS: Record<DistributionStatus, string> = {
  NOT_STARTED: "bg-gray-100 text-gray-700 border-gray-200",
  SUBMITTED: "bg-blue-100 text-blue-700 border-blue-200",
  IN_REVIEW: "bg-yellow-100 text-yellow-700 border-yellow-200",
  PUBLISHED: "bg-green-100 text-green-700 border-green-200",
  REJECTED: "bg-red-100 text-red-700 border-red-200",
};

const STATUS_ICON: Record<DistributionStatus, typeof Clock> = {
  NOT_STARTED: Clock,
  SUBMITTED: Send,
  IN_REVIEW: Eye,
  PUBLISHED: CheckCircle2,
  REJECTED: AlertTriangle,
};

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

function getRssUrl(podcastId: string): string {
  return `/api/distributions/podcast/${podcastId}/rss`;
}

export function DistributionPage() {
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<Team[]>([]);
  const [podcasts, setPodcasts] = useState<Podcast[]>([]);
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [platforms, setPlatforms] = useState<Platform[]>([]);

  const [selectedEpisodeId, setSelectedEpisodeId] = useState<string>("");
  const [distributions, setDistributions] = useState<DistributionResponse[]>(
    []
  );
  const [distributionsLoading, setDistributionsLoading] = useState(false);

  const [dataDrafts, setDataDrafts] = useState<Record<string, string>>({});
  const [rejectionDrafts, setRejectionDrafts] = useState<
    Record<string, string>
  >({});
  const [savingDataId, setSavingDataId] = useState<string | null>(null);
  const [statusUpdatingId, setStatusUpdatingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const [addOpen, setAddOpen] = useState(false);
  const [newPlatformId, setNewPlatformId] = useState<string>("");
  const [newPlatformData, setNewPlatformData] = useState<string>("");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [teamsRes, platformsRes] = await Promise.all([
          api.get("/teams"),
          api.get("/distributions/platforms"),
        ]);
        const teamsData: Team[] = teamsRes.data.data;
        const platformsData: Platform[] = platformsRes.data.data;
        setTeams(teamsData);
        setPlatforms(platformsData);

        if (teamsData.length === 0) {
          setLoading(false);
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
        setPodcasts(allPodcasts);

        if (allPodcasts.length === 0) {
          setLoading(false);
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
        const allEpisodes = episodeResults
          .flat()
          .sort((a, b) => {
            const da = new Date(a.createdAt).getTime();
            const db = new Date(b.createdAt).getTime();
            return db - da;
          });
        setEpisodes(allEpisodes);

        if (allEpisodes.length > 0) {
          setSelectedEpisodeId(String(allEpisodes[0].id));
        }
      } catch (err) {
        toast.error(getErrorMessage(err, "加载分发数据失败"));
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [toast]);

  const loadDistributions = useCallback(
    async (episodeId: string) => {
      if (!episodeId) return;
      setDistributionsLoading(true);
      try {
        const res = await api.get(`/distributions/episode/${episodeId}`);
        const data: DistributionResponse[] = res.data.data;
        setDistributions(data);
        const nextDataDrafts: Record<string, string> = {};
        const nextRejectionDrafts: Record<string, string> = {};
        for (const d of data) {
          nextDataDrafts[String(d.id)] = d.platformDataJson ?? "";
          nextRejectionDrafts[String(d.id)] = d.rejectionReason ?? "";
        }
        setDataDrafts(nextDataDrafts);
        setRejectionDrafts(nextRejectionDrafts);
      } catch (err) {
        toast.error(getErrorMessage(err, "加载分发记录失败"));
      } finally {
        setDistributionsLoading(false);
      }
    },
    [toast]
  );

  useEffect(() => {
    if (selectedEpisodeId) {
      loadDistributions(selectedEpisodeId);
    } else {
      setDistributions([]);
    }
  }, [selectedEpisodeId, loadDistributions]);

  const selectedEpisode = useMemo(
    () =>
      episodes.find((e) => String(e.id) === selectedEpisodeId) ?? null,
    [episodes, selectedEpisodeId]
  );

  const availablePlatforms = useMemo(() => {
    const addedIds = new Set(
      distributions.map((d) => String(d.platformId))
    );
    return platforms.filter(
      (p) => p.isActive !== false && !addedIds.has(String(p.id))
    );
  }, [platforms, distributions]);

  const statusSummary = useMemo(() => {
    const summary: Record<DistributionStatus, number> = {
      NOT_STARTED: 0,
      SUBMITTED: 0,
      IN_REVIEW: 0,
      PUBLISHED: 0,
      REJECTED: 0,
    };
    for (const d of distributions) {
      const key = d.status as DistributionStatus;
      if (summary[key] != null) summary[key] += 1;
    }
    return summary;
  }, [distributions]);

  const handleStatusChange = async (
    distributionId: string,
    status: string
  ) => {
    setStatusUpdatingId(distributionId);
    try {
      const rejectionReason =
        status === "REJECTED"
          ? rejectionDrafts[distributionId]?.trim()
            ? rejectionDrafts[distributionId].trim()
            : null
          : null;
      const res = await api.patch(`/distributions/${distributionId}/status`, {
        status,
        rejectionReason,
      });
      const updated: DistributionResponse = res.data.data;
      setDistributions((prev) =>
        prev.map((d) =>
          String(d.id) === distributionId ? { ...d, ...updated } : d
        )
      );
      if (updated.rejectionReason !== undefined) {
        setRejectionDrafts((prev) => ({
          ...prev,
          [distributionId]: updated.rejectionReason ?? "",
        }));
      }
      toast.success("分发状态已更新");
    } catch (err) {
      toast.error(getErrorMessage(err, "更新分发状态失败"));
    } finally {
      setStatusUpdatingId(null);
    }
  };

  const handleSaveData = async (distributionId: string) => {
    setSavingDataId(distributionId);
    try {
      await api.put(`/distributions/${distributionId}`, {
        platformDataJson: dataDrafts[distributionId] ?? "",
      });
      toast.success("平台数据已保存");
    } catch (err) {
      toast.error(getErrorMessage(err, "保存平台数据失败"));
    } finally {
      setSavingDataId(null);
    }
  };

  const handleDelete = async (distributionId: string) => {
    if (!window.confirm("确定要删除此分发记录吗？")) return;
    setDeletingId(distributionId);
    try {
      await api.delete(`/distributions/${distributionId}`);
      setDistributions((prev) =>
        prev.filter((d) => String(d.id) !== distributionId)
      );
      toast.success("分发记录已删除");
    } catch (err) {
      toast.error(getErrorMessage(err, "删除分发记录失败"));
    } finally {
      setDeletingId(null);
    }
  };

  const handleCreate = async () => {
    if (!selectedEpisodeId || !newPlatformId) {
      toast.error("请选择平台");
      return;
    }
    setCreating(true);
    try {
      await api.post(`/distributions/episode/${selectedEpisodeId}`, {
        platformId: Number(newPlatformId),
        platformDataJson: newPlatformData || null,
      });
      toast.success("平台已添加");
      setAddOpen(false);
      setNewPlatformId("");
      setNewPlatformData("");
      loadDistributions(selectedEpisodeId);
    } catch (err) {
      toast.error(getErrorMessage(err, "添加平台失败"));
    } finally {
      setCreating(false);
    }
  };

  const handleCopyRss = async (podcastId: string) => {
    const url = `${window.location.origin}${getRssUrl(podcastId)}`;
    try {
      await navigator.clipboard.writeText(url);
      toast.success("RSS链接已复制");
    } catch {
      toast.error("复制失败，请手动复制");
    }
  };

  if (loading) {
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
          <h1 className="text-3xl font-bold tracking-tight">分发管理</h1>
          <p className="text-muted-foreground mt-1">
            管理单集在各平台的分发状态与平台数据
          </p>
        </div>
        <Button
          onClick={() => setAddOpen(true)}
          disabled={!selectedEpisodeId || availablePlatforms.length === 0}
        >
          <Plus className="mr-2 h-4 w-4" />
          添加平台
        </Button>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <Rss className="h-4 w-4" />
            播客 RSS 订阅源
          </CardTitle>
          <CardDescription>
            复制或在新标签页打开各节目的 RSS 订阅链接
          </CardDescription>
        </CardHeader>
        <CardContent>
          {podcasts.length === 0 ? (
            <p className="text-sm text-muted-foreground">暂无可用节目</p>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {podcasts.map((podcast) => (
                <div
                  key={podcast.id}
                  className="flex items-center justify-between gap-3 rounded-lg border p-3"
                >
                  <div className="flex min-w-0 items-center gap-2">
                    <Radio className="h-4 w-4 shrink-0 text-primary" />
                    <span className="truncate text-sm font-medium">
                      {podcast.name}
                    </span>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleCopyRss(String(podcast.id))}
                    >
                      <Copy className="mr-1.5 h-3.5 w-3.5" />
                      复制链接
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() =>
                        window.open(
                          getRssUrl(String(podcast.id)),
                          "_blank",
                          "noopener,noreferrer"
                        )
                      }
                    >
                      <ExternalLink className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <Share2 className="h-4 w-4" />
            选择单集
          </CardTitle>
          <CardDescription>
            选择一个单集查看并管理其平台分发
          </CardDescription>
        </CardHeader>
        <CardContent>
          {episodes.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-center">
              <Radio className="h-8 w-8 text-muted-foreground/50" />
              <p className="mt-2 text-sm text-muted-foreground">
                暂无可分发的单集
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
              <div className="flex-1 space-y-2">
                <Label htmlFor="episode-select">单集</Label>
                <Select
                  value={selectedEpisodeId}
                  onValueChange={setSelectedEpisodeId}
                >
                  <SelectTrigger id="episode-select">
                    <SelectValue placeholder="选择单集" />
                  </SelectTrigger>
                  <SelectContent>
                    {episodes.map((episode) => {
                      const podcast = podcasts.find(
                        (p) => String(p.id) === String(episode.podcastId)
                      );
                      return (
                        <SelectItem
                          key={episode.id}
                          value={String(episode.id)}
                        >
                          {podcast ? `${podcast.name} · ` : ""}
                          {episode.title}
                          {episode.number != null
                            ? `（第${episode.number}期）`
                            : ""}
                        </SelectItem>
                      );
                    })}
                  </SelectContent>
                </Select>
              </div>
              {distributions.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {DISTRIBUTION_STATUSES.map((s) => (
                    <Badge
                      key={s}
                      variant="outline"
                      className={cn("border", STATUS_BADGE_CLASS[s])}
                    >
                      {DISTRIBUTION_STATUS_LABELS[s] ?? s}:{" "}
                      {statusSummary[s]}
                    </Badge>
                  ))}
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {selectedEpisode && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <span className="font-medium text-foreground">
              {selectedEpisode.title}
            </span>
            {selectedEpisode.number != null && (
              <span>· 第{selectedEpisode.number}期</span>
            )}
          </div>

          {distributionsLoading ? (
            <div className="flex h-[30vh] items-center justify-center">
              <Loader2 className="h-7 w-7 animate-spin text-muted-foreground" />
            </div>
          ) : distributions.length === 0 ? (
            <Card className="flex flex-col items-center justify-center py-16 text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
                <Share2 className="h-7 w-7 text-muted-foreground" />
              </div>
              <CardTitle className="mt-4 text-lg">暂未分发到任何平台</CardTitle>
              <CardDescription className="mt-1 max-w-sm">
                点击右上角"添加平台"按钮，为此单集添加分发平台。
              </CardDescription>
              <Button
                className="mt-5"
                onClick={() => setAddOpen(true)}
                disabled={availablePlatforms.length === 0}
              >
                <Plus className="mr-2 h-4 w-4" />
                添加平台
              </Button>
            </Card>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {distributions.map((distribution) => {
                const distId = String(distribution.id);
                const StatusIcon =
                  STATUS_ICON[distribution.status as DistributionStatus] ??
                  Clock;
                const isStatusUpdating = statusUpdatingId === distId;
                const isSavingData = savingDataId === distId;
                const isDeleting = deletingId === distId;
                const rejected = distribution.status === "REJECTED";
                return (
                  <Card key={distribution.id}>
                    <CardHeader className="pb-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex min-w-0 items-center gap-3">
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                            <Radio className="h-5 w-5 text-primary" />
                          </div>
                          <div className="min-w-0">
                            <CardTitle className="text-base truncate">
                              {distribution.platformDisplayName ??
                                distribution.platformName ??
                                "未知平台"}
                            </CardTitle>
                            <CardDescription className="truncate">
                              {distribution.platformName}
                            </CardDescription>
                          </div>
                        </div>
                        <Badge
                          variant="outline"
                          className={cn(
                            "shrink-0 border",
                            STATUS_BADGE_CLASS[
                              distribution.status as DistributionStatus
                            ]
                          )}
                        >
                          <StatusIcon className="mr-1 h-3 w-3" />
                          {DISTRIBUTION_STATUS_LABELS[
                            distribution.status as DistributionStatus
                          ] ?? distribution.status}
                        </Badge>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="grid grid-cols-2 gap-3 text-xs text-muted-foreground">
                        <div>
                          <span className="block text-muted-foreground/80">
                            提交时间
                          </span>
                          <span className="font-medium text-foreground">
                            {distribution.submittedAt
                              ? formatDateTime(distribution.submittedAt)
                              : "-"}
                          </span>
                        </div>
                        <div>
                          <span className="block text-muted-foreground/80">
                            发布时间
                          </span>
                          <span className="font-medium text-foreground">
                            {distribution.publishedAt
                              ? formatDateTime(distribution.publishedAt)
                              : "-"}
                          </span>
                        </div>
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor={`status-${distId}`}>分发状态</Label>
                        <Select
                          value={distribution.status as string}
                          onValueChange={(value) =>
                            handleStatusChange(distId, value)
                          }
                          disabled={isStatusUpdating}
                        >
                          <SelectTrigger id={`status-${distId}`} className="h-9">
                            {isStatusUpdating ? (
                              <span className="flex items-center gap-2 text-muted-foreground">
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                更新中...
                              </span>
                            ) : (
                              <SelectValue />
                            )}
                          </SelectTrigger>
                          <SelectContent>
                            {DISTRIBUTION_STATUSES.map((s) => (
                              <SelectItem key={s} value={s}>
                                {DISTRIBUTION_STATUS_LABELS[s] ?? s}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>

                      {rejected && (
                        <div className="space-y-2">
                          <Label
                            htmlFor={`rejection-${distId}`}
                            className="flex items-center gap-1.5 text-red-600"
                          >
                            <AlertTriangle className="h-3.5 w-3.5" />
                            驳回原因
                          </Label>
                          <Textarea
                            id={`rejection-${distId}`}
                            rows={2}
                            placeholder="请输入驳回原因"
                            value={rejectionDrafts[distId] ?? ""}
                            onChange={(e) =>
                              setRejectionDrafts((prev) => ({
                                ...prev,
                                [distId]: e.target.value,
                              }))
                            }
                          />
                        </div>
                      )}

                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <Label htmlFor={`data-${distId}`}>
                            平台数据 (JSON)
                          </Label>
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            className="h-7"
                            onClick={() => handleSaveData(distId)}
                            disabled={isSavingData}
                          >
                            {isSavingData ? (
                              <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <Save className="mr-1.5 h-3.5 w-3.5" />
                            )}
                            保存
                          </Button>
                        </div>
                        <Textarea
                          id={`data-${distId}`}
                          rows={4}
                          className="font-mono text-xs"
                          placeholder='{"key": "value"}'
                          value={dataDrafts[distId] ?? ""}
                          onChange={(e) =>
                            setDataDrafts((prev) => ({
                              ...prev,
                              [distId]: e.target.value,
                            }))
                          }
                        />
                      </div>

                      {distribution.createdAt && (
                        <p className="text-xs text-muted-foreground">
                          创建于 {formatDate(distribution.createdAt)}
                        </p>
                      )}

                      <div className="flex justify-end border-t pt-3">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="text-red-600 hover:text-red-600 hover:bg-red-50"
                          onClick={() => handleDelete(distId)}
                          disabled={isDeleting}
                        >
                          {isDeleting ? (
                            <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Trash2 className="mr-1.5 h-3.5 w-3.5" />
                          )}
                          删除
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          )}
        </div>
      )}

      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>添加分发平台</DialogTitle>
            <DialogDescription>
              为当前单集添加一个尚未分发的平台
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="new-platform">
                平台 <span className="text-destructive">*</span>
              </Label>
              <Select value={newPlatformId} onValueChange={setNewPlatformId}>
                <SelectTrigger id="new-platform">
                  <SelectValue placeholder="选择平台" />
                </SelectTrigger>
                <SelectContent>
                  {availablePlatforms.length === 0 ? (
                    <SelectItem value="__none__" disabled>
                      所有平台均已添加
                    </SelectItem>
                  ) : (
                    availablePlatforms.map((p) => (
                      <SelectItem key={p.id} value={String(p.id)}>
                        {p.displayName ?? p.name}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="new-platform-data">平台数据 (JSON)</Label>
              <Textarea
                id="new-platform-data"
                rows={5}
                className="font-mono text-xs"
                placeholder='{"key": "value"}'
                value={newPlatformData}
                onChange={(e) => setNewPlatformData(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter className="gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setAddOpen(false)}
              disabled={creating}
            >
              取消
            </Button>
            <Button
              type="button"
              onClick={handleCreate}
              disabled={creating || !newPlatformId}
            >
              {creating && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              添加
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
