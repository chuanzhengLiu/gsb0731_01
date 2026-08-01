import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api";
import type { Podcast } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Plus } from "lucide-react";

const PODCAST_TYPES = ["访谈", "叙事", "知识", "新闻"];

export function PodcastsPage() {
  const { user } = useAuth();
  const [podcasts, setPodcasts] = useState<Podcast[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState(PODCAST_TYPES[0]);
  const [error, setError] = useState<string | null>(null);

  const canManage = user?.role === "ADMIN" || user?.role === "PRODUCER";

  async function load() {
    try {
      setPodcasts(await api<Podcast[]>("/podcasts"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api<Podcast>("/podcasts", { method: "POST", body: { name, type } });
      setName("");
      setShowForm(false);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建失败");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">节目</h1>
        {canManage && (
          <Button onClick={() => setShowForm((v) => !v)}>
            <Plus className="h-4 w-4" /> 新建节目
          </Button>
        )}
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle>新建节目</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-4">
              <div className="space-y-2">
                <Label htmlFor="pname">名称</Label>
                <Input id="pname" value={name} onChange={(e) => setName(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="ptype">类型</Label>
                <select id="ptype" value={type} onChange={(e) => setType(e.target.value)}
                  className="flex h-10 rounded-md border border-input bg-background px-3 py-2 text-sm">
                  {PODCAST_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <Button type="submit">创建</Button>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {podcasts.map((p) => (
          <Link key={p.id} to={`/podcasts/${p.id}`}>
            <Card className="transition-shadow hover:shadow-md">
              <CardHeader>
                <CardTitle>{p.name}</CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-muted-foreground">
                类型：{p.type}
                {p.updateFrequency ? ` · 更新：${p.updateFrequency}` : ""}
              </CardContent>
            </Card>
          </Link>
        ))}
        {podcasts.length === 0 && (
          <p className="text-sm text-muted-foreground">暂无节目</p>
        )}
      </div>
    </div>
  );
}
