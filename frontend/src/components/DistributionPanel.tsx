import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/components/ui/toast'
import {
  DISTRIBUTION_STATUS_LABELS,
  type Distribution,
  type DistributionStatus,
  type PlatformAccount,
} from '@/lib/types'
import { Plus, Link2 } from 'lucide-react'

const STATUS_VARIANT: Record<DistributionStatus, 'default' | 'warning' | 'success' | 'secondary' | 'danger'> = {
  NOT_STARTED: 'secondary',
  SUBMITTED: 'default',
  UNDER_REVIEW: 'warning',
  PUBLISHED: 'success',
  REJECTED: 'danger',
}

export default function DistributionPanel({ episodeId }: { episodeId: number }) {
  const { toast } = useToast()
  const [accounts, setAccounts] = useState<PlatformAccount[]>([])
  const [distributions, setDistributions] = useState<Distribution[]>([])
  const [selectedAccount, setSelectedAccount] = useState('')

  const load = async () => {
    const [a, d] = await Promise.all([
      api.get<PlatformAccount[]>('/platform-accounts'),
      api.get<Distribution[]>(`/episodes/${episodeId}/distributions`),
    ])
    setAccounts(a)
    setDistributions(d)
  }

  useEffect(() => {
    load()
  }, [episodeId])

  const add = async () => {
    if (!selectedAccount) return
    try {
      await api.post(`/episodes/${episodeId}/distributions`, {
        platformAccountId: Number(selectedAccount),
      })
      toast('已添加分发任务', 'success')
      setSelectedAccount('')
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '添加失败', 'error')
    }
  }

  const changeStatus = async (id: number, status: DistributionStatus) => {
    await api.patch(`/distributions/${id}`, { status })
    load()
  }

  const usedAccountIds = new Set(distributions.map((d) => d.platformAccountId))
  const available = accounts.filter((a) => !usedAccountIds.has(a.id))

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Select value={selectedAccount} onChange={(e) => setSelectedAccount(e.target.value)} className="max-w-xs">
          <option value="">选择平台账号...</option>
          {available.map((a) => (
            <option key={a.id} value={a.id}>
              {a.platformName} - {a.displayName}
            </option>
          ))}
        </Select>
        <Button onClick={add} disabled={!selectedAccount}>
          <Plus className="mr-2 h-4 w-4" /> 添加分发
        </Button>
      </div>

      <div className="space-y-2">
        {distributions.length === 0 && (
          <Card>
            <CardContent className="py-10 text-center text-muted-foreground">
              暂无分发任务。添加平台后可追踪各平台的审核与上线状态。
            </CardContent>
          </Card>
        )}
        {distributions.map((d) => (
          <Card key={d.id}>
            <CardContent className="py-4 flex items-center gap-4">
              <div className="flex-1">
                <div className="font-medium">
                  {d.platformName} <span className="text-muted-foreground text-sm">({d.accountDisplayName})</span>
                </div>
                {d.rejectionReason && (
                  <div className="text-sm text-red-600 mt-1">拒绝原因：{d.rejectionReason}</div>
                )}
              </div>
              <Badge variant={STATUS_VARIANT[d.status] as 'default'}>
                {DISTRIBUTION_STATUS_LABELS[d.status]}
              </Badge>
              <Select
                value={d.status}
                onChange={(e) => changeStatus(d.id, e.target.value as DistributionStatus)}
                className="w-40"
              >
                {Object.entries(DISTRIBUTION_STATUS_LABELS).map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </Select>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
