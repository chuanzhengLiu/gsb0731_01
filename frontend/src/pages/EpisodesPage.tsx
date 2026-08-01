import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "@/lib/api";
import type { Episode } from "@/lib/types";
import { EPISODE_STATUS_LABELS } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Plus } from "lucide-react";

export function EpisodesPage() {
  const { podcastId } = useParams();
  const { user } = useAuth();
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [number, setNumber] = useState("");
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);

  const canManage = user?.role === "ADMIN" || user?.role === "PRODUCER";

  async function load() {
    try {
      setEpisodes(await api<Episode[]>(`/podcasts/${podcastId}/episodes`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [podcastId]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api<Episode>(`/podcasts/${podcastId}/episodes`, {
        method: "POST",
        body: { number: Number(number), title },
      });
      setNumber("");
      setTitle("");
      setShowForm(false);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建失败");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/" className="text-sm text-muted-foreground hover:underline">← 返回节目列表</Link>
          <h1 className="text-2xl font-bold">单集</h1>
        </div>
        {canManage && (
          <Button onClick={() => setShowForm((v) => !v)}>
            <Plus className="h-4 w-4" /> 新建单集
          </Button>
        )}
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {showForm && (
        <Card>
          <CardHeader><CardTitle>新建单集</CardTitle></CardHeader>
          <CardContent>
            <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-4">
              <div className="space-y-2">
                <Label htmlFor="num">集数</Label>
                <Input id="num" type="number" value={number}
                  onChange={(e) => setNumber(e.target.value)} required className="w-24" />
              </div>
              <div className="space-y-2 flex-1 min-w-[200px]">
                <Label htmlFor="title">标题</Label>
                <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} required />
              </div>
              <Button type="submit">创建</Button>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="space-y-3">
        {episodes.map((ep) => (
          <Link key={ep.id} to={`/episodes/${ep.id}`}>
            <Card className="transition-shadow hover:shadow-md">
              <CardContent className="flex items-center justify-between py-4">
                <div>
                  <span className="font-medium">EP{ep.number} · {ep.title}</span>
                  {ep.theme && <span className="ml-2 text-sm text-muted-foreground">{ep.theme}</span>}
                </div>
                <Badge variant="secondary">{EPISODE_STATUS_LABELS[ep.status]}</Badge>
              </CardContent>
            </Card>
          </Link>
        ))}
        {episodes.length === 0 && <p className="text-sm text-muted-foreground">暂无单集</p>}
      </div>
    </div>
  );
}
