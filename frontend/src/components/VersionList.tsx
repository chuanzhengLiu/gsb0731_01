import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { useToast } from '@/components/ui/toast'
import { api } from '@/lib/api'
import { formatTime, formatDate } from '@/lib/utils'
import type { AudioVersion } from '@/lib/types'
import { Archive, CheckCircle2, Download, Lock } from 'lucide-react'

interface Props {
  episodeId: number
  versions: AudioVersion[]
  activeVersionId: number | null
  onSelect: (id: number) => void
  onChanged: () => void
}

export default function VersionList({ episodeId, versions, activeVersionId, onSelect, onChanged }: Props) {
  const { toast } = useToast()

  const setFinal = async (id: number) => {
    await api.post(`/episodes/${episodeId}/audio/${id}/final`, {})
    toast('已设为定稿版本', 'success')
    onChanged()
  }

  const archive = async (id: number) => {
    await api.post(`/episodes/${episodeId}/audio/${id}/archive`, {})
    toast('版本已归档', 'success')
    onChanged()
  }

  return (
    <div className="space-y-2">
      <p className="text-sm text-muted-foreground">
        每集保留最近 10 个版本。归档版本仅可下载，不能在线播放。
      </p>
      {versions.map((v) => {
        const isActive = !v.archived && v.id === activeVersionId
        return (
          <Card key={v.id} className={isActive ? 'border-primary' : ''}>
            <CardContent className="py-3 flex items-center gap-4">
              {v.archived ? (
                <div className="px-2 flex items-center gap-1 text-muted-foreground">
                  <Lock className="h-4 w-4" />
                  <span className="font-mono text-lg font-bold">v{v.versionNumber}</span>
                </div>
              ) : (
                <Button variant="ghost" className="px-2" onClick={() => onSelect(v.id)}>
                  <span className="font-mono text-lg font-bold">v{v.versionNumber}</span>
                </Button>
              )}
              <div className="flex-1 min-w-0">
                <div className="font-medium truncate">{v.fileName}</div>
                <div className="text-xs text-muted-foreground">
                  {formatTime(v.durationMs)} · {(v.fileSize / 1024 / 1024).toFixed(1)} MB · {v.uploaderName} ·{' '}
                  {formatDate(v.createdAt)}
                </div>
              </div>
              {v.archived && <Badge variant="secondary">已归档·仅下载</Badge>}
              <a href={v.downloadUrl} download={v.fileName}>
                <Button size="icon" variant="ghost" title="下载">
                  <Download className="h-4 w-4" />
                </Button>
              </a>
              {!v.archived && (
                <>
                  <Button size="sm" variant="outline" onClick={() => archive(v.id)}>
                    <Archive className="mr-1 h-3 w-3" /> 归档
                  </Button>
                  <Button size="sm" onClick={() => setFinal(v.id)}>
                    <CheckCircle2 className="mr-1 h-3 w-3" /> 设为定稿
                  </Button>
                </>
              )}
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
