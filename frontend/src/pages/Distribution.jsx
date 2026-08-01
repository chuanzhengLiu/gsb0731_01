import { useEffect, useState } from 'react'
import api, { getUser } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { Dialog } from '../components/ui/dialog'
import { DISTRIBUTION_STATUS } from '../lib/utils'

const PRESET_PLATFORMS = ['小宇宙', 'Apple Podcasts', 'Spotify', '网易云音乐', '喜马拉雅']

export default function Distribution() {
  const [platforms, setPlatforms] = useState([])
  const [calendar, setCalendar] = useState([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ name: '', accountName: '', categoryOptionsJson: '' })
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const role = getUser()?.role
  const canEdit = ['ADMIN', 'OPERATOR'].includes(role)

  const load = () => api.get('/platforms').then((res) => setPlatforms(res.data))
  useEffect(() => { load() }, [])

  useEffect(() => {
    const from = `${month}-01T00:00:00`
    const [y, m] = month.split('-').map(Number)
    const last = new Date(y, m, 0).getDate()
    api.get(`/calendar?from=${from}&to=${month}-${last}T23:59:59`)
      .then((res) => setCalendar(res.data)).catch(() => {})
  }, [month])

  const submit = async (e) => {
    e.preventDefault()
    await api.post('/platforms', {
      name: form.name,
      accountName: form.accountName || null,
      categoryOptionsJson: form.categoryOptionsJson || null,
    })
    setOpen(false)
    setForm({ name: '', accountName: '', categoryOptionsJson: '' })
    load()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">分发管理</h2>
        {canEdit && <Button onClick={() => setOpen(true)}>添加平台账号</Button>}
      </div>

      <Card>
        <CardHeader><CardTitle>平台账号</CardTitle></CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
            {platforms.map((p) => (
              <div key={p.id} className="rounded-md border p-4 text-sm">
                <p className="font-medium">{p.name}</p>
                <p className="text-xs text-muted-foreground">账号：{p.accountName || '未设置'}</p>
              </div>
            ))}
            {platforms.length === 0 && (
              <p className="text-sm text-muted-foreground">
                暂无平台。常用平台：{PRESET_PLATFORMS.join('、')}
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>发布日历</span>
            <Input type="month" className="w-40" value={month} onChange={(e) => setMonth(e.target.value)} />
          </CardTitle>
        </CardHeader>
        <CardContent>
          {calendar.length === 0 ? (
            <p className="text-sm text-muted-foreground">本月暂无排期发布计划</p>
          ) : (
            <ul className="space-y-2">
              {calendar.map((c) => (
                <li key={c.distributionId} className="flex items-center justify-between rounded-md border p-3 text-sm">
                  <span>{c.episodeTitle} → {c.platformName}</span>
                  <span className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground">{c.scheduledAt?.slice(0, 16).replace('T', ' ')}</span>
                    <Badge variant={c.status === 'LIVE' ? 'success' : 'secondary'}>
                      {DISTRIBUTION_STATUS[c.status]}
                    </Badge>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Dialog open={open} onClose={() => setOpen(false)} title="添加平台账号">
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label>平台名称</Label>
            <Input value={form.name} required list="preset-platforms"
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <datalist id="preset-platforms">
              {PRESET_PLATFORMS.map((p) => <option key={p} value={p} />)}
            </datalist>
          </div>
          <div className="space-y-2">
            <Label>账号名</Label>
            <Input value={form.accountName}
              onChange={(e) => setForm({ ...form, accountName: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>分类选项（JSON，可选）</Label>
            <Input value={form.categoryOptionsJson} placeholder='["Technology","Education"]'
              onChange={(e) => setForm({ ...form, categoryOptionsJson: e.target.value })} />
          </div>
          <Button className="w-full">保存</Button>
        </form>
      </Dialog>
    </div>
  )
}
