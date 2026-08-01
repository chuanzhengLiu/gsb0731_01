export type TeamRole = 'ADMIN' | 'PRODUCER' | 'EDITOR' | 'OPERATOR' | 'HOST' | 'GUEST'

export type PodcastType = 'INTERVIEW' | 'NARRATIVE' | 'KNOWLEDGE' | 'NEWS'

export type EpisodeStatus =
  | 'PLANNING'
  | 'RECORDING'
  | 'ROUGH_CUT'
  | 'FINE_CUT'
  | 'REVIEW'
  | 'FINALIZED'
  | 'DISTRIBUTING'
  | 'PUBLISHED'

export type MarkerType =
  | 'SLIP'
  | 'RE_RECORD'
  | 'VOLUME'
  | 'BACKGROUND_MUSIC'
  | 'SFX'
  | 'TRANSITION'
  | 'FACT_CHECK'

export type MarkerStatus = 'PENDING' | 'IN_PROGRESS' | 'RESOLVED' | 'IGNORED'

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'

export type DistributionStatus =
  | 'NOT_STARTED'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'

export interface Team {
  id: number
  name: string
  createdBy: number
  createdAt: string
  myRole: TeamRole
}

export interface Member {
  userId: number
  email: string
  name: string
  role: TeamRole
  joinedAt: string
}

export interface Podcast {
  id: number
  name: string
  type: PodcastType
  updateFrequency?: string
  targetDurationSeconds?: number
  structureTemplateJson?: string
  createdAt: string
}

export interface Episode {
  id: number
  podcastId: number
  number: number
  title: string
  theme?: string
  recordDate?: string
  status: EpisodeStatus
  finalAudioUrl?: string
  scheduledAt?: string
  createdAt: string
  updatedAt: string
}

export interface AudioVersion {
  id: number
  episodeId: number
  versionNumber: number
  fileUrl: string | null
  fileName: string
  mimeType: string
  fileSize: number
  durationMs: number
  peaksUrl?: string | null
  archived: boolean
  uploadedBy: number
  uploaderName: string
  downloadUrl: string
  createdAt: string
}

export interface Marker {
  id: number
  audioVersionId: number
  startTimeMs: number
  endTimeMs?: number
  type: MarkerType
  description: string
  status: MarkerStatus
  screenshotUrl?: string
  createdBy: number
  creatorName: string
  resolvedBy?: number
  resolvedAt?: string
  createdAt: string
}

export interface TranscriptSegment {
  id?: number
  audioVersionId?: number
  startTimeMs: number
  endTimeMs: number
  text: string
  speaker?: string
  seq?: number
}

export interface Task {
  id: number
  episodeId: number
  assigneeId?: number
  assigneeName?: string
  title: string
  description?: string
  dueDate?: string
  status: TaskStatus
  createdAt: string
}

export interface Platform {
  id: number
  name: string
  code: string
  rssRequiredFieldsJson?: string
  categoryOptionsJson?: string
}

export interface PlatformAccount {
  id: number
  platformId: number
  platformName: string
  displayName: string
  createdAt: string
}

export interface Distribution {
  id: number
  episodeId: number
  platformAccountId: number
  platformName: string
  accountDisplayName: string
  status: DistributionStatus
  submittedAt?: string
  publishedAt?: string
  platformDataJson?: string
  rejectionReason?: string
  updatedAt: string
}

export interface Asset {
  id: number
  name: string
  type: 'AUDIO' | 'TEXT'
  fileUrl?: string
  content?: string
  usageCount: number
  createdAt: string
}

export const EPISODE_STATUS_LABELS: Record<EpisodeStatus, string> = {
  PLANNING: '策划',
  RECORDING: '录制',
  ROUGH_CUT: '粗剪',
  FINE_CUT: '精剪',
  REVIEW: '审听',
  FINALIZED: '定稿',
  DISTRIBUTING: '分发中',
  PUBLISHED: '已发布',
}

export const MARKER_TYPE_LABELS: Record<MarkerType, string> = {
  SLIP: '口误',
  RE_RECORD: '补录',
  VOLUME: '音量问题',
  BACKGROUND_MUSIC: '背景音乐',
  SFX: '音效插入',
  TRANSITION: '过渡不自然',
  FACT_CHECK: '事实待核实',
}

export const MARKER_STATUS_LABELS: Record<MarkerStatus, string> = {
  PENDING: '待处理',
  IN_PROGRESS: '处理中',
  RESOLVED: '已解决',
  IGNORED: '已忽略',
}

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: '待办',
  IN_PROGRESS: '进行中',
  DONE: '完成',
  CANCELLED: '已取消',
}

export const DISTRIBUTION_STATUS_LABELS: Record<DistributionStatus, string> = {
  NOT_STARTED: '未开始',
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审核中',
  PUBLISHED: '已上线',
  REJECTED: '被拒绝',
}

export const TEAM_ROLE_LABELS: Record<TeamRole, string> = {
  ADMIN: '团队管理员',
  PRODUCER: '制作人/主编',
  EDITOR: '剪辑师',
  OPERATOR: '运营',
  HOST: '主播',
  GUEST: '嘉宾',
}
