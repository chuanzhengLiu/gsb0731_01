import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { MARKER_STATUS_LABELS, MARKER_TYPE_LABELS, type Marker, type MarkerStatus, type MarkerType } from '@/lib/types'
import { formatTime, formatDate } from '@/lib/utils'
import { Plus, Trash2 } from 'lucide-react'

interface Props {
  markers: Marker[]
  filter: { type: string; status: string; keyword: string }
  setFilter: (f: { type: string; status: string; keyword: string }) => void
  onAdd: () => void
  onUpdateStatus: (id: number, status: MarkerStatus) => void
  onDelete: (id: number) => void
}

const STATUS_VARIANT: Record<MarkerStatus, 'default' | 'warning' | 'success' | 'secondary' | 'danger'> = {
  PENDING: 'danger',
  IN_PROGRESS: 'warning',
  RESOLVED: 'success',
  IGNORED: 'secondary',
}

export default function MarkerPanel({ markers, filter, setFilter, onAdd, onUpdateStatus, onDelete }: Props) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Input
          placeholder="搜索标记描述..."
          value={filter.keyword}
          onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
          className="max-w-xs"
        />
        <Select
          value={filter.type}
          onChange={(e) => setFilter({ ...filter, type: e.target.value })}
          className="max-w-[160px]"
        >
          <option value="">全部类型</option>
          {Object.entries(MARKER_TYPE_LABELS).map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </Select>
        <Select
          value={filter.status}
          onChange={(e) => setFilter({ ...filter, status: e.target.value })}
          className="max-w-[160px]"
        >
          <option value="">全部状态</option>
          {Object.entries(MARKER_STATUS_LABELS).map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </Select>
        <Button className="ml-auto" onClick={onAdd}>
          <Plus className="mr-2 h-4 w-4" /> 添加标记
        </Button>
      </div>

      <div className="space-y-2">
        {markers.length === 0 && (
          <Card>
            <CardContent className="py-10 text-center text-muted-foreground">
              暂无标记。在波形图上定位后按 M 键或点击"添加标记"。
            </CardContent>
          </Card>
        )}
        {markers.map((m) => (
          <Card key={m.id}>
            <CardContent className="py-3">
              <div className="flex items-start gap-3">
                <div className="min-w-[90px] text-sm font-mono text-primary">
                  {formatTime(m.startTimeMs)}
                  {m.endTimeMs ? ` - ${formatTime(m.endTimeMs)}` : ''}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Badge variant="default">{MARKER_TYPE_LABELS[m.type]}</Badge>
                    <Badge variant={STATUS_VARIANT[m.status] as 'default'}>
                      {MARKER_STATUS_LABELS[m.status]}
                    </Badge>
                    <span className="text-xs text-muted-foreground">
                      {m.creatorName} · {formatDate(m.createdAt)}
                    </span>
                  </div>
                  <p className="text-sm whitespace-pre-wrap">{m.description}</p>
                </div>
                <div className="flex items-center gap-1">
                  {m.status !== 'RESOLVED' && (
                    <Button size="sm" variant="outline" onClick={() => onUpdateStatus(m.id, 'RESOLVED')}>
                      解决
                    </Button>
                  )}
                  {m.status === 'RESOLVED' && (
                    <Button size="sm" variant="outline" onClick={() => onUpdateStatus(m.id, 'PENDING')}>
                      重开
                    </Button>
                  )}
                  <Button size="icon" variant="ghost" onClick={() => onDelete(m.id)}>
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
