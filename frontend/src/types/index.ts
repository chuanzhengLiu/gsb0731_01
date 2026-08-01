export interface User {
  id: number
  email: string
  name: string
}

export interface Team {
  id: number
  name: string
  createdBy: number
  createdAt: string
  currentUserRole?: string
}

export interface TeamMember {
  id: number
  userId: number
  email: string
  name: string
  roleInTeam: string
  joinedAt: string
  avatarUrl?: string
}

export interface Podcast {
  id: number
  teamId: number
  name: string
  type: string
  updateFrequency?: string
  targetDuration?: number
  structureTemplateJson?: string
  description?: string
  coverImageUrl?: string
  createdAt: string
  updatedAt: string
}

export interface Episode {
  id: number
  podcastId: number
  number: number
  title: string
  theme?: string
  recordDate?: string
  status: string
  finalAudioUrl?: string
  scheduledAt?: string
  createdBy: number
  createdAt: string
  updatedAt: string
  podcastName?: string
  podcast?: { id: number; name: string }
  createdByName?: string
  markerCount?: number
  taskCount?: number
}

export interface Task {
  id: number
  episodeId: number
  assigneeId?: number
  title: string
  description?: string
  dueDate?: string
  status: string
  createdBy: number
  createdAt: string
  updatedAt: string
  assigneeName?: string
  creatorName?: string
}

export interface AudioVersion {
  id: number
  episodeId: number
  versionNumber: number
  fileUrl: string
  fileName: string
  fileSize?: number
  durationMs: number
  waveformUrl?: string
  uploadedBy: number
  isArchived: boolean
  isFinal?: boolean
  createdAt: string
  uploadedAt?: string
  uploadedByName?: string
}

export interface TimelineMarker {
  id: number
  audioVersionId: number
  startTimeMs: number
  endTimeMs?: number
  type: string
  description?: string
  screenshotUrl?: string
  status: string
  assigneeId?: number
  createdBy: number
  resolvedAt?: string
  createdAt: string
  updatedAt: string
  createdByName?: string
  assigneeName?: string
}

export interface TranscriptSegment {
  id: number
  audioVersionId: number
  startTimeMs: number
  endTimeMs: number
  text: string
  speaker?: string
  segmentOrder: number
}

export interface Distribution {
  id: number
  episodeId: number
  platformId: number
  status: string
  platformDataJson?: string
  submittedAt?: string
  publishedAt?: string
  reviewedAt?: string
  rejectionReason?: string
  createdBy: number
  createdAt: string
  updatedAt: string
  platformName?: string
  platformDisplayName?: string
}

export interface Platform {
  id: number
  name: string
  displayName: string
  rssRequiredFieldsJson?: string
  categoryOptionsJson?: string
  isActive: boolean
}

export interface Asset {
  id: number
  teamId: number
  name: string
  type: string
  category?: string
  fileUrl?: string
  content?: string
  usageCount: number
  createdBy: number
  createdAt: string
  updatedAt: string
  createdByName?: string
}

export interface ShareLink {
  id: number
  episodeId: number
  token: string
  createdBy: number
  expiresAt: string
  lastAccessedAt?: string
  accessCount: number
  isRevoked: boolean
  createdAt: string
  episodeTitle?: string
}

export interface WaveformData {
  duration_ms: number
  durationMs?: number
  samples: number[]
  sample_count: number
  sampleCount?: number
}
