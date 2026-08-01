import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs) {
  return twMerge(clsx(inputs))
}

export function formatMs(ms) {
  if (ms == null) return '--:--'
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

export const MARKER_TYPES = {
  MISSPEAK: '口误',
  RERECORD: '补录',
  VOLUME: '音量问题',
  BGM: '背景音乐',
  SFX: '音效插入',
  TRANSITION: '过渡不自然',
  FACT_CHECK: '事实待核实',
}

export const MARKER_STATUS = {
  PENDING: '待处理',
  IN_PROGRESS: '处理中',
  RESOLVED: '已解决',
  IGNORED: '已忽略',
}

export const EPISODE_STATUS = {
  PLANNING: '策划',
  RECORDED: '录制',
  ROUGH_CUT: '粗剪',
  FINE_CUT: '精剪',
  REVIEW: '审听',
  FINALIZED: '定稿',
  DISTRIBUTING: '分发',
  PUBLISHED: '已发布',
}

export const DISTRIBUTION_STATUS = {
  NOT_STARTED: '未开始',
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审核中',
  LIVE: '已上线',
  REJECTED: '被拒绝',
}

export const ROLES = {
  ADMIN: '团队管理员',
  PRODUCER: '制作人/主编',
  EDITOR: '剪辑师',
  OPERATOR: '运营',
  HOST: '主播/嘉宾',
}

export const PODCAST_TYPES = {
  INTERVIEW: '访谈',
  NARRATIVE: '叙事',
  KNOWLEDGE: '知识',
  NEWS: '新闻',
}
