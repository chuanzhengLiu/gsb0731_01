import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api";
import type { Asset, AssetType, AssetUsage } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

/**
 * Asset library (README §4.5): categorised audio/text assets with preview and
 * per-asset usage tracking. Managing the library requires producer-level.
 */
export function AssetsPage() {
  const { user } = useAuth();
  const [assets, setAssets] = useState<Asset[]>([]);
  const [filter, setFilter] = useState<AssetType | "">("");
  const [error, setError] = useState<string | null>(null);
  const [usageOpen, setUsageOpen] = useState<number | null>(null);
  const [usages, setUsages] = useState<AssetUsage[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);

  // Text asset form
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [text, setText] = useState("");
  // Audio upload form
  const [audioName, setAudioName] = useState("");
  const [audioCategory, setAudioCategory] = useState("");
  const [uploading, setUploading] = useState(false);

  const canManage = user?.role === "ADMIN" || user?.role === "PRODUCER";

  const load = useCallback(async () => {
    try {
      const qs = filter ? `?type=${filter}` : "";
      setAssets(await api<Asset[]>(`/assets${qs}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }, [filter]);

  useEffect(() => {
    load();
  }, [load]);

  async function createText(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/assets/text", { method: "POST", body: { name, category: category || null, textContent: text } });
      setName(""); setCategory(""); setText("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建失败");
    }
  }

  async function uploadAudio(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      if (audioName) fd.append("name", audioName);
      if (audioCategory) fd.append("category", audioCategory);
      await api("/assets/audio", { method: "POST", body: fd, isForm: true });
      setAudioName(""); setAudioCategory("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "上传失败");
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function remove(id: number) {
    setError(null);
    try {
      await api(`/assets/${id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    }
  }

  async function toggleUsage(id: number) {
    if (usageOpen === id) { setUsageOpen(null); return; }
    try {
      setUsages(await api<AssetUsage[]>(`/assets/${id}/usages`));
      setUsageOpen(id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载使用记录失败");
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/" className="text-sm text-muted-foreground hover:underline">← 返回节目列表</Link>
        <h1 className="text-2xl font-bold">素材库</h1>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {canManage && (
        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardHeader><CardTitle>新增文本素材</CardTitle></CardHeader>
            <CardContent>
              <form onSubmit={createText} className="space-y-3">
                <div className="space-y-1">
                  <Label>名称</Label>
                  <Input value={name} onChange={(e) => setName(e.target.value)} required />
                </div>
                <div className="space-y-1">
                  <Label>分类</Label>
                  <Input value={category} onChange={(e) => setCategory(e.target.value)}
                    placeholder="如 口播文案 / slogan" />
                </div>
                <div className="space-y-1">
                  <Label>内容</Label>
                  <Input value={text} onChange={(e) => setText(e.target.value)} required />
                </div>
                <Button type="submit">添加文本素材</Button>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>上传音频素材</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-1">
                <Label>名称（可选）</Label>
                <Input value={audioName} onChange={(e) => setAudioName(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label>分类</Label>
                <Input value={audioCategory} onChange={(e) => setAudioCategory(e.target.value)}
                  placeholder="如 开场音乐 / 过渡音效 / 广告片花" />
              </div>
              <label className="inline-flex cursor-pointer items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground">
                {uploading ? "上传中..." : "选择音频上传"}
                <input ref={fileRef} type="file" accept=".wav,.mp3,.m4a" className="hidden"
                  onChange={uploadAudio} disabled={uploading} />
              </label>
            </CardContent>
          </Card>
        </div>
      )}

      <div className="flex items-center gap-3">
        <select value={filter} onChange={(e) => setFilter(e.target.value as AssetType | "")}
          className="h-9 rounded-md border border-input bg-background px-2 text-sm">
          <option value="">全部类型</option>
          <option value="AUDIO">音频素材</option>
          <option value="TEXT">文本素材</option>
        </select>
      </div>

      <div className="space-y-2">
        {assets.length === 0 && <p className="text-sm text-muted-foreground">暂无素材</p>}
        {assets.map((a) => (
          <Card key={a.id}>
            <CardContent className="space-y-2 py-3">
              <div className="flex flex-wrap items-center gap-3 text-sm">
                <Badge variant={a.type === "AUDIO" ? "default" : "secondary"}>
                  {a.type === "AUDIO" ? "音频" : "文本"}
                </Badge>
                <span className="font-medium">{a.name}</span>
                {a.category && <Badge variant="outline">{a.category}</Badge>}
                <span className="text-muted-foreground">使用 {a.usageCount} 次</span>
                <span className="flex-1" />
                <Button variant="ghost" size="sm" onClick={() => toggleUsage(a.id)}>
                  {usageOpen === a.id ? "收起使用记录" : "使用记录"}
                </Button>
                {canManage && <Button variant="ghost" size="sm" onClick={() => remove(a.id)}>删除</Button>}
              </div>
              {a.type === "AUDIO" && a.previewUrl && (
                <audio controls src={a.previewUrl} className="w-full" />
              )}
              {a.type === "TEXT" && a.textContent && (
                <p className="rounded-md bg-muted/50 p-2 text-sm">{a.textContent}</p>
              )}
              {usageOpen === a.id && (
                <div className="space-y-1 border-t pt-2">
                  {usages.length === 0 && <p className="text-xs text-muted-foreground">暂无使用记录</p>}
                  {usages.map((u) => (
                    <div key={u.id} className="text-xs text-muted-foreground">
                      EP{u.episodeNumber} · {u.episodeTitle}
                      {u.positionMs != null && ` · 位置 ${fmtMs(u.positionMs)}`}
                      {u.note && ` · ${u.note}`}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function fmtMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  return `${Math.floor(total / 60)}:${(total % 60).toString().padStart(2, "0")}`;
}
