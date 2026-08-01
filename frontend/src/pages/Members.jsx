import { useEffect, useState } from 'react'
import api, { getUser } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label, Select } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { Dialog } from '../components/ui/dialog'
import { ROLES } from '../lib/utils'

export default function Members() {
  const [members, setMembers] = useState([])
  const [sessions, setSessions] = useState([])
  const [open, setOpen] = useState(false)
  const [invite, setInvite] = useState({ email: '', role: 'EDITOR' })
  const [inviteResult, setInviteResult] = useState('')
  const [error, setError] = useState('')
  const me = getUser()
  const isAdmin = me?.role === 'ADMIN'

  const load = () => {
    api.get('/teams/members').then((res) => setMembers(res.data))
    api.get('/auth/sessions').then((res) => setSessions(res.data))
  }
  useEffect(load, [])

  const submitInvite = async (e) => {
    e.preventDefault()
    setError('')
    setInviteResult('')
    try {
      const { data } = await api.post('/teams/invites', invite)
      setInviteResult(data.message)
    } catch (err) {
      setError(err.response?.data?.message || '邀请失败')
    }
  }

  const removeMember = async (id) => {
    if (!window.confirm('确认移除该成员？')) return
    await api.delete(`/teams/members/${id}`)
    load()
  }

  const revokeSession = async (id) => {
    await api.delete(`/auth/sessions/${id}`)
    load()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">团队成员</h2>
        {isAdmin && <Button onClick={() => setOpen(true)}>邀请成员</Button>}
      </div>

      <Card>
        <CardContent className="pt-6">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="pb-2">姓名</th><th className="pb-2">邮箱</th>
                <th className="pb-2">角色</th><th className="pb-2">加入时间</th>
                {isAdmin && <th className="pb-2">操作</th>}
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.id} className="border-b">
                  <td className="py-2">{m.name}</td>
                  <td>{m.email}</td>
                  <td><Badge variant="secondary">{ROLES[m.roleInTeam] || m.roleInTeam}</Badge></td>
                  <td>{m.joinedAt?.slice(0, 10)}</td>
                  {isAdmin && (
                    <td>
                      {m.userId !== me.id && (
                        <Button size="sm" variant="destructive" onClick={() => removeMember(m.id)}>移除</Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>我的活跃会话</CardTitle></CardHeader>
        <CardContent>
          <ul className="space-y-2 text-sm">
            {sessions.map((s) => (
              <li key={s.id} className="flex items-center justify-between rounded-md border p-3">
                <span>{s.ipAddress || '未知IP'} · {(s.userAgent || '').slice(0, 60)} · 登录于 {s.createdAt?.slice(0, 16).replace('T', ' ')}</span>
                <Button size="sm" variant="outline" onClick={() => revokeSession(s.id)}>强制下线</Button>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <Dialog open={open} onClose={() => setOpen(false)} title="邀请成员（链接24小时有效）">
        <form onSubmit={submitInvite} className="space-y-4">
          <div className="space-y-2">
            <Label>邮箱</Label>
            <Input type="email" value={invite.email} required
              onChange={(e) => setInvite({ ...invite, email: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>角色</Label>
            <Select value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value })}>
              {Object.entries(ROLES).filter(([k]) => k !== 'ADMIN').map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </Select>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          {inviteResult && (
            <p className="rounded-md bg-green-50 p-2 text-sm text-green-800">{inviteResult}</p>
          )}
          <Button className="w-full">发送邀请邮件</Button>
        </form>
      </Dialog>
    </div>
  )
}
