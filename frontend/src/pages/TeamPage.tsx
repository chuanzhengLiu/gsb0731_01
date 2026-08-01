import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Label } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useToast } from '@/components/ui/toast'
import { useAuth } from '@/lib/auth'
import { TEAM_ROLE_LABELS, type Member, type TeamRole } from '@/lib/types'
import { formatDate } from '@/lib/utils'
import { UserPlus, Trash2 } from 'lucide-react'

export default function TeamPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  const [members, setMembers] = useState<Member[]>([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ email: '', role: 'EDITOR' as TeamRole })

  const teamId = user?.activeTeamId

  const load = async () => {
    if (!teamId) return
    setMembers(await api.get<Member[]>(`/teams/${teamId}/members`))
  }

  useEffect(() => {
    load()
  }, [teamId])

  const invite = async () => {
    try {
      await api.post(`/teams/${teamId}/invitations`, { email: form.email, role: form.role })
      toast('邀请已发送，链接 24 小时内有效', 'success')
      setOpen(false)
      setForm({ email: '', role: 'EDITOR' })
    } catch (err) {
      toast(err instanceof Error ? err.message : '邀请失败', 'error')
    }
  }

  const updateRole = async (userId: number, role: TeamRole) => {
    await api.patch(`/teams/${teamId}/members/${userId}`, { role })
    load()
  }

  const remove = async (userId: number) => {
    if (!confirm('确定移除该成员？')) return
    await api.delete(`/teams/${teamId}/members/${userId}`)
    load()
  }

  const isAdmin = user?.roleInTeam === 'ADMIN'

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">团队成员</h1>
          <p className="text-muted-foreground">管理团队成员、角色与邀请</p>
        </div>
        {isAdmin && (
          <Button onClick={() => setOpen(true)}>
            <UserPlus className="mr-2 h-4 w-4" /> 邀请成员
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>成员列表（{members.length}）</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {members.map((m) => (
            <div key={m.userId} className="flex items-center gap-3 rounded-md border p-3">
              <div className="flex-1">
                <div className="font-medium">
                  {m.name}
                  {m.userId === user?.id && <span className="text-muted-foreground text-sm ml-2">（我）</span>}
                </div>
                <div className="text-sm text-muted-foreground">{m.email}</div>
              </div>
              <Badge variant="secondary">{TEAM_ROLE_LABELS[m.role]}</Badge>
              {isAdmin && m.userId !== user?.id && (
                <>
                  <Select
                    value={m.role}
                    onChange={(e) => updateRole(m.userId, e.target.value as TeamRole)}
                    className="w-40"
                  >
                    {Object.entries(TEAM_ROLE_LABELS).map(([v, l]) => (
                      <option key={v} value={v}>
                        {l}
                      </option>
                    ))}
                  </Select>
                  <Button size="icon" variant="ghost" onClick={() => remove(m.userId)}>
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </>
              )}
            </div>
          ))}
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogHeader>
          <DialogTitle>邀请成员</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>邮箱</Label>
            <Input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>角色</Label>
            <Select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as TeamRole })}>
              {Object.entries(TEAM_ROLE_LABELS).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </Select>
          </div>
          <Button className="w-full" onClick={invite}>
            发送邀请
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
