import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface TeamStats {
  episodeCount: number
  totalMarkers: number
  resolvedMarkers: number
  overdueTasks: number
  averageMarkersPerEpisode: number
  perMember: { userId: number; name: string; markersCreated: number; tasksAssigned: number; tasksDone: number }[]
}

interface Coverage {
  platformName: string
  total: number
  published: number
  coveragePercent: number
  averageReviewHours: number | null
}

export default function StatsPage() {
  const [team, setTeam] = useState<TeamStats | null>(null)
  const [coverage, setCoverage] = useState<Coverage[]>([])

  useEffect(() => {
    api.get<TeamStats>('/stats/team').then(setTeam).catch(() => {})
    api.get<Coverage[]>('/stats/distributions').then(setCoverage).catch(() => {})
  }, [])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">数据统计</h1>
        <p className="text-muted-foreground">团队制作效率与分发覆盖概览</p>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="单集总数" value={team?.episodeCount ?? 0} />
        <StatCard label="标记总数" value={team?.totalMarkers ?? 0} />
        <StatCard label="已解决标记" value={team?.resolvedMarkers ?? 0} />
        <StatCard label="逾期待办" value={team?.overdueTasks ?? 0} danger />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>人均效率</CardTitle>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-muted-foreground border-b">
                <th className="py-2">成员</th>
                <th>创建标记</th>
                <th>分配任务</th>
                <th>完成任务</th>
              </tr>
            </thead>
            <tbody>
              {team?.perMember.map((m) => (
                <tr key={m.userId} className="border-b last:border-0">
                  <td className="py-2 font-medium">{m.name}</td>
                  <td>{m.markersCreated}</td>
                  <td>{m.tasksAssigned}</td>
                  <td>{m.tasksDone}</td>
                </tr>
              ))}
              {team?.perMember.length === 0 && (
                <tr>
                  <td colSpan={4} className="py-6 text-center text-muted-foreground">
                    暂无数据
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>分发覆盖</CardTitle>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-muted-foreground border-b">
                <th className="py-2">平台</th>
                <th>分发数</th>
                <th>已上线</th>
                <th>上架率</th>
                <th>平均审核时长</th>
              </tr>
            </thead>
            <tbody>
              {coverage.map((c) => (
                <tr key={c.platformName} className="border-b last:border-0">
                  <td className="py-2 font-medium">{c.platformName}</td>
                  <td>{c.total}</td>
                  <td>{c.published}</td>
                  <td>{c.coveragePercent}%</td>
                  <td>{c.averageReviewHours != null ? `${c.averageReviewHours.toFixed(1)} 小时` : '-'}</td>
                </tr>
              ))}
              {coverage.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-6 text-center text-muted-foreground">
                    暂无分发数据
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  )
}

function StatCard({ label, value, danger }: { label: string; value: number; danger?: boolean }) {
  return (
    <Card>
      <CardContent className="py-6">
        <div className="text-sm text-muted-foreground">{label}</div>
        <div className={`text-3xl font-bold mt-1 ${danger ? 'text-red-600' : ''}`}>{value}</div>
      </CardContent>
    </Card>
  )
}
