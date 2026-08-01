import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Label, Textarea } from '@/components/ui/input'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/components/ui/toast'
import { EPISODE_STATUS_LABELS, type Episode, type Podcast } from '@/lib/types'
import { ArrowLeft, Plus } from 'lucide-react'

export default function PodcastDetailPage() {
  const { podcastId } = useParams()
  const { toast } = useToast()
  const [podcast, setPodcast] = useState<Podcast | null>(null)
  const [episodes, setEpisodes] = useState<Episode[]>([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ title: '', theme: '', recordDate: '' })

  const load = async () => {
    const [p, eps] = await Promise.all([
      api.get<Podcast>(`/podcasts/${podcastId}`),
      api.get<Episode[]>(`/podcasts/${podcastId}/episodes`),
    ])
    setPodcast(p)
    setEpisodes(eps)
  }

  useEffect(() => {
    load()
  }, [podcastId])

  const create = async () => {
    try {
      await api.post(`/podcasts/${podcastId}/episodes`, {
        title: form.title,
        theme: form.theme || null,
        recordDate: form.recordDate || null,
      })
      setOpen(false)
      setForm({ title: '', theme: '', recordDate: '' })
      toast('单集已创建', 'success')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '创建失败', 'error')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/podcasts">
          <Button variant="ghost" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{podcast?.name}</h1>
          <p className="text-sm text-muted-foreground">共 {episodes.length} 集</p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> 新建单集
        </Button>
      </div>

      <div className="space-y-3">
        {episodes.map((ep) => (
          <Link key={ep.id} to={`/episodes/${ep.id}`}>
            <Card className="hover:shadow-md transition-shadow cursor-pointer">
              <CardContent className="flex items-center justify-between py-4">
                <div>
                  <div className="font-medium">
                    #{ep.number} {ep.title}
                  </div>
                  {ep.theme && <div className="text-sm text-muted-foreground mt-0.5">{ep.theme}</div>}
                </div>
                <Badge>{EPISODE_STATUS_LABELS[ep.status]}</Badge>
              </CardContent>
            </Card>
          </Link>
        ))}
        {episodes.length === 0 && (
          <Card>
            <CardContent className="py-12 text-center text-muted-foreground">
              还没有单集，点击右上角创建
            </CardContent>
          </Card>
        )}
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogHeader>
          <DialogTitle>新建单集</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>标题</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>主题</Label>
            <Textarea value={form.theme} onChange={(e) => setForm({ ...form, theme: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>录制日期</Label>
            <Input
              type="date"
              value={form.recordDate}
              onChange={(e) => setForm({ ...form, recordDate: e.target.value })}
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
