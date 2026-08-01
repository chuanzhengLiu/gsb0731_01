import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { ShareLink } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

interface Props {
  episodeId: number;
}

/**
 * Guest share-link management (README §3.1/§8): create a 7-day link, list
 * existing links with access counts, and revoke. The openable URL is shown once
 * on creation (only the token hash is stored server-side).
 */
export function ShareLinksCard({ episodeId }: Props) {
  const [links, setLinks] = useState<ShareLink[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [freshUrl, setFreshUrl] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setLinks(await api<ShareLink[]>(`/episodes/${episodeId}/share-links`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }, [episodeId]);

  useEffect(() => {
    load();
  }, [load]);

  async function create() {
    setError(null);
    try {
      const link = await api<ShareLink>(`/episodes/${episodeId}/share-links`, { method: "POST" });
      setFreshUrl(link.url);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建失败");
    }
  }

  async function revoke(id: number) {
    setError(null);
    try {
      await api(`/share-links/${id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "撤销失败");
    }
  }

  function badge(l: ShareLink) {
    if (l.revoked) return <Badge variant="outline">已撤销</Badge>;
    if (l.expired) return <Badge variant="outline">已过期</Badge>;
    return <Badge>有效</Badge>;
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>访客分享链接</CardTitle>
        <Button variant="outline" size="sm" onClick={create}>生成分享链接（7天）</Button>
      </CardHeader>
      <CardContent className="space-y-2">
        {error && <p className="text-sm text-destructive">{error}</p>}
        {freshUrl && (
          <div className="rounded-md border border-emerald-300 bg-emerald-50 p-2 text-sm">
            链接已生成（请复制，仅显示一次）：
            <code className="ml-1 break-all">{freshUrl}</code>
          </div>
        )}
        {links.length === 0 && <p className="text-sm text-muted-foreground">暂无分享链接</p>}
        {links.map((l) => (
          <div key={l.id} className="flex flex-wrap items-center gap-3 rounded-md border p-2 text-sm">
            {badge(l)}
            <span className="text-muted-foreground">到期 {fmtDate(l.expiresAt)}</span>
            <span className="text-muted-foreground">访问 {l.accessCount} 次</span>
            <span className="flex-1" />
            {!l.revoked && !l.expired && (
              <Button variant="ghost" size="sm" onClick={() => revoke(l.id)}>撤销</Button>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
