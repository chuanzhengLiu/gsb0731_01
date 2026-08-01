import { useEffect, useState } from 'react'
import api from '../lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'

/** 数据统计：团队效率 + 分发覆盖（README 明确不做播放量统计） */
export default function Stats() {
  const [team, setTeam] = useState(null)
  const [distribution, setDistribution] = useState(null)

  useEffect(() => {
    api.get('/stats/team').then((res) => setTeam(res.data))
    api.get('/stats/distribution').then((res) => setDistribution(res.data))
  }, [])

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">数据统计</h2>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard title="团队成员" value={team?.memberCount} />
        <StatCard title="单集总数" value={team?.episodeCount} />
        <StatCard title="人均处理标记数" value={team?.avgMarkersPerMember} />
        <StatCard title="平均审听轮次" value={team?.avgReviewRounds} />
        <StatCard title="逾期任务数" value={team?.overdueTaskCount} warn={team?.overdueTaskCount > 0} />
      </div>

      <Card>
        <CardHeader><CardTitle>分发覆盖</CardTitle></CardHeader>
        <CardContent>
          {distribution?.platforms?.length ? (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-muted-foreground">
                  <th className="pb-2">平台</th><th className="pb-2">分发任务数</th>
                  <th className="pb-2">已上线</th><th className="pb-2">上架率</th>
                  <th className="pb-2">平均审核时长</th>
                </tr>
              </thead>
              <tbody>
                {distribution.platforms.map((p) => (
                  <tr key={p.platformId} className="border-b">
                    <td className="py-2">{p.platformName}</td>
                    <td>{p.totalDistributions}</td>
                    <td>{p.liveCount}</td>
                    <td>{p.liveRate}%</td>
                    <td>{p.avgReviewHours} 小时</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-sm text-muted-foreground">暂无分发数据</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function StatCard({ title, value, warn }) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs text-muted-foreground">{title}</p>
        <p className={`mt-1 text-2xl font-bold ${warn ? 'text-destructive' : ''}`}>
          {value ?? '—'}
        </p>
      </CardContent>
    </Card>
  )
}
