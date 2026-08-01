import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { getUser } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label, Select, Textarea } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { Dialog } from '../components/ui/dialog'
import { PODCAST_TYPES } from '../lib/utils'

export default function Podcasts() {
  const [podcasts, setPodcasts] = useState([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({
    name: '', type: 'INTERVIEW', updateFrequency: '', targetDuration: '', structureText: '',
  })
  const [error, setError] = useState('')
  const role = getUser()?.role
  const canEdit = role === 'ADMIN' || role === 'PRODUCER'

  const load = () => api.get('/podcasts').then((res) => setPodcasts(res.data))
  useEffect(() => { load() }, [])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    let structureTemplate = null
    if (form.structureText.trim()) {
      // 每行格式：板块名,时长秒数  如：开场,30
      try {
        structureTemplate = form.structureText.trim().split('\n').map((line) => {
          const [name, sec] = line.split(/[,，]/)
          return { name: name.trim(), durationSec: parseInt(sec, 10) }
        })
      } catch {
        setError('结构模板格式错误，每行应为：板块名,秒数')
        return
      }
    }
    try {
      await api.post('/podcasts', {
        name: form.name,
        type: form.type,
        updateFrequency: form.updateFrequency || null,
        targetDuration: form.targetDuration ? parseInt(form.targetDuration, 10) : null,
        structureTemplate,
      })
      setOpen(false)
      setForm({ name: '', type: 'INTERVIEW', updateFrequency: '', targetDuration: '', structureText: '' })
      load()
    } catch (err) {
      setError(err.response?.data?.message || '创建失败')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">节目管理</h2>
        {canEdit && <Button onClick={() => setOpen(true)}>新建节目</Button>}
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {podcasts.map((p) => (
          <Link key={p.id} to={`/podcasts/${p.id}`}>
            <Card className="h-full hover:shadow-md">
              <CardHeader>
                <CardTitle className="flex items-center justify-between">
                  {p.name}
                  <Badge variant="secondary">{PODCAST_TYPES[p.type] || p.type}</Badge>
                </CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-muted-foreground">
                <p>更新频率：{p.updateFrequency || '未设置'}</p>
                <p>目标时长：{p.targetDuration ? `${Math.round(p.targetDuration / 60)} 分钟` : '未设置'}</p>
              </CardContent>
            </Card>
          </Link>
        ))}
        {podcasts.length === 0 && <p className="text-sm text-muted-foreground">暂无节目</p>}
      </div>

      <Dialog open={open} onClose={() => setOpen(false)} title="新建节目">
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label>节目名称</Label>
            <Input value={form.name} required onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>类型</Label>
            <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              {Object.entries(PODCAST_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
          </div>
          <div className="space-y-2">
            <Label>更新频率</Label>
            <Input value={form.updateFrequency} placeholder="如：每周一更"
              onChange={(e) => setForm({ ...form, updateFrequency: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>目标时长（秒）</Label>
            <Input type="number" min="1" value={form.targetDuration} placeholder="如：2400"
              onChange={(e) => setForm({ ...form, targetDuration: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>结构模板（可选，每行：板块名,秒数）</Label>
            <Textarea value={form.structureText} placeholder={'开场,30\n嘉宾介绍,120\n主题讨论,1800\n结尾,60'}
              onChange={(e) => setForm({ ...form, structureText: e.target.value })} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button className="w-full">创建</Button>
        </form>
      </Dialog>
    </div>
  )
}
