/**
 * 共享标签映射常量
 * 消除 StoryboardView / DirectorView 等组件中重复定义的枚举→中文标签映射
 */

// ============================================================
// 分镜状态
// ============================================================
export const STORYBOARD_STATUS_LABELS: Record<string, string> = {
  PENDING: '待生成',
  IMAGE_GENERATING: '图片生成中',
  IMAGE_DONE: '图片已生成',
  VIDEO_GENERATING: '视频生成中',
  VIDEO_DONE: '视频已生成',
  ERROR: '失败',
}

export const STORYBOARD_STATUS_TAG_TYPE: Record<string, string> = {
  PENDING: 'info',
  IMAGE_GENERATING: 'warning',
  IMAGE_DONE: 'info',
  VIDEO_GENERATING: 'warning',
  VIDEO_DONE: 'success',
  ERROR: 'danger',
}

// ============================================================
// 景别
// ============================================================
export const SHOT_SIZE_LABELS: Record<string, string> = {
  EXTREME_CLOSE_UP: '特写',
  CLOSE_UP: '近景',
  MEDIUM_CLOSE_UP: '中近景',
  MEDIUM: '中景',
  MEDIUM_WIDE: '中远景',
  WIDE: '远景',
  EXTREME_WIDE: '大远景',
}

// ============================================================
// 机位角度
// ============================================================
export const CAMERA_ANGLE_LABELS: Record<string, string> = {
  EYE_LEVEL: '平视',
  HIGH_ANGLE: '俯视',
  LOW_ANGLE: '仰视',
  BIRD_EYE: '鸟瞰',
  DUTCH_ANGLE: '倾斜',
}

// ============================================================
// 运镜方式
// ============================================================
export const CAMERA_MOVEMENT_LABELS: Record<string, string> = {
  STATIC: '静止',
  PAN_LEFT: '左摇',
  PAN_RIGHT: '右摇',
  TILT_UP: '上摇',
  TILT_DOWN: '下摇',
  ZOOM_IN: '推近',
  ZOOM_OUT: '拉远',
  TRACKING: '跟踪',
  CRANE: '升降',
  HANDHELD: '手持',
}

// ============================================================
// 任务状态
// ============================================================
export const TASK_STATUS_LABELS: Record<string, string> = {
  PENDING: '等待中',
  RUNNING: '执行中',
  SUCCESS: '完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

export const TASK_STATUS_TAG_TYPE: Record<string, string> = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLED: 'info',
}

// ============================================================
// 任务优先级
// ============================================================
export const PRIORITY_TAG_TYPE: Record<string, string> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'info',
}

// ============================================================
// 辅助函数（安全取值，支持 undefined）
// ============================================================
export function getStoryboardStatusLabel(status: string | undefined): string {
  if (!status) return '未知'
  return STORYBOARD_STATUS_LABELS[status] || status
}

export function getStoryboardStatusTagType(status: string | undefined): string {
  if (!status) return 'info'
  return STORYBOARD_STATUS_TAG_TYPE[status] || 'info'
}

export function getShotSizeLabel(ss: string | undefined): string {
  if (!ss) return '未知'
  return SHOT_SIZE_LABELS[ss] || ss
}

export function getCameraAngleLabel(ca: string | undefined): string {
  if (!ca) return '未知'
  return CAMERA_ANGLE_LABELS[ca] || ca
}

export function getCameraMovementLabel(cm: string | undefined): string {
  if (!cm) return '未知'
  return CAMERA_MOVEMENT_LABELS[cm] || cm
}

export function getTaskStatusLabel(status: string): string {
  return TASK_STATUS_LABELS[status] || status
}

export function getTaskStatusTagType(status: string): string {
  return TASK_STATUS_TAG_TYPE[status] || 'info'
}

export function getPriorityTagType(priority: string): string {
  return PRIORITY_TAG_TYPE[priority] || 'info'
}

// ============================================================
// JSON 安全解析辅助
// ============================================================
export function parseJsonArray<T = unknown>(jsonStr: string | undefined): T[] {
  if (!jsonStr) return []
  try {
    const parsed = JSON.parse(jsonStr)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function parseNumberArray(jsonStr: string | undefined): number[] {
  return parseJsonArray<number>(jsonStr)
}

export function parseStringArray(jsonStr: string | undefined): string[] {
  return parseJsonArray<string>(jsonStr)
}
