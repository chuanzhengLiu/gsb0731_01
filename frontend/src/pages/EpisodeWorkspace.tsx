import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Label, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useToast } from '@/components/ui/toast'
import WaveformPlayer from '@/components/WaveformPlayer'
import {
  MARKER_STATUS_LABELS,
  MARKER_TYPE_LABELS,
  EPISODE_STATUS_LABELS,
  type AudioVersion,
  type Episode,
  type Marker,
  type MarkerType,
  type MarkerStatus,
  type Task,
  type TranscriptSegment,
} from '@/lib/types'
import { formatTime } from '@/lib/utils'
import { ArrowLeft, Plus, Upload, Trash2 } from 'lucide-react'
import MarkerPanel from '@/components/MarkerPanel'
import TranscriptPanel from '@/components/TranscriptPanel'
import TaskPanel from '@/components/TaskPanel'
import DistributionPanel from '@/components/DistributionPanel'
import VersionList from '@/components/VersionList'

const MARKER_COLORS: Record<string, string> = {
  SLIP: 'rgba(239,68,68,0.85)',
  RE_RECORD: 'rgba(245,158,11,0.85)',
  VOLUME: 'rgba(59,130,246,0.85)',
  BACKGROUND_MUSIC: 'rgba(168,85,247,0.85)',
  SFX: 'rgba(16,185,129,0.85)',
  TRANSITION: 'rgba(236,72,153,0.85)',
  FACT_CHECK: 'rgba(99,102,241,0.85)',
}

const TABS = [
  { id: 'markers', label: '标记' },
  { id: 'transcript', label: '转写' },
  { id: 'tasks', label: '任务' },
  { id: 'versions', label: '版本' },
  { id: 'distribution', label: '分发' },
] as const

type Tab = (typeof TABS)[number]['id']

export default function EpisodeWorkspace() {
  const { episodeId } = useParams()
  const { toast } = useToast()
  const [episode, setEpisode] = useState<Episode | null>(null)
  const [versions, setVersions] = useState<AudioVersion[]>([])
  const [activeVersionId, setActiveVersionId] = useState<number | null>(null)
  const [markers, setMarkers] = useState<Marker[]>([])
  const [tab, setTab] = useState<Tab>('markers')
  const [showMarkerDialog, setShowMarkerDialog] = useState(false)
  const [filter, setFilter] = useState({ type: '', status: '', keyword: '' })
  const pendingTimeRef = useRef<number>(0)
  const [markerForm, setMarkerForm] = useState({
    startTimeMs: 0,
    endTimeMs: '',
    type: 'SLIP' as MarkerType,
    description: '',
  })

  const activeVersion = useMemo(
    () => versions.find((v) => v.id === activeVersionId && !v.archived)
      || versions.find((v) => !v.archived)
      || null,
    [versions, activeVersionId]
  )

  const loadEpisode = useCallback(async () => {
    const ep = await api.get<Episode>(`/podcasts/episodes/${episodeId}`)
    setEpisode(ep)
  }, [episodeId])

  const loadVersions = useCallback(async () => {
    const list = await api.get<AudioVersion[]>(`/episodes/${episodeId}/audio`)
    setVersions(list)
    // Default to the newest non-archived version; archived versions cannot be played online.
    const firstPlayable = list.find((v) => !v.archived)
    if (firstPlayable) setActiveVersionId(firstPlayable.id)
  }, [episodeId])

  const loadMarkers = useCallback(async () => {
    if (!activeVersionId) {
      setMarkers([])
      return
    }
    const params = new URLSearchParams()
    if (filter.type) params.set('type', filter.type)
    if (filter.status) params.set('status', filter.status)
    if (filter.keyword) params.set('keyword', filter.keyword)
    const list = await api.get<Marker[]>(`/audio-versions/${activeVersionId}/markers?${params}`)
    setMarkers(list)
  }, [activeVersionId, filter])

  useEffect(() => {
    loadEpisode()
    loadVersions()
  }, [loadEpisode, loadVersions])

  useEffect(() => {
    loadMarkers()
  }, [loadMarkers])

  const upload = async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    try {
      await api.postForm(`/episodes/${episodeId}/audio`, form)
      toast('音频上传成功，正在处理波形...', 'success')
      await loadVersions()
      await loadEpisode()
    } catch (err) {
      toast(err instanceof Error ? err.message : '上传失败', 'error')
    }
  }

  const openMarkerAt = (ms: number) => {
    pendingTimeRef.current = ms
    setMarkerForm({
      startTimeMs: ms,
      endTimeMs: '',
      type: 'SLIP',
      description: '',
    })
    setShowMarkerDialog(true)
  }

  const createMarker = async () => {
    if (!activeVersionId) return
    try {
      await api.post(`/audio-versions/${activeVersionId}/markers`, {
        startTimeMs: markerForm.startTimeMs,
        endTimeMs: markerForm.endTimeMs ? Number(markerForm.endTimeMs) : null,
        type: markerForm.type,
        description: markerForm.description,
      })
      setShowMarkerDialog(false)
      toast('标记已添加', 'success')
      loadMarkers()
    } catch (err) {
      toast(err instanceof Error ? err.message : '添加失败', 'error')
    }
  }

  const updateMarkerStatus = async (id: number, status: MarkerStatus) => {
    if (!activeVersionId) return
    await api.patch(`/audio-versions/${activeVersionId}/markers/${id}`, { status })
    loadMarkers()
  }

  const deleteMarker = async (id: number) => {
    if (!activeVersionId) return
    await api.delete(`/audio-versions/${activeVersionId}/markers/${id}`)
    loadMarkers()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to={episode ? `/podcasts/${episode.podcastId}` : '/podcasts'}>
          <Button variant="ghost" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold">
              #{episode?.number} {episode?.title}
            </h1>
            {episode && <Badge>{EPISODE_STATUS_LABELS[episode.status]}</Badge>}
          </div>
          {episode?.theme && <p className="text-sm text-muted-foreground mt-1">{episode.theme}</p>}
        </div>
        <label className="inline-flex items-center justify-center rounded-md text-sm font-medium h-9 px-4 py-2 bg-primary text-primary-foreground hover:bg-primary/90 cursor-pointer">
          <input
            type="file"
            accept=".mp3,.wav,.m4a,audio/*"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) upload(f)
            }}
          />
          <Upload className="mr-2 h-4 w-4" /> 上传音频
        </label>
      </div>

      {activeVersion ? (
        <WaveformPlayer
          key={activeVersion.id}
          audioUrl={activeVersion.fileUrl}
          peaksUrl={activeVersion.peaksUrl}
          durationMs={activeVersion.durationMs}
          markers={markers}
          currentTimeMs={0}
          onSeek={(ms) => openMarkerAt(ms)}
          markerColorByType={MARKER_COLORS}
        />
      ) : (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            <p>尚未上传音频。上传粗剪 MP3/WAV/M4A 文件后即可开始时间轴协作。</p>
          </CardContent>
        </Card>
      )}

      <div className="flex items-center gap-2 border-b">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === t.id
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'markers' && (
        <MarkerPanel
          markers={markers}
          filter={filter}
          setFilter={setFilter}
          onAdd={() => openMarkerAt(pendingTimeRef.current)}
          onUpdateStatus={updateMarkerStatus}
          onDelete={deleteMarker}
        />
      )}
      {tab === 'transcript' && activeVersion && <TranscriptPanel versionId={activeVersion.id} />}
      {tab === 'tasks' && episodeId && <TaskPanel episodeId={Number(episodeId)} />}
      {tab === 'versions' && (
        <VersionList
          episodeId={Number(episodeId)}
          versions={versions}
          activeVersionId={activeVersionId}
          onSelect={setActiveVersionId}
          onChanged={() => {
            loadVersions()
            loadEpisode()
          }}
        />
      )}
      {tab === 'distribution' && episodeId && <DistributionPanel episodeId={Number(episodeId)} />}

      <Dialog open={showMarkerDialog} onOpenChange={setShowMarkerDialog}>
        <DialogHeader>
          <DialogTitle>添加标记</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>开始时间</Label>
              <Input
                value={formatTime(markerForm.startTimeMs)}
                onChange={(e) => {
                  const parts = e.target.value.split(':').map(Number)
                  let ms = 0
                  if (parts.length === 2) ms = (parts[0] * 60 + parts[1]) * 1000
                  else if (parts.length === 3) ms = (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
                  setMarkerForm({ ...markerForm, startTimeMs: ms })
                }}
              />
            </div>
            <div className="space-y-2">
              <Label>结束时间（可选，点标记留空）</Label>
              <Input
                placeholder="留空为点标记"
                value={markerForm.endTimeMs}
                onChange={(e) => setMarkerForm({ ...markerForm, endTimeMs: e.target.value })}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label>类型</Label>
            <Select
              value={markerForm.type}
              onChange={(e) => setMarkerForm({ ...markerForm, type: e.target.value as MarkerType })}
            >
              {Object.entries(MARKER_TYPE_LABELS).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </Select>
          </div>
          <div className="space-y-2">
            <Label>描述</Label>
            <Textarea
              value={markerForm.description}
              onChange={(e) => setMarkerForm({ ...markerForm, description: e.target.value })}
              placeholder="说明这里需要修改什么..."
            />
          </div>
          <Button className="w-full" onClick={createMarker}>
            添加标记
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
