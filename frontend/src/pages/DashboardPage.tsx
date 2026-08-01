import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { EPISODE_STATUS_LABELS, type Episode, type Podcast, type Task } from '@/lib/types'
import { Radio, ListTodo, Mic2 } from 'lucide-react'

export default function DashboardPage() {
  const { user } = useAuth()
  const [podcasts, setPodcasts] = useState<Podcast[]>([])
  const [myTasks, setMyTasks] = useState<Task[]>([])
  const [recentEpisodes, setRecentEpisodes] = useState<Episode[]>([])

  useEffect(() => {
    api.get<Podcast[]>('/podcasts').then(setPodcasts).catch(() => {})
    api.get<Task[]>('/tasks/mine').then(setMyTasks).catch(() => {})
  }, [])

  useEffect(() => {
    if (podcasts.length === 0) {
      setRecentEpisodes([])
      return
    }
    Promise.all(
      podcasts.map((p) => api.get<Episode[]>(`/podcasts/${p.id}/episodes`))
    ).then((results) => {
      const all = results.flat().sort((a, b) => b.number - a.number).slice(0, 8)
      setRecentEpisodes(all)
    })
  }, [podcasts])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">你好，{user?.name}</h1>
        <p className="text-muted-foreground">欢迎回到播客制作协作系统</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm">节目数量</CardTitle>
            <Radio className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="text-2xl font-bold">{podcasts.length}</CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm">我的待办</CardTitle>
            <ListTodo className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="text-2xl font-bold">
            {myTasks.filter((t) => t.status !== 'DONE' && t.status !== 'CANCELLED').length}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm">最近单集</CardTitle>
            <Mic2 className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="text-2xl font-bold">{recentEpisodes.length}</CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>最近单集</CardTitle>
            <CardDescription>各节目最新创建的单集</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            {recentEpisodes.length === 0 && (
              <p className="text-sm text-muted-foreground">
                还没有单集，去
                <Link to="/podcasts" className="text-primary hover:underline mx-1">
                  节目管理
                </Link>
                创建吧
              </p>
            )}
            {recentEpisodes.map((ep) => (
              <Link
                key={ep.id}
                to={`/episodes/${ep.id}`}
                className="flex items-center justify-between rounded-md border p-3 hover:bg-accent"
              >
                <div>
                  <div className="font-medium">
                    #{ep.number} {ep.title}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {new Date(ep.createdAt).toLocaleDateString('zh-CN')}
                  </div>
                </div>
                <Badge>{EPISODE_STATUS_LABELS[ep.status]}</Badge>
              </Link>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>我的任务</CardTitle>
            <CardDescription>分配给我的任务</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            {myTasks.length === 0 && <p className="text-sm text-muted-foreground">暂无任务</p>}
            {myTasks.slice(0, 6).map((t) => (
              <Link
                key={t.id}
                to={`/episodes/${t.episodeId}`}
                className="flex items-center justify-between rounded-md border p-3 hover:bg-accent"
              >
                <span className="font-medium">{t.title}</span>
                <Badge variant={t.status === 'DONE' ? 'success' : 'warning'}>
                  {t.status === 'TODO' ? '待办' : t.status === 'IN_PROGRESS' ? '进行中' : t.status === 'DONE' ? '完成' : '取消'}
                </Badge>
              </Link>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
