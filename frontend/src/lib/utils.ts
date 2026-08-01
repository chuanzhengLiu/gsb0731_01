import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export const EPISODE_STATUS_LABELS: Record<string, string> = {
  PLANNING: '策划',
  RECORDING: '录制中',
  ROUGH_CUT: '粗剪',
  FINE_CUT: '精剪',
  REVIEW: '审听',
  FINALIZED: '定稿',
  DISTRIBUTING: '分发中',
  PUBLISHED: '已发布',
}

export const MARKER_TYPE_LABELS: Record<string, string> = {
  SLIP: '口误',
  RETAPE: '补录',
  VOLUME: '音量问题',
  BGM: '背景音乐',
  SFX: '音效插入',
  TRANSITION: '过渡不自然',
  FACT_CHECK: '事实待核实',
}

export const MARKER_TYPE_COLORS: Record<string, string> = {
  SLIP: 'bg-red-500',
  RETAPE: 'bg-orange-500',
  VOLUME: 'bg-yellow-500',
  BGM: 'bg-blue-500',
  SFX: 'bg-purple-500',
  TRANSITION: 'bg-pink-500',
  FACT_CHECK: 'bg-cyan-500',
}

export const MARKER_STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理',
  IN_PROGRESS: '处理中',
  RESOLVED: '已解决',
  IGNORED: '已忽略',
}

export const MARKER_STATUS_COLORS: Record<string, string> = {
  PENDING: 'bg-red-100 text-red-800',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-800',
  RESOLVED: 'bg-green-100 text-green-800',
  IGNORED: 'bg-gray-100 text-gray-800',
}

export const TASK_STATUS_LABELS: Record<string, string> = {
  TODO: '待办',
  IN_PROGRESS: '进行中',
  DONE: '已完成',
  CANCELLED: '已取消',
}

export const DISTRIBUTION_STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: '未开始',
  SUBMITTED: '已提交',
  IN_REVIEW: '审核中',
  PUBLISHED: '已上线',
  REJECTED: '被拒绝',
}

export const PODCAST_TYPE_LABELS: Record<string, string> = {
  INTERVIEW: '访谈',
  NARRATIVE: '叙事',
  KNOWLEDGE: '知识',
  NEWS: '新闻',
}

export const TEAM_ROLE_LABELS: Record<string, string> = {
  ADMIN: '团队管理员',
  PRODUCER: '制作人/主编',
  EDITOR: '剪辑师',
  OPERATOR: '运营',
  HOST: '主播',
  GUEST: '嘉宾',
}
