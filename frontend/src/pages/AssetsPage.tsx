import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Label, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useToast } from '@/components/ui/toast'
import type { Asset } from '@/lib/types'
import { formatDate } from '@/lib/utils'
import { Plus, Music, FileText, Play } from 'lucide-react'

export default function AssetsPage() {
  const { toast } = useToast()
  const [assets, setAssets] = useState<Asset[]>([])
  const [type, setType] = useState<'' | 'AUDIO' | 'TEXT'>('')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ name: '', type: 'AUDIO' as 'AUDIO' | 'TEXT', content: '' })

  const load = async () => {
    setAssets(await api.get<Asset[]>('/assets' + (type ? `?type=${type}` : '')))
  }

  useEffect(() => {
    load()
  }, [type])

  const create = async () => {
    try {
      await api.post('/assets', {
        name: form.name,
        type: form.type,
        content: form.type === 'TEXT' ? form.content : null,
      })
      setOpen(false)
      setForm({ name: '', type: 'AUDIO', content: '' })
      toast('素材已添加', 'success')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '添加失败', 'error')
    }
  }

  const remove = async (id: number) => {
    if (!confirm('确定删除？')) return
    await api.delete(`/assets/${id}`)
    load()
  }

  const uploadAudio = async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    form.append('name', file.name)
    try {
      await api.postForm('/assets/audio', form)
      toast('音频素材已上传', 'success')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '上传失败', 'error')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">素材库</h1>
          <p className="text-muted-foreground">管理开场音乐、过渡音效、固定口播文案等素材</p>
        </div>
        <div className="flex gap-2">
          <Select value={type} onChange={(e) => setType(e.target.value as '' | 'AUDIO' | 'TEXT')} className="w-36">
            <option value="">全部</option>
            <option value="AUDIO">音频</option>
            <option value="TEXT">文本</option>
          </Select>
          <label className="inline-flex items-center justify-center rounded-md text-sm font-medium h-9 px-4 bg-secondary text-secondary-foreground hover:bg-secondary/80 cursor-pointer">
            <input
              type="file"
              accept=".mp3,.wav,.m4a,audio/*"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) uploadAudio(f)
                e.target.value = ''
              }}
            />
            <Music className="mr-2 h-4 w-4" /> 上传音频
          </label>
          <Button onClick={() => setOpen(true)}>
            <Plus className="mr-2 h-4 w-4" /> 添加文本素材
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {assets.map((a) => (
          <Card key={a.id}>
            <CardContent className="py-4">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary/10">
                  {a.type === 'AUDIO' ? (
                    <Music className="h-5 w-5 text-primary" />
                  ) : (
                    <FileText className="h-5 w-5 text-primary" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{a.name}</div>
                  <div className="text-xs text-muted-foreground">
                    使用 {a.usageCount} 次 · {formatDate(a.createdAt)}
                  </div>
                  {a.type === 'TEXT' && a.content && (
                    <p className="text-sm text-muted-foreground mt-2 line-clamp-3">{a.content}</p>
                  )}
                  {a.type === 'AUDIO' && a.fileUrl && (
                    <audio controls src={a.fileUrl} className="mt-2 w-full h-8" />
                  )}
                </div>
              </div>
              <Button variant="ghost" size="sm" className="mt-2 text-destructive" onClick={() => remove(a.id)}>
                删除
              </Button>
            </CardContent>
          </Card>
        ))}
        {assets.length === 0 && (
          <Card className="col-span-full">
            <CardContent className="py-12 text-center text-muted-foreground">暂无素材</CardContent>
          </Card>
        )}
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogHeader>
          <DialogTitle>添加素材</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>名称</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>类型</Label>
            <Select
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as 'AUDIO' | 'TEXT' })}
            >
              <option value="AUDIO">音频</option>
              <option value="TEXT">文本（口播文案）</option>
            </Select>
          </div>
          {form.type === 'TEXT' && (
            <div className="space-y-2">
              <Label>文案内容</Label>
              <Textarea
                value={form.content}
                onChange={(e) => setForm({ ...form, content: e.target.value })}
                rows={5}
              />
            </div>
          )}
          <Button className="w-full" onClick={create}>
            添加
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
