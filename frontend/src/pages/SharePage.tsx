import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import WaveSurfer from 'wavesurfer.js'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { formatTime } from '@/lib/utils'
import { Play, Pause } from 'lucide-react'

interface ShareData {
  episode: { id: number; title: string; theme: string; status: string; number: number }
  podcast: { id: number; name: string }
  latestVersion: {
    id: number
    versionNumber: number
    fileUrl: string
    durationMs: number
    peaksUrl: string
  } | null
  expiresAt: string
}

export default function SharePage() {
  const { token } = useParams()
  const [data, setData] = useState<ShareData | null>(null)
  const [error, setError] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)
  const wsRef = useRef<WaveSurfer | null>(null)
  const [playing, setPlaying] = useState(false)
  const [currentMs, setCurrentMs] = useState(0)
  const [duration, setDuration] = useState(0)

  useEffect(() => {
    api
      .get<ShareData>(`/share/${token}`)
      .then(setData)
      .catch((e) => setError(e.message || '分享链接无效或已过期'))
  }, [token])

  useEffect(() => {
    if (!data?.latestVersion || !containerRef.current) return
    const ws = WaveSurfer.create({
      container: containerRef.current,
      waveColor: '#c4b5fd',
      progressColor: '#7c3aed',
      height: 80,
      url: data.latestVersion.fileUrl,
    })
    wsRef.current = ws
    ws.on('ready', () => setDuration(ws.getDuration() * 1000))
    ws.on('timeupdate', (t) => setCurrentMs(t * 1000))
    ws.on('play', () => setPlaying(true))
    ws.on('pause', () => setPlaying(false))
    return () => ws.destroy()
  }, [data])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-muted/30 p-4">
        <Card className="max-w-md w-full">
          <CardContent className="py-10 text-center text-muted-foreground">{error}</CardContent>
        </Card>
      </div>
    )
  }

  if (!data) return null

  return (
    <div className="min-h-screen bg-muted/30 p-6">
      <div className="max-w-3xl mx-auto space-y-6">
        <Card>
          <CardHeader>
            <div className="text-sm text-muted-foreground">{data.podcast.name}</div>
            <CardTitle>
              #{data.episode.number} {data.episode.title}
            </CardTitle>
            {data.episode.theme && <p className="text-sm text-muted-foreground">{data.episode.theme}</p>}
          </CardHeader>
          <CardContent className="space-y-4">
            {data.latestVersion ? (
              <>
                <div ref={containerRef} />
                <div className="flex items-center gap-3">
                  <Button onClick={() => wsRef.current?.playPause()}>
                    {playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
                  </Button>
                  <span className="text-sm tabular-nums text-muted-foreground">
                    {formatTime(currentMs)} / {formatTime(duration)}
                  </span>
                </div>
              </>
            ) : (
              <p className="text-muted-foreground">该单集暂无音频</p>
            )}
            <p className="text-xs text-muted-foreground">
              访客分享链接，将在 {new Date(data.expiresAt).toLocaleString('zh-CN')} 过期
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
