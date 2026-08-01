import { useEffect, useRef, useState } from 'react'
import api from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label, Select, Textarea } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { Dialog } from '../components/ui/dialog'

const ASSET_CATEGORIES = ['开场音乐', '过渡音效', '广告片花', '口播文案', '赞助商口播', '节目slogan']

export default function Assets() {
  const [assets, setAssets] = useState([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ name: '', type: 'AUDIO', category: '', content: '' })
  const [detail, setDetail] = useState(null) // 使用追踪
  const [usages, setUsages] = useState([])
  const [usageForm, setUsageForm] = useState(null) // {assetId, episodeId, positionMs}
  const [episodes, setEpisodes] = useState([])
  const fileRef = useRef(null)
  const [uploadTarget, setUploadTarget] = useState(null)

  const load = () => api.get('/assets').then((res) => setAssets(res.data))
  useEffect(() => { load() }, [])

  // 加载所有单集供使用记录选择
  useEffect(() => {
    api.get('/podcasts').then(async (res) => {
      const all = []
      for (const p of res.data) {
        const eps = await api.get(`/podcasts/${p.id}/episodes`).catch(() => ({ data: [] }))
        all.push(...eps.data.map((e) => ({ id: e.id, label: `${p.name} 第${e.number}期 ${e.title}` })))
      }
      setEpisodes(all)
    }).catch(() => {})
  }, [])

  const submit = async (e) => {
    e.preventDefault()
    await api.post('/assets', {
      name: form.name, type: form.type,
      category: form.category || null, content: form.content || null,
    })
    setOpen(false)
    setForm({ name: '', type: 'AUDIO', category: '', content: '' })
    load()
  }

  const uploadFile = async (file) => {
    if (!file || !uploadTarget) return
    const data = new FormData()
    data.append('file', file)
    await api.post(`/assets/${uploadTarget}/file`, data)
    setUploadTarget(null)
    if (fileRef.current) fileRef.current.value = ''
    load()
  }

  const showUsages = async (asset) => {
    const res = await api.get(`/assets/${asset.id}/usages`)
    setUsages(res.data)
    setDetail(asset)
  }

  const recordUsage = async (e) => {
    e.preventDefault()
    await api.post(`/assets/${usageForm.assetId}/usages`, {
      episodeId: parseInt(usageForm.episodeId, 10),
      positionMs: usageForm.positionMs ? parseInt(usageForm.positionMs, 10) : null,
    })
    setUsageForm(null)
    load()
    if (detail) showUsages(detail)
  }

  const remove = async (id) => {
    if (!window.confirm('确认删除该素材？')) return
    await api.delete(`/assets/${id}`)
    load()
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">素材库</h2>
        <Button onClick={() => setOpen(true)}>新增素材</Button>
      </div>
      <input ref={fileRef} type="file" accept=".wav,.mp3,.m4a" className="hidden"
        onChange={(e) => uploadFile(e.target.files[0])} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {assets.map((a) => (
          <Card key={a.id}>
            <CardHeader>
              <CardTitle className="flex items-center justify-between text-base">
                {a.name}
                <Badge variant={a.type === 'AUDIO' ? 'default' : 'secondary'}>
                  {a.type === 'AUDIO' ? '音频' : '文本'}
                </Badge>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p className="text-xs text-muted-foreground">分类：{a.category || '未分类'} · 使用 {a.usageCount} 次</p>
              {a.type === 'TEXT' && a.content && (
                <p className="line-clamp-3 rounded bg-muted p-2 text-xs">{a.content}</p>
              )}
              {a.type === 'AUDIO' && a.fileUrl && (
                <p className="text-xs text-green-700">音频文件已上传</p>
              )}
              <div className="flex flex-wrap gap-1">
                {a.type === 'AUDIO' && (
                  <Button size="sm" variant="outline"
                    onClick={() => { setUploadTarget(a.id); fileRef.current?.click() }}>
                    {a.fileUrl ? '替换文件' : '上传文件'}
                  </Button>
                )}
                <Button size="sm" variant="outline" onClick={() => showUsages(a)}>使用追踪</Button>
                <Button size="sm" variant="outline"
                  onClick={() => setUsageForm({ assetId: a.id, episodeId: '', positionMs: '' })}>
                  记录使用
                </Button>
                <Button size="sm" variant="ghost" className="text-destructive" onClick={() => remove(a.id)}>删除</Button>
              </div>
            </CardContent>
          </Card>
        ))}
        {assets.length === 0 && <p className="text-sm text-muted-foreground">暂无素材</p>}
      </div>

      <Dialog open={open} onClose={() => setOpen(false)} title="新增素材">
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label>名称</Label>
            <Input value={form.name} required onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>类型</Label>
            <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="AUDIO">音频素材</option>
              <option value="TEXT">文本素材</option>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>分类</Label>
            <Select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
              <option value="">未分类</option>
              {ASSET_CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </Select>
          </div>
          {form.type === 'TEXT' && (
            <div className="space-y-2">
              <Label>文案内容</Label>
              <Textarea value={form.content}
                onChange={(e) => setForm({ ...form, content: e.target.value })} />
            </div>
          )}
          <Button className="w-full">保存</Button>
        </form>
      </Dialog>

      <Dialog open={!!detail} onClose={() => setDetail(null)} title={`使用追踪：${detail?.name}`}>
        {usages.length === 0 ? (
          <p className="text-sm text-muted-foreground">暂无使用记录</p>
        ) : (
          <ul className="space-y-2 text-sm">
            {usages.map((u) => {
              const ep = episodes.find((e) => e.id === u.episodeId)
              return (
                <li key={u.id} className="rounded-md border p-2">
                  {ep?.label || `单集#${u.episodeId}`}
                  {u.positionMs != null && ` · 位置 ${(u.positionMs / 1000).toFixed(1)}秒`}
                  <span className="text-xs text-muted-foreground"> · {u.createdAt?.slice(0, 10)}</span>
                </li>
              )
            })}
          </ul>
        )}
      </Dialog>

      <Dialog open={!!usageForm} onClose={() => setUsageForm(null)} title="记录素材使用">
        {usageForm && (
          <form onSubmit={recordUsage} className="space-y-4">
            <div className="space-y-2">
              <Label>使用到的单集</Label>
              <Select value={usageForm.episodeId} required
                onChange={(e) => setUsageForm({ ...usageForm, episodeId: e.target.value })}>
                <option value="">选择单集</option>
                {episodes.map((e) => <option key={e.id} value={e.id}>{e.label}</option>)}
              </Select>
            </div>
            <div className="space-y-2">
              <Label>使用位置（毫秒，可选）</Label>
              <Input type="number" min="0" value={usageForm.positionMs}
                onChange={(e) => setUsageForm({ ...usageForm, positionMs: e.target.value })} />
            </div>
            <Button className="w-full">保存</Button>
          </form>
        )}
      </Dialog>
    </div>
  )
}
