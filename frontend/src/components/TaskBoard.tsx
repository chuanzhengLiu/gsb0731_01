import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Task, TaskStatus, TeamMember } from "@/lib/types";
import { TASK_STATUS_LABELS } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

const TASK_STATUSES: TaskStatus[] = ["TODO", "IN_PROGRESS", "DONE"];

interface Props {
  episodeId: number;
}

/**
 * Per-episode task board (README §4.1 任务看板): producers assign tasks with a
 * due date; assignees progress their own task's status. This is the entry point
 * behind the "剪辑师只能操作分配给自己的单集" rule.
 */
export function TaskBoard({ episodeId }: Props) {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [desc, setDesc] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const [dueDate, setDueDate] = useState("");

  const canManage = user?.role === "ADMIN" || user?.role === "PRODUCER";

  const load = useCallback(async () => {
    try {
      setTasks(await api<Task[]>(`/episodes/${episodeId}/tasks`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载任务失败");
    }
  }, [episodeId]);

  useEffect(() => {
    load();
    if (canManage) {
      api<TeamMember[]>("/team/members").then(setMembers).catch(() => {});
    }
  }, [load, canManage]);

  async function createTask(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api(`/episodes/${episodeId}/tasks`, {
        method: "POST",
        body: {
          description: desc,
          assigneeId: assigneeId ? Number(assigneeId) : null,
          dueDate: dueDate || null,
        },
      });
      setDesc("");
      setAssigneeId("");
      setDueDate("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建任务失败");
    }
  }

  async function changeStatus(t: Task, status: TaskStatus) {
    if (status === t.status) return;
    setError(null);
    try {
      await api(`/tasks/${t.id}/status`, { method: "PATCH", body: { status } });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "更新状态失败");
    }
  }

  async function remove(t: Task) {
    setError(null);
    try {
      await api(`/tasks/${t.id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败");
    }
  }

  return (
    <Card>
      <CardHeader><CardTitle>任务看板</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        {error && <p className="text-sm text-destructive">{error}</p>}

        {canManage && (
          <form onSubmit={createTask} className="flex flex-wrap items-end gap-3 rounded-md border p-3">
            <div className="flex-1 min-w-[200px] space-y-1">
              <label className="text-xs text-muted-foreground">任务描述</label>
              <Input value={desc} onChange={(e) => setDesc(e.target.value)} required
                placeholder="例：完成 12:30 处补录" />
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">负责人</label>
              <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}
                className="flex h-9 rounded-md border border-input bg-background px-2 text-sm">
                <option value="">未指派</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>{m.name}（{m.role}）</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">截止日期</label>
              <Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)}
                className="w-40" />
            </div>
            <Button type="submit">添加任务</Button>
          </form>
        )}

        <div className="space-y-2">
          {tasks.length === 0 && <p className="text-sm text-muted-foreground">暂无任务</p>}
          {tasks.map((t) => (
            <div key={t.id} className="flex flex-wrap items-center gap-3 rounded-md border p-2 text-sm">
              <span className="flex-1">{t.description}</span>
              {t.assigneeName && <Badge variant="outline">{t.assigneeName}</Badge>}
              {t.dueDate && (
                <span className={t.overdue ? "text-destructive" : "text-muted-foreground"}>
                  截止 {t.dueDate}{t.overdue ? "（逾期）" : ""}
                </span>
              )}
              <select value={t.status}
                onChange={(e) => changeStatus(t, e.target.value as TaskStatus)}
                className="h-8 rounded-md border border-input bg-background px-2 text-xs">
                {TASK_STATUSES.map((s) => (
                  <option key={s} value={s}>{TASK_STATUS_LABELS[s]}</option>
                ))}
              </select>
              {canManage && (
                <Button variant="ghost" size="sm" onClick={() => remove(t)}>删除</Button>
              )}
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
