import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import axios from 'axios'
import AudioPlayer from '../components/AudioPlayer'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { MARKER_TYPES, formatMs } from '../lib/utils'

/** 访客分享页：无需登录，token 鉴权，7天过期 */
export default function ShareView() {
  const { token } = useParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const playerRef = useRef(null)

  useEffect(() => {
    axios.get(`/api/share/${token}`)
      .then((res) => setData(res.data))
      .catch((err) => setError(err.response?.data?.message || '分享链接无效或已过期'))
  }, [token])

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted">
        <Card className="w-full max-w-sm"><CardContent className="pt-6 text-center">{error}</CardContent></Card>
      </div>
    )
  }
  if (!data) return <div className="flex min-h-screen items-center justify-center">加载中…</div>

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <div>
        <h2 className="text-2xl font-bold">{data.title}</h2>
        <p className="text-sm text-muted-foreground">
          {data.theme} · 访客链接有效期至 {data.expiresAt?.slice(0, 16).replace('T', ' ')}
        </p>
      </div>

      <Card>
        <CardHeader><CardTitle>音频审听</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {data.audioUrl ? (
            <>
              <AudioPlayer
                ref={playerRef}
                audioUrl={data.audioUrl}
                peaks={data.peaks}
                durationMs={data.durationMs}
                markers={data.markers?.map((m, i) => ({ ...m, id: i }))}
              />
              <button className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
                onClick={() => playerRef.current?.playPause()}>
                播放 / 暂停
              </button>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">该单集暂无可播放音频</p>
          )}
        </CardContent>
      </Card>

      {data.markers?.length > 0 && (
        <Card>
          <CardHeader><CardTitle>制作标记（只读）</CardTitle></CardHeader>
          <CardContent>
            <ul className="space-y-2 text-sm">
              {data.markers.map((m, i) => (
                <li key={i} className="flex items-center gap-3 rounded-md border p-2">
                  <button className="font-mono text-primary hover:underline"
                    onClick={() => playerRef.current?.seekMs(m.startTimeMs)}>
                    {formatMs(m.startTimeMs)}{m.endTimeMs != null ? ` - ${formatMs(m.endTimeMs)}` : ''}
                  </button>
                  <Badge variant="outline">{MARKER_TYPES[m.type]}</Badge>
                  <span>{m.description}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
