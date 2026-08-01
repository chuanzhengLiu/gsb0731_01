import { useEffect, useRef, useState } from 'react'
import WaveSurfer from 'wavesurfer.js'
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatTime } from '@/lib/utils'
import type { Marker } from '@/lib/types'

interface WaveformPlayerProps {
  audioUrl: string | null
  peaksUrl?: string | null
  durationMs: number
  markers: Marker[]
  currentTimeMs: number
  onSeek: (ms: number) => void
  onReady?: () => void
  markerColorByType?: Record<string, string>
}

export default function WaveformPlayer({
  audioUrl,
  peaksUrl,
  markers,
  onSeek,
  onReady,
  markerColorByType = {},
}: WaveformPlayerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const wavesurferRef = useRef<WaveSurfer | null>(null)
  const markerRefs = useRef<Record<number, HTMLDivElement | null>>({})
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentMs, setCurrentMs] = useState(0)
  const [duration, setDuration] = useState(0)
  const [muted, setMuted] = useState(false)

  useEffect(() => {
    if (!containerRef.current || !audioUrl) return
    let cancelled = false

    const ws = WaveSurfer.create({
      container: containerRef.current,
      waveColor: '#c4b5fd',
      progressColor: '#7c3aed',
      cursorColor: '#4c1d95',
      height: 96,
      barWidth: 2,
      barRadius: 2,
      url: audioUrl,
      normalize: true,
    })
    wavesurferRef.current = ws

    ws.on('ready', () => {
      if (cancelled) return
      setDuration(ws.getDuration() * 1000)
      if (peaksUrl) {
        fetch(peaksUrl)
          .then((r) => (r.ok ? r.json() : null))
          .then((peaks) => {
            if (Array.isArray(peaks)) ws.setOptions({ peaks: [peaks] })
          })
          .catch(() => {})
      }
      onReady?.()
    })
    ws.on('timeupdate', (time) => setCurrentMs(time * 1000))
    ws.on('play', () => setIsPlaying(true))
    ws.on('pause', () => setIsPlaying(false))
    ws.on('finish', () => setIsPlaying(false))

    return () => {
      cancelled = true
      ws.destroy()
      wavesurferRef.current = null
    }
  }, [audioUrl])

  useEffect(() => {
    const ws = wavesurferRef.current
    if (!ws || !duration) return
    Object.values(markerRefs.current).forEach((el) => {
      if (el && el.parentNode) el.parentNode.removeChild(el)
    })
    markerRefs.current = {}
    const wrapper = containerRef.current
    if (!wrapper) return
    const scroll = wrapper.querySelector('div') as HTMLElement | null
    if (!scroll) return

    markers.forEach((m) => {
      const leftPct = Math.min(100, (m.startTimeMs / duration) * 100)
      const marker = document.createElement('div')
      marker.style.position = 'absolute'
      marker.style.top = '0'
      marker.style.bottom = '0'
      marker.style.left = `${leftPct}%`
      marker.style.width = m.endTimeMs ? `${Math.max(0.5, ((m.endTimeMs - m.startTimeMs) / duration) * 100)}%` : '3px'
      marker.style.backgroundColor =
        markerColorByType[m.type] || (m.endTimeMs ? 'rgba(245,158,11,0.35)' : 'rgba(239,68,68,0.85)')
      marker.style.borderRadius = '2px'
      marker.style.pointerEvents = 'none'
      marker.title = m.description
      markerRefs.current[m.id] = marker
      scroll.appendChild(marker)
    })
  }, [markers, duration])

  const toggle = () => wavesurferRef.current?.playPause()
  const seek = (sec: number) => {
    const ws = wavesurferRef.current
    if (!ws) return
    const dur = ws.getDuration()
    ws.seekTo(Math.max(0, Math.min(dur, sec)) / dur)
  }

  const nudge = (deltaSec: number) => {
    const ws = wavesurferRef.current
    if (!ws) return
    seek(ws.getCurrentTime() + deltaSec)
  }

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
      if (e.code === 'Space') {
        e.preventDefault()
        toggle()
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault()
        nudge(-5)
      } else if (e.code === 'ArrowRight') {
        e.preventDefault()
        nudge(5)
      } else if (e.code === 'KeyM') {
        e.preventDefault()
        onSeek(currentMs)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [currentMs, onSeek])

  return (
    <div className="rounded-lg border bg-card p-4 space-y-3">
      <div ref={containerRef} className="relative" />
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" onClick={() => nudge(-5)}>
            <SkipBack className="h-4 w-4" />
          </Button>
          <Button onClick={toggle} size="icon">
            {isPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
          </Button>
          <Button variant="ghost" size="icon" onClick={() => nudge(5)}>
            <SkipForward className="h-4 w-4" />
          </Button>
          <span className="text-sm tabular-nums text-muted-foreground ml-2">
            {formatTime(currentMs)} / {formatTime(duration)}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => {
              const ws = wavesurferRef.current
              if (!ws) return
              ws.setMuted(!muted)
              setMuted(!muted)
            }}
          >
            {muted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
          </Button>
        </div>
      </div>
    </div>
  )
}
