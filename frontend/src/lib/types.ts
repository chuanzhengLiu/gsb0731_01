// Shared domain types mirroring backend DTOs.

export type Role = "ADMIN" | "PRODUCER" | "EDITOR" | "OPERATOR" | "HOST" | "GUEST";

export interface User {
  id: number;
  email: string;
  name: string;
  role: Role;
  teamId: number | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Podcast {
  id: number;
  teamId: number;
  name: string;
  type: string;
  updateFrequency?: string | null;
  targetDurationMs?: number | null;
  structureTemplateJson?: string | null;
  createdAt: string;
}

export type EpisodeStatus =
  | "PLANNING" | "RECORDING" | "ROUGH_CUT" | "FINE_CUT"
  | "REVIEW" | "FINALIZED" | "DISTRIBUTING" | "PUBLISHED";

export interface Episode {
  id: number;
  podcastId: number;
  number: number;
  title: string;
  theme?: string | null;
  recordDate?: string | null;
  status: EpisodeStatus;
  finalAudioUrl?: string | null;
  createdAt: string;
}

export interface AudioVersion {
  id: number;
  episodeId: number;
  versionNumber: number;
  originalName?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  durationMs?: number | null;
  archived: boolean;
  hasWaveform: boolean;
  uploadedBy?: number | null;
  createdAt: string;
  streamUrl: string;
}

export type MarkerType =
  | "MISSPEAK" | "RE_RECORD" | "VOLUME_ISSUE" | "BACKGROUND_MUSIC"
  | "SOUND_EFFECT" | "BAD_TRANSITION" | "FACT_CHECK";

export type MarkerStatus = "PENDING" | "IN_PROGRESS" | "RESOLVED" | "IGNORED";

export interface TimelineMarker {
  id: number;
  audioVersionId: number;
  startTimeMs: number;
  endTimeMs?: number | null;
  type: MarkerType;
  description?: string | null;
  screenshotUrl?: string | null;
  status: MarkerStatus;
  createdBy?: number | null;
  createdAt: string;
}

export const MARKER_TYPE_LABELS: Record<MarkerType, string> = {
  MISSPEAK: "口误",
  RE_RECORD: "补录",
  VOLUME_ISSUE: "音量问题",
  BACKGROUND_MUSIC: "背景音乐",
  SOUND_EFFECT: "音效插入",
  BAD_TRANSITION: "过渡不自然",
  FACT_CHECK: "事实待核实",
};

export const MARKER_STATUS_LABELS: Record<MarkerStatus, string> = {
  PENDING: "待处理",
  IN_PROGRESS: "处理中",
  RESOLVED: "已解决",
  IGNORED: "已忽略",
};

/**
 * Allowed status-flow transitions, mirroring the backend MarkerStatus state
 * machine (README §4.2 标记状态流转). Used to restrict the UI's options.
 */
export const ALLOWED_STATUS_TRANSITIONS: Record<MarkerStatus, MarkerStatus[]> = {
  PENDING: ["IN_PROGRESS", "RESOLVED", "IGNORED"],
  IN_PROGRESS: ["RESOLVED", "IGNORED", "PENDING"],
  RESOLVED: ["IN_PROGRESS", "PENDING"],
  IGNORED: ["PENDING"],
};

export const EPISODE_STATUS_LABELS: Record<EpisodeStatus, string> = {
  PLANNING: "策划",
  RECORDING: "录制",
  ROUGH_CUT: "粗剪",
  FINE_CUT: "精剪",
  REVIEW: "审听",
  FINALIZED: "定稿",
  DISTRIBUTING: "分发",
  PUBLISHED: "已发布",
};

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "团队管理员",
  PRODUCER: "制作人/主编",
  EDITOR: "剪辑师",
  OPERATOR: "运营",
  HOST: "主播/嘉宾",
  GUEST: "访客",
};

export interface Invitation {
  id: number;
  teamId: number;
  email: string;
  roleInTeam: Role;
  accepted: boolean;
  revoked: boolean;
  expired: boolean;
  expiresAt: string;
  createdAt: string;
}

export interface TeamMember {
  id: number;
  email: string;
  name: string;
  role: Role;
}

// ---- Transcript (README §4.3) ----
export interface TranscriptSegment {
  id: number;
  audioVersionId: number;
  segmentIndex: number;
  startTimeMs: number;
  endTimeMs: number;
  text: string;
  speaker?: string | null;
  speakerColor?: string | null;
  edited: boolean;
}

// ---- Task board (README §4.1) ----
export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: "待办",
  IN_PROGRESS: "进行中",
  DONE: "已完成",
};

export interface Task {
  id: number;
  episodeId: number;
  assigneeId?: number | null;
  assigneeName?: string | null;
  description: string;
  dueDate?: string | null;
  status: TaskStatus;
  overdue: boolean;
  createdAt: string;
}

// ---- Distribution (README §4.4) ----
export type DistributionStatus =
  | "NOT_STARTED" | "SUBMITTED" | "IN_REVIEW" | "PUBLISHED" | "REJECTED";

export const DISTRIBUTION_STATUS_LABELS: Record<DistributionStatus, string> = {
  NOT_STARTED: "未开始",
  SUBMITTED: "已提交",
  IN_REVIEW: "审核中",
  PUBLISHED: "已上线",
  REJECTED: "被拒绝",
};

export interface Platform {
  id: number;
  name: string;
  rssRequiredFieldsJson?: string | null;
  categoryOptionsJson?: string | null;
}

export interface PlatformAccount {
  id: number;
  platformId: number;
  platformName: string;
  accountName: string;
  accountUrl?: string | null;
  notes?: string | null;
}

export interface Distribution {
  id: number;
  episodeId: number;
  platformId: number;
  platformName: string;
  status: DistributionStatus;
  submittedAt?: string | null;
  publishedAt?: string | null;
  platformDataJson?: string | null;
}

export interface CalendarEntry {
  episodeId: number;
  episodeNumber: number;
  episodeTitle: string;
  podcastId: number;
  podcastName?: string | null;
  date: string;
  status: string;
}

// ---- Assets (README §4.5) ----
export type AssetType = "AUDIO" | "TEXT";

export interface Asset {
  id: number;
  teamId: number;
  name: string;
  type: AssetType;
  category?: string | null;
  sizeBytes?: number | null;
  durationMs?: number | null;
  contentType?: string | null;
  textContent?: string | null;
  usageCount: number;
  previewUrl?: string | null;
  createdAt: string;
}

export interface AssetUsage {
  id: number;
  assetId: number;
  episodeId: number;
  episodeNumber?: number | null;
  episodeTitle?: string | null;
  positionMs?: number | null;
  note?: string | null;
  createdAt: string;
}

// ---- Structure template (README §4.1) ----
export interface TemplateSection {
  name: string;
  targetDurationMs?: number | null;
}

export interface StructureComparison {
  episodeId: number;
  hasTemplate: boolean;
  templateTotalMs?: number | null;
  actualDurationMs?: number | null;
  diffMs?: number | null;
  sections: TemplateSection[];
}

// ---- Stats (README §4.6) ----
export interface EpisodeStat {
  episodeId: number;
  episodeNumber: number;
  episodeTitle: string;
  podcastName?: string | null;
  durationMs?: number | null;
  markerCount: number;
  versionCount: number;
  cycleDays?: number | null;
}

export interface MemberEfficiency {
  userId: number;
  name: string;
  role: Role;
  markersRaised: number;
  openTasks: number;
  overdueTasks: number;
}

export interface PlatformCoverage {
  platformId: number;
  platformName: string;
  total: number;
  published: number;
  publishRate: number;
  avgReviewHours?: number | null;
}

export interface TeamStats {
  episodes: EpisodeStat[];
  members: MemberEfficiency[];
  platforms: PlatformCoverage[];
  avgMarkersPerEpisode: number;
  totalOverdueTasks: number;
}

// ---- Share links (README §3.1/§8) ----
export interface ShareLink {
  id: number;
  episodeId: number;
  url: string;
  expiresAt: string;
  revoked: boolean;
  expired: boolean;
  accessCount: number;
  lastAccessedAt?: string | null;
  createdAt: string;
}

export interface SharedEpisodeView {
  episodeId: number;
  number: number;
  title: string;
  theme?: string | null;
  status: string;
  podcastName?: string | null;
  audio?: {
    audioVersionId: number;
    versionNumber: number;
    durationMs?: number | null;
    streamUrl: string;
  } | null;
  markers: {
    id: number;
    startTimeMs: number;
    endTimeMs?: number | null;
    type: string;
    description?: string | null;
    status: string;
  }[];
  transcript: {
    id: number;
    startTimeMs: number;
    endTimeMs: number;
    text: string;
    speaker?: string | null;
    speakerColor?: string | null;
  }[];
}
