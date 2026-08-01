import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input, Textarea, Label } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useToast } from '@/components/ui/toast'
import { useAuth } from '@/lib/auth'
import { TASK_STATUS_LABELS, type Task, type TaskStatus, type Member } from '@/lib/types'
import { formatDate } from '@/lib/utils'
import { Plus, Trash2 } from 'lucide-react'

const COLUMNS: { status: TaskStatus; label: string; color: 'default' | 'warning' | 'success' | 'secondary' }[] = [
  { status: 'TODO', label: '待办', color: 'secondary' },
  { status: 'IN_PROGRESS', label: '进行中', color: 'warning' },
  { status: 'DONE', label: '完成', color: 'success' },
]

export default function TaskPanel({ episodeId }: { episodeId: number }) {
  const { toast } = useToast()
  const { user } = useAuth()
  const [tasks, setTasks] = useState<Task[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ title: '', description: '', assigneeId: '', dueDate: '' })

  const load = async () => {
    setTasks(await api.get<Task[]>(`/episodes/${episodeId}/tasks`))
  }

  useEffect(() => {
    load()
    if (user?.activeTeamId) {
      api.get<Member[]>(`/teams/${user.activeTeamId}/members`).then(setMembers).catch(() => {})
    }
  }, [episodeId, user?.activeTeamId])

  const create = async () => {
    try {
      await api.post(`/episodes/${episodeId}/tasks`, {
        title: form.title,
        description: form.description || null,
        assigneeId: form.assigneeId ? Number(form.assigneeId) : null,
        dueDate: form.dueDate ? new Date(form.dueDate).toISOString() : null,
      })
      setOpen(false)
      setForm({ title: '', description: '', assigneeId: '', dueDate: '' })
      toast('任务已创建', 'success')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '创建失败', 'error')
    }
  }

  const move = async (task: Task, status: TaskStatus) => {
    await api.patch(`/tasks/${task.id}`, { status })
    load()
  }

  const remove = async (id: number) => {
    await api.delete(`/tasks/${id}`)
    load()
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> 新建任务
        </Button>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {COLUMNS.map((col) => (
          <div key={col.status} className="space-y-2">
            <div className="font-medium text-sm flex items-center gap-2">
              {col.label}
              <Badge variant={col.color as 'default'}>
                {tasks.filter((t) => t.status === col.status).length}
              </Badge>
            </div>
            {tasks
              .filter((t) => t.status === col.status)
              .map((t) => (
                <Card key={t.id}>
                  <CardContent className="py-3 space-y-2">
                    <div className="font-medium text-sm">{t.title}</div>
                    {t.description && <p className="text-xs text-muted-foreground">{t.description}</p>}
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <span>{t.assigneeName || '未分配'}</span>
                      {t.dueDate && <span>截止 {formatDate(t.dueDate)}</span>}
                    </div>
                    <div className="flex gap-1">
                      {col.status !== 'TODO' && (
                        <Button size="sm" variant="ghost" onClick={() => move(t, 'TODO')}>
                          ← 待办
                        </Button>
                      )}
                      {col.status !== 'IN_PROGRESS' && col.status !== 'DONE' && (
                        <Button size="sm" variant="ghost" onClick={() => move(t, 'IN_PROGRESS')}>
                          开始
                        </Button>
                      )}
                      {col.status !== 'DONE' && (
                        <Button size="sm" variant="ghost" onClick={() => move(t, 'DONE')}>
                          完成
                        </Button>
                      )}
                      <Button size="icon" variant="ghost" className="ml-auto" onClick={() => remove(t.id)}>
                        <Trash2 className="h-3 w-3 text-destructive" />
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ))}
          </div>
        ))}
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogHeader>
          <DialogTitle>新建任务</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>标题</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>描述</Label>
            <Textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>负责人</Label>
              <Select
                value={form.assigneeId}
                onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}
              >
                <option value="">未分配</option>
                {members.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.name}
                  </option>
                ))}
              </Select>
            </div>
            <div className="space-y-2">
              <Label>截止日期</Label>
              <Input
                type="datetime-local"
                value={form.dueDate}
                onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
              />
            </div>
          </div>
          <Button className="w-full" onClick={create}>
            创建
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
