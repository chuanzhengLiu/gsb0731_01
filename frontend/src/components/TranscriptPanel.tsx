import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input, Textarea } from '@/components/ui/input'
import { useToast } from '@/components/ui/toast'
import { formatTime } from '@/lib/utils'
import type { TranscriptSegment } from '@/lib/types'
import { Plus, Save } from 'lucide-react'

const SPEAKER_COLORS: Record<string, string> = {
  A: 'text-violet-600',
  B: 'text-blue-600',
  C: 'text-emerald-600',
}

export default function TranscriptPanel({ versionId }: { versionId: number }) {
  const { toast } = useToast()
  const [segments, setSegments] = useState<TranscriptSegment[]>([])
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<TranscriptSegment[]>([])

  const load = async () => {
    const data = await api.get<TranscriptSegment[]>(`/audio-versions/${versionId}/transcript`)
    setSegments(data)
    setDraft(data)
  }

  useEffect(() => {
    load()
  }, [versionId])

  const addSegment = () => {
    const last = draft[draft.length - 1]
    setDraft([
      ...draft,
      {
        startTimeMs: last ? last.endTimeMs : 0,
        endTimeMs: last ? last.endTimeMs + 5000 : 5000,
        text: '',
        speaker: 'A',
      },
    ])
  }

  const update = (idx: number, patch: Partial<TranscriptSegment>) => {
    setDraft((p) => p.map((s, i) => (i === idx ? { ...s, ...patch } : s)))
  }

  const save = async () => {
    try {
      await api.put(`/audio-versions/${versionId}/transcript`, { segments: draft })
      toast('转写已保存', 'success')
      setEditing(false)
      load()
    } catch (err) {
      toast(err instanceof Error ? err.message : '保存失败', 'error')
    }
  }

  const playAt = (ms: number) => {
    const audio = document.querySelector('audio') as HTMLAudioElement | null
    if (audio) {
      audio.currentTime = ms / 1000
      audio.play().catch(() => {})
    }
  }

  const list = editing ? draft : segments

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <p className="text-sm text-muted-foreground">
          点击句子可跳转到对应时间；不同说话人用颜色区分。
        </p>
        <div className="ml-auto flex gap-2">
          {editing ? (
            <>
              <Button variant="outline" onClick={addSegment}>
                <Plus className="mr-2 h-4 w-4" /> 添加段落
              </Button>
              <Button onClick={save}>
                <Save className="mr-2 h-4 w-4" /> 保存
              </Button>
            </>
          ) : (
            <Button variant="outline" onClick={() => setEditing(true)}>
              编辑转写
            </Button>
          )}
        </div>
      </div>

      <Card>
        <CardContent className="py-4 space-y-3 max-h-[500px] overflow-auto">
          {list.length === 0 && (
            <p className="text-center text-muted-foreground py-8">暂无转写内容，可手工添加或接入 Whisper。</p>
          )}
          {list.map((s, idx) => (
            <div key={idx} className="flex gap-3 group">
              {editing ? (
                <>
                  <Input
                    className="w-20 font-mono text-xs"
                    value={formatTime(s.startTimeMs)}
                    onChange={(e) => {
                      const parts = e.target.value.split(':').map(Number)
                      const ms = parts.length === 2 ? (parts[0] * 60 + parts[1]) * 1000 : 0
                      update(idx, { startTimeMs: ms })
                    }}
                  />
                  <Input
                    className="w-24"
                    value={s.speaker || ''}
                    placeholder="说话人"
                    onChange={(e) => update(idx, { speaker: e.target.value })}
                  />
                  <Textarea
                    value={s.text}
                    onChange={(e) => update(idx, { text: e.target.value })}
                  />
                </>
              ) : (
                <>
                  <button
                    className="w-16 text-xs text-muted-foreground hover:text-primary shrink-0 pt-0.5"
                    onClick={() => playAt(s.startTimeMs)}
                  >
                    {formatTime(s.startTimeMs)}
                  </button>
                  {s.speaker && (
                    <span className={`w-10 text-xs font-medium shrink-0 ${SPEAKER_COLORS[s.speaker] || 'text-foreground'}`}>
                      {s.speaker}:
                    </span>
                  )}
                  <p className="text-sm leading-relaxed hover:bg-accent/50 rounded px-1 -mx-1 cursor-pointer flex-1">
                    {s.text}
                  </p>
                </>
              )}
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
