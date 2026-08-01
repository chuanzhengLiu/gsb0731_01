import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'
import WaveSurfer from 'wavesurfer.js'
import RegionsPlugin from 'wavesurfer.js/dist/plugins/regions.esm.js'
import { formatMs } from '../lib/utils'

const TYPE_COLORS = {
  MISSPEAK: 'rgba(239, 68, 68, 0.35)',
  RERECORD: 'rgba(249, 115, 22, 0.35)',
  VOLUME: 'rgba(234, 179, 8, 0.35)',
  BGM: 'rgba(59, 130, 246, 0.35)',
  SFX: 'rgba(168, 85, 247, 0.35)',
  TRANSITION: 'rgba(236, 72, 153, 0.35)',
  FACT_CHECK: 'rgba(34, 197, 94, 0.35)',
}

/**
 * 音频播放器：wavesurfer.js 波形 + 标记区域 + 拖拽选段
 * peaks 为后端预生成的波形数据（秒级加载）
 */
const AudioPlayer = forwardRef(function AudioPlayer(
  { audioUrl, peaks, durationMs, markers, onRangeSelected, onSeek, onTimeUpdate }, ref,
) {
  const containerRef = useRef(null)
  const wsRef = useRef(null)
  const regionsRef = useRef(null)
  const timeRef = useRef(null)

  useEffect(() => {
    if (!containerRef.current || !audioUrl) return undefined

    const regions = RegionsPlugin.create()
    regionsRef.current = regions

    let parsedPeaks = null
    try {
      const arr = typeof peaks === 'string' ? JSON.parse(peaks) : peaks
      if (Array.isArray(arr) && arr.length > 0) parsedPeaks = [arr]
    } catch { /* 无预生成波形时由 wavesurfer 自行解码 */ }

    const ws = WaveSurfer.create({
      container: containerRef.current,
      waveColor: '#94a3b8',
      progressColor: '#3b82f6',
      cursorColor: '#1e293b',
      height: 128,
      url: audioUrl,
      peaks: parsedPeaks || undefined,
      duration: durationMs ? durationMs / 1000 : undefined,
      plugins: [regions],
    })
    wsRef.current = ws

    ws.on('timeupdate', () => {
      const ms = ws.getCurrentTime() * 1000
      if (timeRef.current) timeRef.current.textContent = formatMs(ms)
      onTimeUpdate?.(ms)
    })
    ws.on('seeking', (ms_) => onSeek?.(ms_ * 1000))

    // 拖拽选择时间段
    regions.enableDragSelection({ color: 'rgba(59, 130, 246, 0.2)' })
    regions.on('region-created', (region) => {
      if (region.id.startsWith('marker-')) return // 标记渲染不触发
      const startMs = Math.round(region.start * 1000)
      const endMs = Math.round(region.end * 1000)
      region.remove()
      if (endMs - startMs > 200) {
        onRangeSelected?.(startMs, endMs)
      }
    })

    return () => { ws.destroy() }
  }, [audioUrl])

  // 渲染标记区域
  useEffect(() => {
    const regions = regionsRef.current
    if (!regions || !wsRef.current) return
    regions.getRegions().filter((r) => r.id.startsWith('marker-')).forEach((r) => r.remove())
    ;(markers || []).forEach((m) => {
      const start = m.startTimeMs / 1000
      const end = m.endTimeMs != null ? m.endTimeMs / 1000 : start
      regions.addRegion({
        id: `marker-${m.id}`,
        start,
        end: Math.max(end, start),
        color: TYPE_COLORS[m.type] || 'rgba(100, 116, 139, 0.35)',
        drag: false,
        resize: false,
      })
    })
  }, [markers, audioUrl])

  useImperativeHandle(ref, () => ({
    playPause: () => wsRef.current?.playPause(),
    seekMs: (ms) => wsRef.current?.setTime(ms / 1000),
    skip: (deltaMs) => {
      const ws = wsRef.current
      if (ws) ws.setTime(Math.max(0, ws.getCurrentTime() + deltaMs / 1000))
    },
    getCurrentTimeMs: () => (wsRef.current ? wsRef.current.getCurrentTime() * 1000 : 0),
    isPlaying: () => wsRef.current?.isPlaying() ?? false,
  }))

  return (
    <div className="space-y-1">
      <div ref={containerRef} className="w-full rounded-md border bg-slate-50" />
      <div className="flex justify-between text-xs text-muted-foreground">
        <span ref={timeRef}>00:00</span>
        <span>{formatMs(durationMs)}</span>
      </div>
    </div>
  )
})

export default AudioPlayer
