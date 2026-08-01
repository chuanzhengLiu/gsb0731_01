import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { StructureComparison } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface Props {
  episodeId: number;
}

/**
 * Structure-template comparison (README §4.1): shows the show's fixed sections
 * with target durations and the actual-vs-template total difference.
 */
export function StructureComparisonCard({ episodeId }: Props) {
  const [data, setData] = useState<StructureComparison | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<StructureComparison>(`/episodes/${episodeId}/structure-comparison`)
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "加载失败"));
  }, [episodeId]);

  if (error) return null;
  if (!data || !data.hasTemplate) {
    return (
      <Card>
        <CardHeader><CardTitle>节目结构模板</CardTitle></CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            该节目未配置固定板块模板（可在节目档案里设置 structure_template_json）。
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader><CardTitle>节目结构模板对比</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        <div className="space-y-1">
          {data.sections.map((s, i) => (
            <div key={i} className="flex items-center justify-between rounded-md border px-3 py-1.5 text-sm">
              <span>{s.name}</span>
              <span className="tabular-nums text-muted-foreground">
                目标 {fmtMsOrNA(s.targetDurationMs ?? null)}
              </span>
            </div>
          ))}
        </div>
        <div className="text-sm">
          模板总时长 {fmtMsOrNA(data.templateTotalMs ?? null)} · 实际时长 {fmtMsOrNA(data.actualDurationMs ?? null)}
          {data.diffMs != null && (
            <span className={data.diffMs >= 0 ? " text-amber-600" : " text-emerald-600"}>
              {" "}（{data.diffMs >= 0 ? "超出" : "少于"} {fmtMs(Math.abs(data.diffMs))}）
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function fmtMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  return `${Math.floor(total / 60)}:${(total % 60).toString().padStart(2, "0")}`;
}

function fmtMsOrNA(ms: number | null): string {
  return ms == null ? "未知" : fmtMs(ms);
}
