import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/lib/api";
import type { Invitation, Role, TeamMember } from "@/lib/types";
import { ROLE_LABELS } from "@/lib/types";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

// Roles that can be invited as team members (README §3.1; GUEST uses share links).
const INVITABLE_ROLES: Role[] = ["PRODUCER", "EDITOR", "OPERATOR", "HOST"];

export function MembersPage() {
  const { user } = useAuth();
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("EDITOR");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const isAdmin = user?.role === "ADMIN";

  const load = useCallback(async () => {
    try {
      setInvitations(await api<Invitation[]>("/invitations"));
      setMembers(await api<TeamMember[]>("/team/members"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    if (isAdmin) load();
  }, [isAdmin, load]);

  async function invite(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    try {
      await api<Invitation>("/invitations", { method: "POST", body: { email, roleInTeam: role } });
      setNotice(`已向 ${email} 发送邀请（链接 24 小时内有效）`);
      setEmail("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "邀请失败");
    }
  }

  async function revoke(id: number) {
    setError(null);
    try {
      await api(`/invitations/${id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "撤销失败");
    }
  }

  function statusBadge(inv: Invitation) {
    if (inv.accepted) return <Badge variant="secondary">已接受</Badge>;
    if (inv.revoked) return <Badge variant="outline">已撤销</Badge>;
    if (inv.expired) return <Badge variant="outline">已过期</Badge>;
    return <Badge>待接受</Badge>;
  }

  if (!isAdmin) {
    return (
      <div className="space-y-4">
        <Link to="/" className="text-sm text-muted-foreground hover:underline">← 返回</Link>
        <p className="text-sm text-muted-foreground">仅团队管理员可管理成员。</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/" className="text-sm text-muted-foreground hover:underline">← 返回节目列表</Link>
        <h1 className="text-2xl font-bold">团队成员</h1>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {notice && <p className="text-sm text-emerald-600">{notice}</p>}

      <Card>
        <CardHeader><CardTitle>邀请成员</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={invite} className="flex flex-wrap items-end gap-4">
            <div className="flex-1 min-w-[220px] space-y-2">
              <Label htmlFor="email">邮箱</Label>
              <Input id="email" type="email" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="role">团队角色</Label>
              <select id="role" value={role} onChange={(e) => setRole(e.target.value as Role)}
                className="flex h-10 rounded-md border border-input bg-background px-3 py-2 text-sm">
                {INVITABLE_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
              </select>
            </div>
            <Button type="submit">发送邀请</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>团队成员（{members.length}）</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {members.length === 0 && <p className="text-sm text-muted-foreground">暂无成员</p>}
          {members.map((m) => (
            <div key={m.id} className="flex items-center gap-3 rounded-md border p-2 text-sm">
              <span className="font-medium">{m.name}</span>
              <span className="text-muted-foreground">{m.email}</span>
              <span className="flex-1" />
              <Badge variant="outline">{ROLE_LABELS[m.role]}</Badge>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>邀请记录</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {invitations.length === 0 && <p className="text-sm text-muted-foreground">暂无邀请</p>}
          {invitations.map((inv) => {
            const pending = !inv.accepted && !inv.revoked && !inv.expired;
            return (
              <div key={inv.id} className="flex items-center gap-3 rounded-md border p-2 text-sm">
                <span className="flex-1">{inv.email}</span>
                <Badge variant="outline">{ROLE_LABELS[inv.roleInTeam]}</Badge>
                {statusBadge(inv)}
                {pending && (
                  <Button variant="ghost" size="sm" onClick={() => revoke(inv.id)}>撤销</Button>
                )}
              </div>
            );
          })}
        </CardContent>
      </Card>
    </div>
  );
}
