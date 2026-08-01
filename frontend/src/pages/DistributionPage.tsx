import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Label } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useToast } from '@/components/ui/toast'
import type { Platform, PlatformAccount } from '@/lib/types'
import { Plus, Trash2, Rss } from 'lucide-react'
import { useAuth } from '@/lib/auth'

export default function DistributionPage() {
  const { toast } = useToast()
  const { user } = useAuth()
  const [platforms, setPlatforms] = useState<Platform[]>([])
  const [accounts, setAccounts] = useState<PlatformAccount[]>([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ platformId: '', displayName: '' })

  const load = async () => {
    const [p, a] = await Promise.all([
      api.get<Platform[]>('/platforms'),
      api.get<PlatformAccount[]>('/platform-accounts'),
    ])
    setPlatforms(p)
    setAccounts(a)
  }

  useEffect(() => {
    load()
  }, [])

  const create = async () => {
    try {
      await api.post('/platform-accounts', {
        platformId: Number(form.platformId),
        displayName: form.displayName,
      })
      toast('平台账号已添加', 'success')
      setOpen(false)
      setForm({ platformId: '', displayName: '' })
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '添加失败', 'error')
    }
  }

  const remove = async (id: number) => {
    if (!confirm('确定删除该平台账号？')) return
    await api.delete(`/platform-accounts/${id}`)
    load()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">分发管理</h1>
          <p className="text-muted-foreground">管理各平台分发账号</p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> 添加平台账号
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>平台账号</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {accounts.length === 0 && (
            <p className="text-sm text-muted-foreground py-6 text-center">
              尚未添加平台账号。支持小宇宙、Apple Podcasts、Spotify、网易云、喜马拉雅。
            </p>
          )}
          {accounts.map((a) => (
            <div key={a.id} className="flex items-center gap-3 rounded-md border p-3">
              <div className="flex-1">
                <div className="font-medium">{a.platformName}</div>
                <div className="text-sm text-muted-foreground">{a.displayName}</div>
              </div>
              <Button size="icon" variant="ghost" onClick={() => remove(a.id)}>
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>RSS Feed</CardTitle>
          <Rss className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-3">
            符合 Podcast RSS 2.0 标准的 feed 地址，可提交到各播客平台。
          </p>
          <p className="text-xs font-mono bg-muted p-2 rounded">
            选择一个节目后，在节目详情中获取 RSS 地址：/api/rss/podcasts/&#123;podcastId&#125;
          </p>
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogHeader>
          <DialogTitle>添加平台账号</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>平台</Label>
            <Select
              value={form.platformId}
              onChange={(e) => setForm({ ...form, platformId: e.target.value })}
            >
              <option value="">选择平台</option>
              {platforms.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="space-y-2">
            <Label>账号显示名称</Label>
            <Input
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
              placeholder="如 我的节目官方账号"
            />
          </div>
          <Button className="w-full" onClick={create}>
            添加
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
