import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api, { getUser } from '../lib/api'
import AudioPlayer from '../components/AudioPlayer'
import { Button } from '../components/ui/button'
import { Input, Label, Select, Textarea } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { Badge } from '../components/ui/card'
import { Dialog } from '../components/ui/dialog'
import {
  DISTRIBUTION_STATUS, EPISODE_STATUS, MARKER_STATUS, MARKER_TYPES, formatMs,
} from '../lib/utils'

const STATUS_BADGE = { PENDING: 'warning', IN_PROGRESS: 'default', RESOLVED: 'success', IGNORED: 'secondary' }

export default function EpisodeDetail() {
  const { id } = useParams()
  const user = getUser()
  const role = user?.role
  const canUpload = ['ADMIN', 'PRODUCER', 'EDITOR'].includes(role)
  const canManage = ['ADMIN', 'PRODUCER'].includes(role)

  const [episode, setEpisode] = useState(null)
  const [podcast, setPodcast] = useState(null)
  const [versions, setVersions] = useState([])
  const [compare, setCompare] = useState(null)
  const [currentVersion, setCurrentVersion] = useState(null)
  const [audio, setAudio] = useState(null) // {url, peaks, durationMs}
  const [markers, setMarkers] = useState([])
  const [filters, setFilters] = useState({ type: '', status: '', keyword: '' })
  const [markerDialog, setMarkerDialog] = useState(null) // {startMs, endMs}
  const [markerForm, setMarkerForm] = useState({ type: 'MISSPEAK', description: '' })
  const [tasks, setTasks] = useState([])
  const [members, setMembers] = useState([])
  const [taskForm, setTaskForm] = useState(null)
  const [transcript, setTranscript] = useState([])
  const [whisperEnabled, setWhisperEnabled] = useState(false)
  const [transcriptImport, setTranscriptImport] = useState(false)
  const [importText, setImportText] = useState('')
  const [editSeg, setEditSeg] = useState(null)
  const [distributions, setDistributions] = useState([])
  const [platforms, setPlatforms] = useState([])
  const [distForm, setDistForm] = useState(null)
  const [shareUrl, setShareUrl] = useState('')
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  const playerRef = useRef(null)
  const fileRef = useRef(null)

  // ---------- 数据加载 ----------
  const loadEpisode = useCallback(() => {
    api.get(`/episodes/${id}`).then((res) => {
      setEpisode(res.data)
      api.get(`/podcasts/${res.data.podcastId}`).then((p) => setPodcast(p.data)).catch(() => {})
    })
  }, [id])

  const loadVersions = useCallback(() => {
    api.get(`/episodes/${id}/versions`).then((res) => {
      setVersions(res.data)
      const latestActive = res.data.find((v) => v.status === 'ACTIVE')
      setCurrentVersion((cur) => cur && res.data.some((v) => v.id === cur.id && v.status === 'ACTIVE')
        ? cur : latestActive || null)
    })
    api.get(`/episodes/${id}/versions/compare`).then((res) => setCompare(res.data)).catch(() => {})
  }, [id])

  const loadMarkers = useCallback(() => {
    const params = new URLSearchParams()
    if (filters.type) params.set('type', filters.type)
    if (filters.status) params.set('status', filters.status)
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (currentVersion) params.set('versionId', currentVersion.id)
    api.get(`/episodes/${id}/markers?${params}`).then((res) => setMarkers(res.data))
  }, [id, filters, currentVersion])

  const loadTasks = useCallback(() => {
    api.get(`/episodes/${id}/tasks`).then((res) => setTasks(res.data))
  }, [id])

  const loadTranscript = useCallback(() => {
    if (!currentVersion) { setTranscript([]); return }
    api.get(`/audio/${currentVersion.id}/transcript`).then((res) => setTranscript(res.data)).catch(() => {})
  }, [currentVersion])

  const loadDistributions = useCallback(() => {
    api.get(`/episodes/${id}/distributions`).then((res) => setDistributions(res.data))
    api.get('/platforms').then((res) => setPlatforms(res.data))
  }, [id])

  useEffect(() => {
    loadEpisode(); loadVersions(); loadTasks(); loadDistributions()
    api.get('/teams/members').then((res) => setMembers(res.data)).catch(() => {})
    api.get('/transcript/enabled').then((res) => setWhisperEnabled(res.data.whisperEnabled)).catch(() => {})
  }, [id])
  useEffect(loadMarkers, [loadMarkers])
  useEffect(loadTranscript, [loadTranscript])

  // 切换版本时加载签名播放 URL + 波形
  useEffect(() => {
    if (!currentVersion) { setAudio(null); return }
    Promise.all([
      api.get(`/audio/${currentVersion.id}/play-url`),
      api.get(`/audio/${currentVersion.id}/waveform`),
    ]).then(([play, wave]) => {
      setAudio({ url: play.data.url, peaks: wave.data.peaks, durationMs: wave.data.durationMs })
    }).catch(() => setAudio(null))
  }, [currentVersion])

  // ---------- 快捷键：空格播放/暂停、M 添加标记、左右箭头微调 ----------
  useEffect(() => {
    const handler = (e) => {
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) return
      if (e.code === 'Space') {
        e.preventDefault()
        playerRef.current?.playPause()
      } else if (e.key === 'm' || e.key === 'M') {
        e.preventDefault()
        const ms = Math.round(playerRef.current?.getCurrentTimeMs() || 0)
        setMarkerDialog({ startMs: ms, endMs: null })
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault()
        playerRef.current?.skip(-5000)
      } else if (e.key === 'ArrowRight') {
        e.preventDefault()
        playerRef.current?.skip(5000)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // ---------- 音频上传 ----------
  const upload = async (file) => {
    if (!file) return
    setUploading(true)
    setError('')
    try {
      const form = new FormData()
      form.append('file', file)
      await api.post(`/episodes/${id}/audio`, form)
      loadVersions()
    } catch (err) {
      setError(err.response?.data?.message || '上传失败')
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  // ---------- 标记 ----------
  const createMarker = async (e) => {
    e.preventDefault()
    try {
      await api.post(`/episodes/${id}/markers`, {
        startTimeMs: markerDialog.startMs,
        endTimeMs: markerDialog.endMs,
        type: markerForm.type,
        description: markerForm.description,
      })
      setMarkerDialog(null)
      setMarkerForm({ type: 'MISSPEAK', description: '' })
      loadMarkers()
    } catch (err) {
      setError(err.response?.data?.message || '创建标记失败')
    }
  }

  const setMarkerStatus = async (markerId, status) => {
    await api.put(`/markers/${markerId}/status`, { status })
    loadMarkers()
  }

  const deleteMarker = async (markerId) => {
    if (!window.confirm('确认删除该标记？')) return
    await api.delete(`/markers/${markerId}`)
    loadMarkers()
  }

  // ---------- 单集状态流转 ----------
  const setEpisodeStatus = async (status) => {
    await api.put(`/episodes/${id}/status`, { status })
    loadEpisode()
  }

  // ---------- 任务 ----------
  const createTask = async (e) => {
    e.preventDefault()
    await api.post(`/episodes/${id}/tasks`, {
      description: taskForm.description,
      assigneeId: taskForm.assigneeId ? parseInt(taskForm.assigneeId, 10) : null,
      dueDate: taskForm.dueDate || null,
    })
    setTaskForm(null)
    loadTasks()
  }

  const setTaskStatus = async (taskId, status) => {
    await api.put(`/tasks/${taskId}/status`, { status })
    loadTasks()
  }

  // ---------- 转写 ----------
  const generateTranscript = async () => {
    if (!currentVersion) return
    try {
      const res = await api.post(`/audio/${currentVersion.id}/transcript/generate`)
      setTranscript(res.data)
    } catch (err) {
      setError(err.response?.data?.message || '转写生成失败')
    }
  }

  const importTranscript = async () => {
    try {
      const segments = JSON.parse(importText)
      const res = await api.post(`/audio/${currentVersion.id}/transcript/import`, { segments })
      setTranscript(res.data)
      setTranscriptImport(false)
      setImportText('')
    } catch {
      setError('转写 JSON 格式错误，应为 [{startTimeMs, endTimeMs, text, speaker}]')
    }
  }

  const saveSegment = async () => {
    await api.put(`/transcript/segments/${editSeg.id}`, {
      text: editSeg.text, startTimeMs: editSeg.startTimeMs, endTimeMs: editSeg.endTimeMs,
    })
    setEditSeg(null)
    loadTranscript()
  }

  // ---------- 分发 ----------
  const createDistribution = async (e) => {
    e.preventDefault()
    await api.post(`/episodes/${id}/distributions`, {
      platformId: parseInt(distForm.platformId, 10),
      platformDataJson: distForm.platformDataJson || null,
      scheduledAt: distForm.scheduledAt ? distForm.scheduledAt + ':00' : null,
    })
    setDistForm(null)
    loadDistributions()
  }

  const setDistStatus = async (distId, status) => {
    await api.put(`/distributions/${distId}/status`, { status })
    loadDistributions()
  }

  const createShare = async () => {
    const { data } = await api.post(`/episodes/${id}/share`)
    setShareUrl(`${window.location.origin}${data.url}`)
  }

  if (!episode) return <p>加载中…</p>

  const actualDuration = currentVersion?.durationMs
  const targetDurationMs = podcast?.targetDuration ? podcast.targetDuration * 1000 : null

  return (
    <div className="space-y-6">
      {/* 头部 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs text-muted-foreground">
            <Link to={`/podcasts/${episode.podcastId}`} className="hover:underline">{podcast?.name || '返回节目'}</Link>
          </p>
          <h2 className="text-2xl font-bold">第{episode.number}期 {episode.title}</h2>
          <p className="text-sm text-muted-foreground">{episode.theme}</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="secondary">{EPISODE_STATUS[episode.status]}</Badge>
          {role !== 'HOST' && (
            <Select className="w-32" value={episode.status}
              onChange={(e) => setEpisodeStatus(e.target.value)}>
              {Object.entries(EPISODE_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
          )}
          {canManage && <Button variant="outline" onClick={createShare}>分享</Button>}
        </div>
      </div>
      {shareUrl && (
        <p className="break-all rounded-md bg-muted p-2 text-xs">
          访客链接（7天有效）：{shareUrl}
        </p>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}

      {/* 音频播放 + 版本 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center justify-between gap-2">
            <span>音频时间轴</span>
            <span className="text-xs font-normal text-muted-foreground">
              快捷键：空格 播放/暂停 · M 添加标记 · ←/→ 快退/快进5秒 · 波形上拖拽选择时间段
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            {canUpload && (
              <>
                <input ref={fileRef} type="file" accept=".wav,.mp3,.m4a" className="hidden"
                  onChange={(e) => upload(e.target.files[0])} />
                <Button onClick={() => fileRef.current?.click()} disabled={uploading}>
                  {uploading ? '上传中…' : versions.length ? '上传新版本' : '上传音频'}
                </Button>
              </>
            )}
            {versions.length > 0 && (
              <Select className="w-56" value={currentVersion?.id || ''}
                onChange={(e) => {
                  const v = versions.find((x) => x.id === parseInt(e.target.value, 10))
                  setCurrentVersion(v)
                }}>
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>
                    v{v.versionNumber} · {formatMs(v.durationMs)}{v.status === 'ARCHIVED' ? '（已归档）' : ''}
                  </option>
                ))}
              </Select>
            )}
            {compare?.durationDiffMs != null && (
              <Badge variant="outline">
                版本对比 v{compare.previousVersion}→v{compare.latestVersion}：
                {compare.durationDiffMs > 0 ? '+' : ''}{(compare.durationDiffMs / 1000).toFixed(1)} 秒
              </Badge>
            )}
            {actualDuration != null && targetDurationMs != null && (
              <Badge variant={Math.abs(actualDuration - targetDurationMs) > 60000 ? 'warning' : 'outline'}>
                实际 {formatMs(actualDuration)} / 目标 {formatMs(targetDurationMs)}
              </Badge>
            )}
            {canManage && currentVersion && (
              <Button size="sm" variant="ghost" onClick={() => setMarkerDialog({
                startMs: Math.round(playerRef.current?.getCurrentTimeMs() || 0), endMs: null,
              })}>
                在当前位置添加点标记
              </Button>
            )}
          </div>

          {audio ? (
            <AudioPlayer
              ref={playerRef}
              audioUrl={audio.url}
              peaks={audio.peaks}
              durationMs={audio.durationMs}
              markers={markers}
              onRangeSelected={(startMs, endMs) => setMarkerDialog({ startMs, endMs })}
            />
          ) : (
            <p className="rounded-md border border-dashed p-8 text-center text-sm text-muted-foreground">
              {versions.length === 0 ? '尚未上传音频' : '该版本已归档，仅支持下载'}
            </p>
          )}
          <div className="flex gap-2">
            {audio && (
              <Button size="sm" onClick={() => playerRef.current?.playPause()}>播放 / 暂停</Button>
            )}
            {currentVersion?.status === 'ARCHIVED' && (
              <a href={`/api/audio/${currentVersion.id}/download`}>
                <Button size="sm" variant="outline">下载归档版本</Button>
              </a>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 标记列表 */}
      <Card>
        <CardHeader>
          <CardTitle>时间轴标记（{markers.length}）</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <Select className="w-36" value={filters.type}
              onChange={(e) => setFilters({ ...filters, type: e.target.value })}>
              <option value="">全部类型</option>
              {Object.entries(MARKER_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
            <Select className="w-32" value={filters.status}
              onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
              <option value="">全部状态</option>
              {Object.entries(MARKER_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
            <Input className="w-48" placeholder="搜索标记描述" value={filters.keyword}
              onChange={(e) => setFilters({ ...filters, keyword: e.target.value })} />
          </div>
          <ul className="space-y-2">
            {markers.map((m) => (
              <li key={m.id} className="rounded-md border p-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <button className="font-mono text-primary hover:underline"
                    onClick={() => playerRef.current?.seekMs(m.startTimeMs)}>
                    {formatMs(m.startTimeMs)}{m.endTimeMs != null ? ` - ${formatMs(m.endTimeMs)}` : ''}
                  </button>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{MARKER_TYPES[m.type]}</Badge>
                    <Badge variant={STATUS_BADGE[m.status]}>{MARKER_STATUS[m.status]}</Badge>
                  </div>
                </div>
                <p className="mt-1">{m.description}</p>
                <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs text-muted-foreground">{m.creatorName} · {m.createdAt?.slice(0, 16).replace('T', ' ')}</span>
                  <div className="flex gap-1">
                    {m.status !== 'IN_PROGRESS' && m.status !== 'RESOLVED' && (
                      <Button size="sm" variant="ghost" onClick={() => setMarkerStatus(m.id, 'IN_PROGRESS')}>处理中</Button>
                    )}
                    {m.status !== 'RESOLVED' && (
                      <Button size="sm" variant="ghost" onClick={() => setMarkerStatus(m.id, 'RESOLVED')}>已解决</Button>
                    )}
                    {m.status === 'PENDING' && (
                      <Button size="sm" variant="ghost" onClick={() => setMarkerStatus(m.id, 'IGNORED')}>忽略</Button>
                    )}
                    {(canManage || m.createdBy === user?.id) && (
                      <Button size="sm" variant="ghost" className="text-destructive"
                        onClick={() => deleteMarker(m.id)}>删除</Button>
                    )}
                  </div>
                </div>
              </li>
            ))}
            {markers.length === 0 && <p className="text-sm text-muted-foreground">暂无标记</p>}
          </ul>
        </CardContent>
      </Card>

      {/* 转写文本 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>转写文本</span>
            <div className="flex gap-2">
              {whisperEnabled && currentVersion && (
                <Button size="sm" variant="outline" onClick={generateTranscript}>Whisper 生成</Button>
              )}
              {currentVersion && (
                <Button size="sm" variant="outline" onClick={() => setTranscriptImport(true)}>导入</Button>
              )}
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {transcript.length === 0 ? (
            <p className="text-sm text-muted-foreground">暂无转写文本</p>
          ) : (
            <ul className="space-y-1">
              {transcript.map((seg) => (
                <li key={seg.id} className="group flex items-start gap-2 rounded-md p-2 text-sm hover:bg-accent">
                  <button className="shrink-0 font-mono text-xs text-primary hover:underline"
                    onClick={() => playerRef.current?.seekMs(seg.startTimeMs)}>
                    {formatMs(seg.startTimeMs)}
                  </button>
                  {seg.speaker && (
                    <span className="shrink-0 rounded bg-blue-100 px-1.5 text-xs text-blue-800">{seg.speaker}</span>
                  )}
                  <span className="flex-1">{seg.text}{seg.edited && <span className="text-xs text-muted-foreground">（已修正）</span>}</span>
                  <span className="hidden shrink-0 gap-1 group-hover:flex">
                    <Button size="sm" variant="ghost" onClick={() => setEditSeg({ ...seg })}>修正</Button>
                    <Button size="sm" variant="ghost"
                      onClick={() => setMarkerDialog({ startMs: seg.startTimeMs, endMs: seg.endTimeMs })}>
                      加标记
                    </Button>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {/* 任务看板 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>任务看板</span>
            {canManage && <Button size="sm" onClick={() => setTaskForm({ description: '', assigneeId: '', dueDate: '' })}>分配任务</Button>}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="space-y-2">
            {tasks.map((t) => {
              const assignee = members.find((m) => m.userId === t.assigneeId)
              return (
                <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3 text-sm">
                  <div>
                    <p>{t.description}</p>
                    <p className="text-xs text-muted-foreground">
                      {assignee ? `负责人：${assignee.name}` : '未分配'}
                      {t.dueDate ? ` · 截止 ${t.dueDate}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={t.status === 'DONE' ? 'success' : t.status === 'IN_PROGRESS' ? 'default' : 'secondary'}>
                      {t.status === 'DONE' ? '已完成' : t.status === 'IN_PROGRESS' ? '进行中' : '待处理'}
                    </Badge>
                    {(canManage || t.assigneeId === user?.id) && t.status !== 'DONE' && (
                      <Button size="sm" variant="outline"
                        onClick={() => setTaskStatus(t.id, t.status === 'TODO' ? 'IN_PROGRESS' : 'DONE')}>
                        {t.status === 'TODO' ? '开始' : '完成'}
                      </Button>
                    )}
                  </div>
                </li>
              )
            })}
            {tasks.length === 0 && <p className="text-sm text-muted-foreground">暂无任务</p>}
          </ul>
        </CardContent>
      </Card>

      {/* 分发 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>平台分发</span>
            {['ADMIN', 'OPERATOR'].includes(role) && (
              <Button size="sm" onClick={() => setDistForm({ platformId: '', platformDataJson: '', scheduledAt: '' })}>
                添加分发
              </Button>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="space-y-2">
            {distributions.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3 text-sm">
                <div>
                  <p className="font-medium">{d.platformName}</p>
                  <p className="text-xs text-muted-foreground">
                    {d.scheduledAt ? `排期 ${d.scheduledAt.slice(0, 16).replace('T', ' ')}` : '未排期'}
                    {d.submittedAt ? ` · 提交于 ${d.submittedAt.slice(0, 10)}` : ''}
                    {d.publishedAt ? ` · 上线于 ${d.publishedAt.slice(0, 10)}` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={d.status === 'LIVE' ? 'success' : d.status === 'REJECTED' ? 'destructive' : 'secondary'}>
                    {DISTRIBUTION_STATUS[d.status]}
                  </Badge>
                  {['ADMIN', 'OPERATOR'].includes(role) && (
                    <Select className="w-28" value={d.status}
                      onChange={(e) => setDistStatus(d.id, e.target.value)}>
                      {Object.entries(DISTRIBUTION_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </Select>
                  )}
                </div>
              </li>
            ))}
            {distributions.length === 0 && <p className="text-sm text-muted-foreground">暂无分发任务</p>}
          </ul>
        </CardContent>
      </Card>

      {/* 标记创建对话框 */}
      <Dialog open={!!markerDialog} onClose={() => setMarkerDialog(null)}
        title={markerDialog?.endMs != null
          ? `添加时间段标记 ${formatMs(markerDialog.startMs)} - ${formatMs(markerDialog.endMs)}`
          : `添加点标记 ${formatMs(markerDialog?.startMs)}`}>
        <form onSubmit={createMarker} className="space-y-4">
          <div className="space-y-2">
            <Label>标记类型</Label>
            <Select value={markerForm.type}
              onChange={(e) => setMarkerForm({ ...markerForm, type: e.target.value })}>
              {Object.entries(MARKER_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
          </div>
          <div className="space-y-2">
            <Label>描述</Label>
            <Textarea value={markerForm.description} required
              placeholder="描述问题，如：此处口误需删除"
              onChange={(e) => setMarkerForm({ ...markerForm, description: e.target.value })} />
          </div>
          <Button className="w-full">保存标记</Button>
        </form>
      </Dialog>

      {/* 任务创建对话框 */}
      <Dialog open={!!taskForm} onClose={() => setTaskForm(null)} title="分配任务">
        {taskForm && (
          <form onSubmit={createTask} className="space-y-4">
            <div className="space-y-2">
              <Label>任务描述</Label>
              <Textarea value={taskForm.description} required
                onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>负责人</Label>
              <Select value={taskForm.assigneeId}
                onChange={(e) => setTaskForm({ ...taskForm, assigneeId: e.target.value })}>
                <option value="">未分配</option>
                {members.map((m) => <option key={m.userId} value={m.userId}>{m.name}</option>)}
              </Select>
            </div>
            <div className="space-y-2">
              <Label>截止日期</Label>
              <Input type="date" value={taskForm.dueDate}
                onChange={(e) => setTaskForm({ ...taskForm, dueDate: e.target.value })} />
            </div>
            <Button className="w-full">创建任务</Button>
          </form>
        )}
      </Dialog>

      {/* 转写导入对话框 */}
      <Dialog open={transcriptImport} onClose={() => setTranscriptImport(false)} title="导入转写文本">
        <div className="space-y-4">
          <p className="text-xs text-muted-foreground">
            JSON 格式：[{'{'}"startTimeMs":0,"endTimeMs":2500,"text":"…","speaker":"主播A"{'}'}]
          </p>
          <Textarea rows={8} value={importText} onChange={(e) => setImportText(e.target.value)} />
          <Button className="w-full" onClick={importTranscript}>导入</Button>
        </div>
      </Dialog>

      {/* 转写修正对话框 */}
      <Dialog open={!!editSeg} onClose={() => setEditSeg(null)} title="修正转写">
        {editSeg && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-2">
                <Label>开始(ms)</Label>
                <Input type="number" value={editSeg.startTimeMs}
                  onChange={(e) => setEditSeg({ ...editSeg, startTimeMs: parseInt(e.target.value, 10) })} />
              </div>
              <div className="space-y-2">
                <Label>结束(ms)</Label>
                <Input type="number" value={editSeg.endTimeMs}
                  onChange={(e) => setEditSeg({ ...editSeg, endTimeMs: parseInt(e.target.value, 10) })} />
              </div>
            </div>
            <Textarea rows={4} value={editSeg.text}
              onChange={(e) => setEditSeg({ ...editSeg, text: e.target.value })} />
            <Button className="w-full" onClick={saveSegment}>保存</Button>
          </div>
        )}
      </Dialog>

      {/* 分发创建对话框 */}
      <Dialog open={!!distForm} onClose={() => setDistForm(null)} title="添加分发平台">
        {distForm && (
          <form onSubmit={createDistribution} className="space-y-4">
            <div className="space-y-2">
              <Label>平台</Label>
              <Select value={distForm.platformId} required
                onChange={(e) => setDistForm({ ...distForm, platformId: e.target.value })}>
                <option value="">选择平台</option>
                {platforms.filter((p) => !distributions.some((d) => d.platformId === p.id))
                  .map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </Select>
            </div>
            <div className="space-y-2">
              <Label>平台专属信息（JSON，如 shownotes 格式、分类标签）</Label>
              <Textarea value={distForm.platformDataJson} placeholder='{"category": "Technology"}'
                onChange={(e) => setDistForm({ ...distForm, platformDataJson: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>排期发布时间</Label>
              <Input type="datetime-local" value={distForm.scheduledAt}
                onChange={(e) => setDistForm({ ...distForm, scheduledAt: e.target.value })} />
            </div>
            <Button className="w-full">创建</Button>
          </form>
        )}
      </Dialog>
    </div>
  )
}
