import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api, { getUser } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { Dialog } from '../components/ui/dialog'
import { EPISODE_STATUS, PODCAST_TYPES, formatMs } from '../lib/utils'

export default function PodcastDetail() {
  const { id } = useParams()
  const [podcast, setPodcast] = useState(null)
  const [episodes, setEpisodes] = useState([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ number: '', title: '', theme: '', recordDate: '', scheduledPublishAt: '' })
  const [error, setError] = useState('')
  const role = getUser()?.role
  const canEdit = role === 'ADMIN' || role === 'PRODUCER'

  const load = () => {
    api.get(`/podcasts/${id}`).then((res) => setPodcast(res.data))
    api.get(`/podcasts/${id}/episodes`).then((res) => setEpisodes(res.data))
  }
  useEffect(load, [id])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await api.post(`/podcasts/${id}/episodes`, {
        number: parseInt(form.number, 10),
        title: form.title,
        theme: form.theme || null,
        recordDate: form.recordDate || null,
        scheduledPublishAt: form.scheduledPublishAt ? form.scheduledPublishAt + ':00' : null,
      })
      setOpen(false)
      setForm({ number: '', title: '', theme: '', recordDate: '', scheduledPublishAt: '' })
      load()
    } catch (err) {
      setError(err.response?.data?.message || '创建失败')
    }
  }

  const template = (() => {
    try {
      return podcast?.structureTemplateJson ? JSON.parse(podcast.structureTemplateJson) : []
    } catch { return [] }
  })()

  if (!podcast) return <p>加载中…</p>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">{podcast.name}</h2>
          <p className="text-sm text-muted-foreground">
            {PODCAST_TYPES[podcast.type]} · {podcast.updateFrequency || '更新频率未设置'} ·
            目标时长 {podcast.targetDuration ? formatMs(podcast.targetDuration * 1000) : '未设置'}
          </p>
        </div>
        <div className="flex gap-2">
          <a href={`/api/rss/podcasts/${id}`} target="_blank" rel="noreferrer">
            <Button variant="outline">RSS Feed</Button>
          </a>
          {canEdit && <Button onClick={() => setOpen(true)}>新建单集</Button>}
        </div>
      </div>

      {template.length > 0 && (
        <Card>
          <CardHeader><CardTitle>节目结构模板</CardTitle></CardHeader>
          <CardContent className="flex flex-wrap items-center gap-2 text-sm">
            {template.map((s, i) => (
              <span key={i} className="flex items-center gap-2">
                <Badge variant="outline">{s.name}（{formatMs(s.durationSec * 1000)}）</Badge>
                {i < template.length - 1 && <span className="text-muted-foreground">→</span>}
              </span>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader><CardTitle>单集列表（{episodes.length}）</CardTitle></CardHeader>
        <CardContent>
          <div className="space-y-2">
            {episodes.map((ep) => (
              <Link key={ep.id} to={`/episodes/${ep.id}`}
                className="flex items-center justify-between rounded-md border p-4 hover:bg-accent">
                <div>
                  <p className="font-medium">第{ep.number}期 {ep.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {ep.theme || ''} {ep.recordDate ? `· 录制于 ${ep.recordDate}` : ''}
                  </p>
                </div>
                <Badge variant={ep.status === 'PUBLISHED' ? 'success' : 'secondary'}>
                  {EPISODE_STATUS[ep.status] || ep.status}
                </Badge>
              </Link>
            ))}
            {episodes.length === 0 && <p className="text-sm text-muted-foreground">暂无单集</p>}
          </div>
        </CardContent>
      </Card>

      <Dialog open={open} onClose={() => setOpen(false)} title="新建单集">
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label>集数</Label>
            <Input type="number" min="1" value={form.number} required
              onChange={(e) => setForm({ ...form, number: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>标题</Label>
            <Input value={form.title} required
              onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>主题</Label>
            <Input value={form.theme}
              onChange={(e) => setForm({ ...form, theme: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>录制日期</Label>
            <Input type="date" value={form.recordDate}
              onChange={(e) => setForm({ ...form, recordDate: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>排期发布（可选）</Label>
            <Input type="datetime-local" value={form.scheduledPublishAt}
              onChange={(e) => setForm({ ...form, scheduledPublishAt: e.target.value })} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button className="w-full">创建</Button>
        </form>
      </Dialog>
    </div>
  )
}
