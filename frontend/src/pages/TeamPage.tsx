import { useState, useEffect, useCallback } from "react";
import {
  Loader2,
  Users,
  UserPlus,
  Trash2,
  Copy,
  Mail,
  Shield,
  CalendarDays,
  Settings,
} from "lucide-react";
import { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { cn, formatDate, TEAM_ROLE_LABELS } from "@/lib/utils";
import type { Team } from "@/types/index";
import api from "@/lib/api";

type ApiError = AxiosError<{ message?: string }>;

type TeamRole =
  | "ADMIN"
  | "PRODUCER"
  | "EDITOR"
  | "OPERATOR"
  | "HOST"
  | "GUEST";

interface TeamWithRole extends Team {
  currentUserRole?: TeamRole;
}

interface TeamMember {
  id: string | number;
  userId: string | number;
  email: string;
  name: string;
  roleInTeam: TeamRole;
  joinedAt?: string;
  avatarUrl?: string | null;
}

interface Invitation {
  id: string | number;
  teamId: string | number;
  email: string;
  roleInTeam: TeamRole;
  token: string;
  expiresAt?: string;
  acceptedAt?: string | null;
}

const TEAM_ROLES: TeamRole[] = [
  "ADMIN",
  "PRODUCER",
  "EDITOR",
  "OPERATOR",
  "HOST",
  "GUEST",
];

const ROLE_BADGE_CLASS: Record<TeamRole, string> = {
  ADMIN: "bg-red-100 text-red-700 border-red-200",
  PRODUCER: "bg-purple-100 text-purple-700 border-purple-200",
  EDITOR: "bg-blue-100 text-blue-700 border-blue-200",
  OPERATOR: "bg-amber-100 text-amber-700 border-amber-200",
  HOST: "bg-green-100 text-green-700 border-green-200",
  GUEST: "bg-gray-100 text-gray-700 border-gray-200",
};

function getErrorMessage(err: unknown, fallback = "操作失败"): string {
  const axiosError = err as ApiError;
  return axiosError?.response?.data?.message || fallback;
}

function getInitial(name: string): string {
  return (name || "?").trim().charAt(0).toUpperCase() || "?";
}

export function TeamPage() {
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<TeamWithRole[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState<string>("");
  const [currentRole, setCurrentRole] = useState<TeamRole | null>(null);

  const [members, setMembers] = useState<TeamMember[]>([]);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);

  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<TeamRole>("EDITOR");
  const [inviting, setInviting] = useState(false);

  const [updatingRoleId, setUpdatingRoleId] = useState<string | number | null>(
    null
  );
  const [removingId, setRemovingId] = useState<string | number | null>(null);

  useEffect(() => {
    const loadTeams = async () => {
      try {
        const res = await api.get("/teams");
        const data: TeamWithRole[] = res.data.data;
        setTeams(data);
        if (data.length > 0) {
          setSelectedTeamId(String(data[0].id));
          setCurrentRole(data[0].currentUserRole ?? null);
        }
      } catch (err) {
        toast.error(getErrorMessage(err, "加载团队列表失败"));
      } finally {
        setLoading(false);
      }
    };
    loadTeams();
  }, [toast]);

  const loadTeamDetail = useCallback(
    async (teamId: string) => {
      setMembersLoading(true);
      try {
        const [teamRes, membersRes, invitationsRes] = await Promise.all([
          api.get(`/teams/${teamId}`),
          api.get(`/teams/${teamId}/members`),
          api.get(`/teams/${teamId}/invitations`),
        ]);
        const teamData: TeamWithRole = teamRes.data.data;
        setCurrentRole(teamData.currentUserRole ?? null);
        setMembers(membersRes.data.data);
        setInvitations(invitationsRes.data.data);
      } catch (err) {
        toast.error(getErrorMessage(err, "加载团队信息失败"));
      } finally {
        setMembersLoading(false);
      }
    },
    [toast]
  );

  useEffect(() => {
    if (!selectedTeamId) {
      setMembers([]);
      setInvitations([]);
      setCurrentRole(null);
      return;
    }
    loadTeamDetail(selectedTeamId);
  }, [selectedTeamId, loadTeamDetail]);

  const handleTeamChange = (teamId: string) => {
    setSelectedTeamId(teamId);
    const team = teams.find((t) => String(t.id) === teamId);
    setCurrentRole(team?.currentUserRole ?? null);
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) {
      toast.error("请输入邮箱");
      return;
    }
    if (!selectedTeamId) return;
    setInviting(true);
    try {
      await api.post(`/teams/${selectedTeamId}/invite`, {
        email: inviteEmail.trim(),
        roleInTeam: inviteRole,
      });
      toast.success("邀请已发送");
      setInviteOpen(false);
      setInviteEmail("");
      setInviteRole("EDITOR");
      const res = await api.get(`/teams/${selectedTeamId}/invitations`);
      setInvitations(res.data.data);
    } catch (err) {
      toast.error(getErrorMessage(err, "发送邀请失败"));
    } finally {
      setInviting(false);
    }
  };

  const handleRoleChange = async (
    memberId: string | number,
    role: TeamRole
  ) => {
    if (!selectedTeamId) return;
    setUpdatingRoleId(memberId);
    try {
      await api.patch(`/teams/${selectedTeamId}/members/${memberId}`, {
        roleInTeam: role,
      });
      toast.success("角色已更新");
      setMembers((prev) =>
        prev.map((m) => (m.id === memberId ? { ...m, roleInTeam: role } : m))
      );
    } catch (err) {
      toast.error(getErrorMessage(err, "更新角色失败"));
    } finally {
      setUpdatingRoleId(null);
    }
  };

  const handleRemove = async (member: TeamMember) => {
    if (!selectedTeamId) return;
    if (
      !window.confirm(`确定要将成员"${member.name}"移出团队吗？`)
    )
      return;
    setRemovingId(member.id);
    try {
      await api.delete(
        `/teams/${selectedTeamId}/members/${member.id}`
      );
      toast.success("成员已移除");
      setMembers((prev) => prev.filter((m) => m.id !== member.id));
    } catch (err) {
      toast.error(getErrorMessage(err, "移除成员失败"));
    } finally {
      setRemovingId(null);
    }
  };

  const handleCopyToken = async (invitation: Invitation) => {
    try {
      await navigator.clipboard.writeText(invitation.token);
      toast.success("邀请令牌已复制");
    } catch {
      toast.error("复制失败，请手动复制");
    }
  };

  const isAdmin = currentRole === "ADMIN";
  const selectedTeam = teams.find((t) => String(t.id) === selectedTeamId);

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
          <h1 className="text-3xl font-bold tracking-tight">团队设置</h1>
          <p className="mt-1 text-muted-foreground">
            管理团队成员、角色与待处理邀请
          </p>
        </div>
        {teams.length > 1 && (
          <Select value={selectedTeamId} onValueChange={handleTeamChange}>
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

      {teams.length === 0 ? (
        <Card className="flex flex-col items-center justify-center py-20 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
            <Users className="h-8 w-8 text-muted-foreground" />
          </div>
          <CardTitle className="mt-6 text-xl">暂无团队</CardTitle>
          <CardDescription className="mt-2 max-w-sm">
            您还没有加入任何团队，请联系管理员创建团队或接受团队邀请。
          </CardDescription>
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10">
                    <Settings className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <CardTitle className="text-lg">
                      {selectedTeam?.name}
                    </CardTitle>
                    <CardDescription>
                      {members.length} 名成员
                      {currentRole &&
                        ` · 您的角色：${TEAM_ROLE_LABELS[currentRole] ?? currentRole}`}
                    </CardDescription>
                  </div>
                </div>
                {isAdmin && (
                  <Button onClick={() => setInviteOpen(true)}>
                    <UserPlus className="mr-2 h-4 w-4" />
                    邀请成员
                  </Button>
                )}
              </div>
            </CardHeader>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Users className="h-4 w-4" />
                团队成员
              </CardTitle>
              <CardDescription>
                查看并管理团队成员及其角色权限
              </CardDescription>
            </CardHeader>
            <CardContent>
              {membersLoading ? (
                <div className="flex justify-center py-10">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : members.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <Users className="h-8 w-8 text-muted-foreground/50" />
                  <p className="mt-2 text-sm text-muted-foreground">
                    暂无团队成员
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left text-xs text-muted-foreground">
                        <th className="pb-2 font-medium">成员</th>
                        <th className="pb-2 font-medium">邮箱</th>
                        <th className="pb-2 text-center font-medium">角色</th>
                        <th className="pb-2 font-medium">加入时间</th>
                        {isAdmin && (
                          <th className="pb-2 text-right font-medium">操作</th>
                        )}
                      </tr>
                    </thead>
                    <tbody>
                      {members.map((member) => (
                        <tr
                          key={member.id}
                          className="border-b last:border-0 hover:bg-muted/40"
                        >
                          <td className="py-3">
                            <div className="flex items-center gap-3">
                              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-medium text-primary">
                                {member.avatarUrl ? (
                                  <img
                                    src={member.avatarUrl}
                                    alt={member.name}
                                    className="h-full w-full rounded-full object-cover"
                                  />
                                ) : (
                                  getInitial(member.name)
                                )}
                              </div>
                              <span className="font-medium">
                                {member.name}
                              </span>
                            </div>
                          </td>
                          <td className="py-3 text-muted-foreground">
                            {member.email}
                          </td>
                          <td className="py-3 text-center">
                            {isAdmin ? (
                              <Select
                                value={member.roleInTeam}
                                onValueChange={(v) =>
                                  handleRoleChange(member.id, v as TeamRole)
                                }
                                disabled={updatingRoleId === member.id}
                              >
                                <SelectTrigger className="h-8 w-[130px] text-xs">
                                  {updatingRoleId === member.id ? (
                                    <span className="flex items-center gap-1 text-muted-foreground">
                                      <Loader2 className="h-3 w-3 animate-spin" />
                                      更新中
                                    </span>
                                  ) : (
                                    <SelectValue />
                                  )}
                                </SelectTrigger>
                                <SelectContent>
                                  {TEAM_ROLES.map((r) => (
                                    <SelectItem key={r} value={r}>
                                      {TEAM_ROLE_LABELS[r] ?? r}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            ) : (
                              <Badge
                                variant="outline"
                                className={cn(
                                  "border",
                                  ROLE_BADGE_CLASS[member.roleInTeam]
                                )}
                              >
                                {TEAM_ROLE_LABELS[member.roleInTeam] ??
                                  member.roleInTeam}
                              </Badge>
                            )}
                          </td>
                          <td className="py-3 text-muted-foreground">
                            <span className="flex items-center gap-1">
                              <CalendarDays className="h-3.5 w-3.5" />
                              {member.joinedAt
                                ? formatDate(member.joinedAt)
                                : "-"}
                            </span>
                          </td>
                          {isAdmin && (
                            <td className="py-3 text-right">
                              <Button
                                variant="ghost"
                                size="sm"
                                className="text-red-600 hover:text-red-600 hover:bg-red-50"
                                onClick={() => handleRemove(member)}
                                disabled={removingId === member.id}
                              >
                                {removingId === member.id ? (
                                  <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                                ) : (
                                  <Trash2 className="mr-1.5 h-3.5 w-3.5" />
                                )}
                                移除
                              </Button>
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Mail className="h-4 w-4" />
                待处理邀请
              </CardTitle>
              <CardDescription>
                已发送但尚未被接受的团队邀请
              </CardDescription>
            </CardHeader>
            <CardContent>
              {invitations.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <Shield className="h-8 w-8 text-muted-foreground/50" />
                  <p className="mt-2 text-sm text-muted-foreground">
                    暂无待处理的邀请
                  </p>
                </div>
              ) : (
                <ul className="divide-y">
                  {invitations.map((inv) => (
                    <li
                      key={inv.id}
                      className="flex flex-wrap items-center justify-between gap-3 py-3"
                    >
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted">
                          <Mail className="h-4 w-4 text-muted-foreground" />
                        </div>
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">
                            {inv.email}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {inv.expiresAt
                              ? `有效期至 ${formatDate(inv.expiresAt)}`
                              : "长期有效"}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge
                          variant="outline"
                          className={cn(
                            "border",
                            ROLE_BADGE_CLASS[inv.roleInTeam]
                          )}
                        >
                          {TEAM_ROLE_LABELS[inv.roleInTeam] ?? inv.roleInTeam}
                        </Badge>
                        {isAdmin && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleCopyToken(inv)}
                          >
                            <Copy className="mr-1.5 h-3.5 w-3.5" />
                            复制邀请链接
                          </Button>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>邀请成员</DialogTitle>
                <DialogDescription>
                  输入被邀请人的邮箱并分配角色，系统将生成邀请令牌。
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={handleInvite}>
                <div className="space-y-4 py-2">
                  <div className="space-y-2">
                    <Label htmlFor="invite-email">
                      邮箱 <span className="text-destructive">*</span>
                    </Label>
                    <Input
                      id="invite-email"
                      type="email"
                      placeholder="member@example.com"
                      value={inviteEmail}
                      onChange={(e) => setInviteEmail(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="invite-role">角色</Label>
                    <Select
                      value={inviteRole}
                      onValueChange={(v) => setInviteRole(v as TeamRole)}
                    >
                      <SelectTrigger id="invite-role">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {TEAM_ROLES.map((r) => (
                          <SelectItem key={r} value={r}>
                            {TEAM_ROLE_LABELS[r] ?? r}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <DialogFooter className="mt-4 gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setInviteOpen(false)}
                    disabled={inviting}
                  >
                    取消
                  </Button>
                  <Button type="submit" disabled={inviting}>
                    {inviting && (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    )}
                    发送邀请
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </>
      )}
    </div>
  );
}
