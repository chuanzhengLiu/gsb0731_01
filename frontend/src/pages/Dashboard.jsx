import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { formatMs, EPISODE_STATUS } from '../lib/utils'

/** 工作台：分配给我的任务 + 最近单集概览 */
export default function Dashboard() {
  const [tasks, setTasks] = useState([])
  const [podcasts, setPodcasts] = useState([])
  const [episodes, setEpisodes] = useState([])

  useEffect(() => {
    api.get('/tasks/mine').then((res) => setTasks(res.data.filter((t) => t.status !== 'DONE'))).catch(() => {})
    api.get('/podcasts').then(async (res) => {
      setPodcasts(res.data)
      const all = []
      for (const p of res.data.slice(0, 3)) {
        const eps = await api.get(`/podcasts/${p.id}/episodes`).catch(() => ({ data: [] }))
        all.push(...eps.data.slice(0, 3).map((e) => ({ ...e, podcastName: p.name })))
      }
      setEpisodes(all)
    }).catch(() => {})
  }, [])

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">工作台</h2>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>我的待办任务</CardTitle>
          </CardHeader>
          <CardContent>
            {tasks.length === 0 ? (
              <p className="text-sm text-muted-foreground">暂无待办任务</p>
            ) : (
              <ul className="space-y-2">
                {tasks.map((t) => (
                  <li key={t.id} className="flex items-center justify-between rounded-md border p-3 text-sm">
                    <span>{t.description}</span>
                    <span className="text-xs text-muted-foreground">
                      {t.dueDate ? `截止 ${t.dueDate}` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>最近单集</CardTitle>
          </CardHeader>
          <CardContent>
            {episodes.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                还没有单集，去<Link to="/podcasts" className="text-primary hover:underline">节目管理</Link>创建
              </p>
            ) : (
              <ul className="space-y-2">
                {episodes.map((e) => (
                  <li key={e.id}>
                    <Link to={`/episodes/${e.id}`}
                      className="flex items-center justify-between rounded-md border p-3 text-sm hover:bg-accent">
                      <span>{e.podcastName} · 第{e.number}期 {e.title}</span>
                      <Badge variant="secondary">{EPISODE_STATUS[e.status] || e.status}</Badge>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>团队节目（{podcasts.length}）</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {podcasts.map((p) => (
            <Link key={p.id} to={`/podcasts/${p.id}`}>
              <Badge variant="outline" className="cursor-pointer px-3 py-1 text-sm">{p.name}</Badge>
            </Link>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
