import { useState, useEffect, useCallback, useRef } from "react";
import {
  Plus,
  Loader2,
  Trash2,
  Edit,
  Play,
  Pause,
  History,
  FileAudio,
  FileText,
  Link2,
  Tag,
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
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { formatDateTime } from "@/lib/utils";
import type { Team, Podcast, Episode } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

type AssetType = "AUDIO" | "TEXT";

interface AssetItem {
  id: string | number;
  teamId: string | number;
  name: string;
  type: AssetType;
  category?: string | null;
  fileUrl?: string | null;
  content?: string | null;
  usageCount?: number | null;
  createdByName?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

interface AssetUsage {
  id: string | number;
  assetId: string | number;
  episodeId: string | number;
  timeMs?: number | null;
  usedAt?: string;
  assetName?: string;
  episodeTitle?: string;
}

interface AssetFormState {
  name: string;
  type: AssetType;
  category: string;
  fileUrl: string;
  content: string;
}

const ASSET_TYPE_LABELS: Record<AssetType, string> = {
  AUDIO: "音频素材",
  TEXT: "文本素材",
};

const EMPTY_FORM: AssetFormState = {
  name: "",
  type: "AUDIO",
  category: "",
  fileUrl: "",
  content: "",
};

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

function formatTimeMs(ms?: number | null): string {
  if (ms == null) return "-";
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function AudioAssetCard({
  asset,
  onUsage,
  onHistory,
  onDelete,
}: {
  asset: AssetItem;
  onUsage: (asset: AssetItem) => void;
  onHistory: (asset: AssetItem) => void;
  onDelete: (asset: AssetItem) => void;
}) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (playing) {
      audio.pause();
    } else {
      void audio.play();
    }
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <FileAudio className="h-5 w-5 text-primary" />
            </div>
            <div className="min-w-0">
              <CardTitle className="truncate text-base">{asset.name}</CardTitle>
              {asset.category && (
                <CardDescription className="flex items-center gap-1 truncate">
                  <Tag className="h-3 w-3" />
                  {asset.category}
                </CardDescription>
              )}
            </div>
          </div>
          <Badge variant="secondary" className="shrink-0">
            {ASSET_TYPE_LABELS.AUDIO}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {asset.fileUrl ? (
          <>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={togglePlay}
                className="shrink-0"
              >
                {playing ? (
                  <Pause className="mr-1.5 h-3.5 w-3.5" />
                ) : (
                  <Play className="mr-1.5 h-3.5 w-3.5" />
                )}
                {playing ? "暂停" : "播放"}
              </Button>
              <a
                href={asset.fileUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
              >
                <Link2 className="h-3 w-3" />
                打开原文件
              </a>
            </div>
            <audio
              ref={audioRef}
              src={asset.fileUrl}
              controls
              className="w-full"
              onPlay={() => setPlaying(true)}
              onPause={() => setPlaying(false)}
              onEnded={() => setPlaying(false)}
            />
          </>
        ) : (
          <p className="text-xs text-muted-foreground">暂无音频文件</p>
        )}
        <div className="flex items-center justify-between border-t pt-3">
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <History className="h-3.5 w-3.5" />
            使用 {asset.usageCount ?? 0} 次
          </span>
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onHistory(asset)}
            >
              <History className="mr-1.5 h-3.5 w-3.5" />
              使用记录
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onUsage(asset)}
            >
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              记录使用
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-destructive hover:text-destructive"
              onClick={() => onDelete(asset)}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function TextAssetCard({
  asset,
  onEdit,
  onUsage,
  onHistory,
  onDelete,
}: {
  asset: AssetItem;
  onEdit: (asset: AssetItem) => void;
  onUsage: (asset: AssetItem) => void;
  onHistory: (asset: AssetItem) => void;
  onDelete: (asset: AssetItem) => void;
}) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <FileText className="h-5 w-5 text-primary" />
            </div>
            <div className="min-w-0">
              <CardTitle className="truncate text-base">{asset.name}</CardTitle>
              {asset.category && (
                <CardDescription className="flex items-center gap-1 truncate">
                  <Tag className="h-3 w-3" />
                  {asset.category}
                </CardDescription>
              )}
            </div>
          </div>
          <Badge variant="secondary" className="shrink-0">
            {ASSET_TYPE_LABELS.TEXT}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="line-clamp-3 whitespace-pre-wrap rounded-md bg-muted/50 p-3 text-sm text-muted-foreground">
          {asset.content || "暂无内容"}
        </p>
        <div className="flex items-center justify-between border-t pt-3">
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <History className="h-3.5 w-3.5" />
            使用 {asset.usageCount ?? 0} 次
          </span>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="sm" onClick={() => onEdit(asset)}>
              <Edit className="mr-1.5 h-3.5 w-3.5" />
              编辑
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onHistory(asset)}
            >
              <History className="mr-1.5 h-3.5 w-3.5" />
              使用记录
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onUsage(asset)}
            >
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              记录使用
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-destructive hover:text-destructive"
              onClick={() => onDelete(asset)}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function AssetsPage() {
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<Team[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState<string>("");
  const [assets, setAssets] = useState<AssetItem[]>([]);
  const [episodes, setEpisodes] = useState<Episode[]>([]);

  const [activeTab, setActiveTab] = useState<AssetType>("AUDIO");

  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<"create" | "edit">("create");
  const [editingId, setEditingId] = useState<string | number | null>(null);
  const [form, setForm] = useState<AssetFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  const [usageOpen, setUsageOpen] = useState(false);
  const [usageAsset, setUsageAsset] = useState<AssetItem | null>(null);
  const [usageEpisodeId, setUsageEpisodeId] = useState<string>("");
  const [usageTimeSec, setUsageTimeSec] = useState<string>("");
  const [usageSubmitting, setUsageSubmitting] = useState(false);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyAsset, setHistoryAsset] = useState<AssetItem | null>(null);
  const [usages, setUsages] = useState<AssetUsage[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

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

  const loadTeamData = useCallback(
    async (teamId: string) => {
      setLoading(true);
      try {
        const [assetsRes, podcastsRes] = await Promise.all([
          api.get(`/assets/team/${teamId}`),
          api.get("/podcasts", { params: { teamId } }),
        ]);
        const assetsData: AssetItem[] = assetsRes.data.data;
        const podcastsData: Podcast[] = podcastsRes.data.data;
        setAssets(assetsData);

        if (podcastsData.length === 0) {
          setEpisodes([]);
          return;
        }
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
          });
        setEpisodes(allEpisodes);
      } catch (err) {
        toast.error(getErrorMessage(err, "加载素材库失败"));
      } finally {
        setLoading(false);
      }
    },
    [toast]
  );

  useEffect(() => {
    if (!selectedTeamId) {
      setAssets([]);
      setEpisodes([]);
      return;
    }
    loadTeamData(selectedTeamId);
  }, [selectedTeamId, loadTeamData]);

  const openCreate = () => {
    setFormMode("create");
    setEditingId(null);
    setForm({ ...EMPTY_FORM, type: activeTab });
    setFormOpen(true);
  };

  const openEdit = (asset: AssetItem) => {
    setFormMode("edit");
    setEditingId(asset.id);
    setForm({
      name: asset.name,
      type: asset.type,
      category: asset.category ?? "",
      fileUrl: asset.fileUrl ?? "",
      content: asset.content ?? "",
    });
    setFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast.error("请输入素材名称");
      return;
    }
    if (form.type === "AUDIO" && !form.fileUrl.trim()) {
      toast.error("请输入音频文件地址");
      return;
    }
    if (form.type === "TEXT" && !form.content.trim()) {
      toast.error("请输入文本内容");
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        name: form.name.trim(),
        type: form.type,
        category: form.category.trim() || null,
        fileUrl: form.type === "AUDIO" ? form.fileUrl.trim() : null,
        content: form.type === "TEXT" ? form.content : null,
      };
      if (formMode === "edit" && editingId != null) {
        await api.put(`/assets/${editingId}`, {
          name: payload.name,
          category: payload.category,
          fileUrl: payload.fileUrl,
          content: payload.content,
        });
        toast.success("素材已更新");
      } else {
        if (!selectedTeamId) {
          toast.error("请先选择团队");
          return;
        }
        await api.post(`/assets/team/${selectedTeamId}`, payload);
        toast.success("素材已创建");
      }
      setFormOpen(false);
      setForm(EMPTY_FORM);
      setEditingId(null);
      if (selectedTeamId) loadTeamData(selectedTeamId);
    } catch (err) {
      toast.error(getErrorMessage(err, "保存素材失败"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (asset: AssetItem) => {
    if (!window.confirm(`确定要删除素材"${asset.name}"吗？`)) return;
    try {
      await api.delete(`/assets/${asset.id}`);
      toast.success("素材已删除");
      setAssets((prev) => prev.filter((a) => a.id !== asset.id));
    } catch (err) {
      toast.error(getErrorMessage(err, "删除素材失败"));
    }
  };

  const openUsage = (asset: AssetItem) => {
    setUsageAsset(asset);
    setUsageEpisodeId(episodes[0] ? String(episodes[0].id) : "");
    setUsageTimeSec("");
    setUsageOpen(true);
  };

  const handleSubmitUsage = async () => {
    if (!usageAsset) return;
    if (!usageEpisodeId) {
      toast.error("请选择单集");
      return;
    }
    setUsageSubmitting(true);
    try {
      const timeMs = usageTimeSec
        ? Math.max(0, Math.round(Number(usageTimeSec) * 1000))
        : null;
      await api.post(`/assets/${usageAsset.id}/usage`, {
        episodeId: Number(usageEpisodeId),
        timeMs,
      });
      toast.success("使用记录已保存");
      setUsageOpen(false);
      if (selectedTeamId) loadTeamData(selectedTeamId);
    } catch (err) {
      toast.error(getErrorMessage(err, "记录使用失败"));
    } finally {
      setUsageSubmitting(false);
    }
  };

  const openHistory = async (asset: AssetItem) => {
    setHistoryAsset(asset);
    setHistoryOpen(true);
    setHistoryLoading(true);
    setUsages([]);
    try {
      const res = await api.get(`/assets/${asset.id}/usages`);
      setUsages(res.data.data);
    } catch (err) {
      toast.error(getErrorMessage(err, "加载使用记录失败"));
    } finally {
      setHistoryLoading(false);
    }
  };

  const audioAssets = assets.filter((a) => a.type === "AUDIO");
  const textAssets = assets.filter((a) => a.type === "TEXT");

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">素材库</h1>
          <p className="mt-1 text-muted-foreground">
            管理团队的音频与文本素材，记录素材使用情况
          </p>
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
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            上传素材/新建文本
          </Button>
        </div>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as AssetType)}
      >
        <TabsList>
          <TabsTrigger value="AUDIO" className="flex items-center gap-1.5">
            <FileAudio className="h-4 w-4" />
            音频素材
            <Badge variant="secondary" className="ml-1">
              {audioAssets.length}
            </Badge>
          </TabsTrigger>
          <TabsTrigger value="TEXT" className="flex items-center gap-1.5">
            <FileText className="h-4 w-4" />
            文本素材
            <Badge variant="secondary" className="ml-1">
              {textAssets.length}
            </Badge>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="AUDIO" className="mt-4">
          {loading ? (
            <div className="flex h-[40vh] items-center justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : audioAssets.length === 0 ? (
            <Card className="flex flex-col items-center justify-center py-20 text-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
                <FileAudio className="h-8 w-8 text-muted-foreground" />
              </div>
              <CardTitle className="mt-6 text-xl">暂无音频素材</CardTitle>
              <CardDescription className="mt-2 max-w-sm">
                点击右上角按钮上传音频素材，填写名称、分类与文件地址即可。
              </CardDescription>
              <Button className="mt-6" onClick={openCreate}>
                <Plus className="mr-2 h-4 w-4" />
                上传音频素材
              </Button>
            </Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {audioAssets.map((asset) => (
                <AudioAssetCard
                  key={asset.id}
                  asset={asset}
                  onUsage={openUsage}
                  onHistory={openHistory}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="TEXT" className="mt-4">
          {loading ? (
            <div className="flex h-[40vh] items-center justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : textAssets.length === 0 ? (
            <Card className="flex flex-col items-center justify-center py-20 text-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
                <FileText className="h-8 w-8 text-muted-foreground" />
              </div>
              <CardTitle className="mt-6 text-xl">暂无文本素材</CardTitle>
              <CardDescription className="mt-2 max-w-sm">
                点击右上角按钮新建文本素材，可用于脚本、备注或文案内容。
              </CardDescription>
              <Button className="mt-6" onClick={openCreate}>
                <Plus className="mr-2 h-4 w-4" />
                新建文本素材
              </Button>
            </Card>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {textAssets.map((asset) => (
                <TextAssetCard
                  key={asset.id}
                  asset={asset}
                  onEdit={openEdit}
                  onUsage={openUsage}
                  onHistory={openHistory}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {formMode === "edit" ? "编辑素材" : "新建素材"}
            </DialogTitle>
            <DialogDescription>
              {formMode === "edit"
                ? "修改素材信息，音频素材可更新文件地址，文本素材可编辑内容。"
                : "创建音频或文本素材，素材归属于当前团队。"}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit}>
            <div className="space-y-4 py-2">
              <div className="space-y-2">
                <Label htmlFor="asset-name">
                  素材名称 <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="asset-name"
                  placeholder="输入素材名称"
                  value={form.name}
                  onChange={(e) =>
                    setForm((prev) => ({ ...prev, name: e.target.value }))
                  }
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="asset-type">素材类型</Label>
                  <Select
                    value={form.type}
                    onValueChange={(v) =>
                      setForm((prev) => ({ ...prev, type: v as AssetType }))
                    }
                    disabled={formMode === "edit"}
                  >
                    <SelectTrigger id="asset-type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="AUDIO">音频素材</SelectItem>
                      <SelectItem value="TEXT">文本素材</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="asset-category">分类</Label>
                  <Input
                    id="asset-category"
                    placeholder="如：片头、采访"
                    value={form.category}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, category: e.target.value }))
                    }
                  />
                </div>
              </div>
              {form.type === "AUDIO" ? (
                <div className="space-y-2">
                  <Label htmlFor="asset-fileurl">
                    音频文件地址 <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="asset-fileurl"
                    placeholder="https://example.com/audio.mp3"
                    value={form.fileUrl}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, fileUrl: e.target.value }))
                    }
                  />
                  <p className="text-xs text-muted-foreground">
                    请输入可访问的音频文件 URL，文件上传功能将在后续版本提供。
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  <Label htmlFor="asset-content">
                    文本内容 <span className="text-destructive">*</span>
                  </Label>
                  <Textarea
                    id="asset-content"
                    rows={6}
                    placeholder="输入文本素材内容"
                    value={form.content}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, content: e.target.value }))
                    }
                  />
                </div>
              )}
            </div>
            <DialogFooter className="mt-4 gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setFormOpen(false)}
                disabled={submitting}
              >
                取消
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                {formMode === "edit" ? "保存" : "创建"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={usageOpen} onOpenChange={setUsageOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>记录素材使用</DialogTitle>
            <DialogDescription>
              {usageAsset?.name} · 选择使用该素材的单集与时间位置
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="usage-episode">单集</Label>
              <Select value={usageEpisodeId} onValueChange={setUsageEpisodeId}>
                <SelectTrigger id="usage-episode">
                  <SelectValue placeholder="选择单集" />
                </SelectTrigger>
                <SelectContent>
                  {episodes.length === 0 ? (
                    <SelectItem value="__none__" disabled>
                      暂无可选单集
                    </SelectItem>
                  ) : (
                    episodes.map((ep) => (
                      <SelectItem key={ep.id} value={String(ep.id)}>
                        {ep.title}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="usage-time">时间位置（秒，可选）</Label>
              <Input
                id="usage-time"
                type="number"
                min="0"
                step="0.1"
                placeholder="如：12.5"
                value={usageTimeSec}
                onChange={(e) => setUsageTimeSec(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter className="gap-2">
            <Button
              variant="outline"
              onClick={() => setUsageOpen(false)}
              disabled={usageSubmitting}
            >
              取消
            </Button>
            <Button
              onClick={handleSubmitUsage}
              disabled={usageSubmitting || episodes.length === 0}
            >
              {usageSubmitting && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              保存记录
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={historyOpen} onOpenChange={setHistoryOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>使用记录</DialogTitle>
            <DialogDescription>
              {historyAsset?.name} · 共 {usages.length} 条使用记录
            </DialogDescription>
          </DialogHeader>
          {historyLoading ? (
            <div className="flex justify-center py-10">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : usages.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-center">
              <History className="h-8 w-8 text-muted-foreground/50" />
              <p className="mt-2 text-sm text-muted-foreground">
                暂无使用记录
              </p>
            </div>
          ) : (
            <ul className="divide-y">
              {usages.map((usage) => (
                <li
                  key={usage.id}
                  className="flex items-center justify-between gap-3 py-3"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">
                      {usage.episodeTitle || `单集 #${usage.episodeId}`}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {usage.usedAt
                        ? formatDateTime(usage.usedAt)
                        : "时间未知"}
                    </p>
                  </div>
                  <Badge variant="outline" className="shrink-0 font-mono">
                    {formatTimeMs(usage.timeMs)}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setHistoryOpen(false)}>
              关闭
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
