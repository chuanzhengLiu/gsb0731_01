import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Label, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useToast } from '@/components/ui/toast'
import { Plus, Radio } from 'lucide-react'
import type { Podcast, PodcastType } from '@/lib/types'

const TYPE_LABELS: Record<PodcastType, string> = {
  INTERVIEW: '访谈',
  NARRATIVE: '叙事',
  KNOWLEDGE: '知识',
  NEWS: '新闻',
}

export default function PodcastsPage() {
  const { toast } = useToast()
  const [podcasts, setPodcasts] = useState<Podcast[]>([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ name: '', type: 'INTERVIEW' as PodcastType, updateFrequency: '', targetDurationSeconds: '' })

  const load = () => api.get<Podcast[]>('/podcasts').then(setPodcasts)

  useEffect(() => {
    load()
  }, [])

  const create = async () => {
    try {
      await api.post('/podcasts', {
        name: form.name,
        type: form.type,
        updateFrequency: form.updateFrequency || null,
        targetDurationSeconds: form.targetDurationSeconds ? Number(form.targetDurationSeconds) : null,
      })
      setOpen(false)
      setForm({ name: '', type: 'INTERVIEW', updateFrequency: '', targetDurationSeconds: '' })
      toast('节目创建成功', 'success')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '创建失败', 'error')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">节目管理</h1>
          <p className="text-muted-foreground">管理你的播客节目与单集</p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> 新建节目
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {podcasts.map((p) => (
          <Link key={p.id} to={`/podcasts/${p.id}`}>
            <Card className="h-full hover:shadow-md transition-shadow cursor-pointer">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <div className="flex h-9 w-9 items-center justify-center rounded-md bg-primary/10">
                    <Radio className="h-4 w-4 text-primary" />
                  </div>
                  <CardTitle className="text-base">{p.name}</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <div className="text-sm text-muted-foreground">类型：{TYPE_LABELS[p.type]}</div>
                {p.targetDurationSeconds && (
                  <div className="text-sm text-muted-foreground">
                    目标时长：{Math.round(p.targetDurationSeconds / 60)} 分钟
                  </div>
                )}
              </CardContent>
            </Card>
          </Link>
        ))}
        {podcasts.length === 0 && (
          <Card className="col-span-full">
            <CardContent className="py-12 text-center text-muted-foreground">
              还没有节目，点击右上角创建第一个节目
            </CardContent>
          </Card>
        )}
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogHeader>
          <DialogTitle>新建节目</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>节目名称</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>类型</Label>
            <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as PodcastType })}>
              {Object.entries(TYPE_LABELS).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </Select>
          </div>
          <div className="space-y-2">
            <Label>更新频率</Label>
            <Input
              placeholder="如 每周更新"
              value={form.updateFrequency}
              onChange={(e) => setForm({ ...form, updateFrequency: e.target.value })}
            />
          </div>
          <div className="space-y-2">
            <Label>目标时长（秒）</Label>
            <Input
              type="number"
              value={form.targetDurationSeconds}
              onChange={(e) => setForm({ ...form, targetDurationSeconds: e.target.value })}
            />
          </div>
          <Button className="w-full" onClick={create}>
            创建
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
