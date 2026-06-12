/**
 * AI漫剧制作平台 - 全局类型定义
 * 对应设计文档第五章：28张数据表 (5.1~5.28)
 */

// ============================================================
// 基础类型
// ============================================================

/** 统一API响应 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页响应 */
export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// ============================================================
// 流水线阶段 (pipeline_stage enum)
// ============================================================

export type PipelineStage =
  | 'SCRIPT'       // 剧本模块完成
  | 'CHARACTER'    // 角色模块完成
  | 'SCENE'        // 场景模块完成
  | 'STORYBOARD'   // 分镜模块完成
  | 'DIRECTOR'     // 导演模块完成
  | 'OUTPUT'       // S级/输出模块完成

// ============================================================
// 5.1 项目表 project
// ============================================================

export interface Project {
  id?: number
  name: string
  description: string
  style: StyleType          // SHORT_DRAMA / COMIC / TRAILER
  pipelineStage: PipelineStage
  currentEpisodeId?: number
  createdAt: string
  updatedAt: string
}

export type StyleType = 'SHORT_DRAMA' | 'COMIC' | 'TRAILER'

// ============================================================
// 5.x 项目模板表 project_template
// ============================================================

export interface ProjectTemplate {
  id?: number
  name: string
  description: string
  style: StyleType | 'CUSTOM'
  templateData: string
  isBuiltin: boolean
  useCount: number
  updatedAt: string
}

// ============================================================
// 5.2 小说表 novel
// ============================================================

export interface Novel {
  id?: number
  projectId: number
  title: string
  author: string
  filePath: string
  totalChapters: number
  status: NovelStatus        // IMPORTING / SUMMARIZING / CONVERTING / COMPLETED / ERROR
  errorMessage?: string
  importedAt: string
  completedAt?: string
}

export type NovelStatus = 'IMPORTING' | 'SUMMARIZING' | 'CONVERTING' | 'COMPLETED' | 'ERROR'

// ============================================================
// 5.3 章节摘要表 chapter_summary
// ============================================================

export interface ChapterSummary {
  id?: number
  novelId: number
  chapterIndex: number
  chapterTitle: string
  summaryText: string         // JSON string of structured summary (A.8.2 output)
  status: SummaryStatus
  createdAt: string
}

export type SummaryStatus = 'PENDING' | 'GENERATING' | 'COMPLETED' | 'ERROR'

// ============================================================
// 5.4 剧本表 script
// ============================================================

export interface Script {
  id?: number
  projectId: number
  title: string
  outline: string            // 大纲内容 (A.5 output)
  currentStep: ScriptStep    // OUTLINE / EPISODES / DRAFT / REFINED
  totalEpisodes: number
  status: ScriptStatus
  createdAt: string
  updatedAt: string
}

export type ScriptStep = 'OUTLINE' | 'EPISODES' | 'DRAFT' | 'REFINED'
export type ScriptStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'ERROR'

// ============================================================
// 5.5 剧集表 episode
// ============================================================

export interface Episode {
  id?: number
  scriptId: number
  episodeNumber: number
  title: string
  scriptContent: string      // 剧本正文
  parsedData?: string        // 结构化JSON (A.8.1 output)
  status: EpisodeStatus
  createdAt: string
  updatedAt: string
}

export type EpisodeStatus = 'DRAFT' | 'PARSED' | 'READY' | 'ERROR'

// ============================================================
// 5.6 角色表 character
// ============================================================

export interface Character {
  id?: number
  projectId: number
  name: string
  role: CharacterRole        // PROTAGONIST / ANTAGONIST / SUPPORTING / EXTRA
  gender: Gender
  ageRange: string           // e.g., "20-30岁"
  appearance: string         // 外貌描述 (6层锚点中的外观层)
  personality: string        // 性格描述
  anchorPrompt: string       // 完整6层身份锚点拼装后的提示词
  referenceImageId?: number  // 定妆图ID
  extractedFromScript: boolean
  confirmedAt?: string
  createdAt: string
  updatedAt: string
}

export type CharacterRole = 'PROTAGONIST' | 'ANTAGONIST' | 'SUPPORTING' | 'EXTRA'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER'

// ============================================================
// 5.7 角色特征锚点表 character_anchor
// ============================================================

export interface CharacterAnchor {
  id?: number
  characterId: number
  layer: AnchorLayer         // IDENTITY / APPEARANCE / COSTUME / ACCESSORY / POSE / EXPRESSION
  description: string
  orderIndex: number
  createdAt: string
}

export type AnchorLayer =
  | 'IDENTITY'     // 第一层：身份定位
  | 'APPEARANCE'   // 第二层：外貌特征
  | 'COSTUME'      // 第三层：服装风格
  | 'ACCESSORY'    // 第四层：道具配饰
  | 'POSE'         // 第五层：姿态动作习惯
  | 'EXPRESSION'   // 第六层：表情神态

// ============================================================
// 5.8 场景表 scene
// ============================================================

export interface Scene {
  id?: number
  projectId: number
  name: string
  description: string
  timeOfDay: TimeOfDay       // MORNING / NOON / AFTERNOON / EVENING / NIGHT / DAWN
  weather?: Weather
  styleHint: string           // 风格关键词
  frontViewUrl?: string      // 四视图URLs
  backViewUrl?: string
  leftViewUrl?: string
  rightViewUrl?: string
  confirmedAt?: string
  createdAt: string
  updatedAt: string
}

export type TimeOfDay = 'MORNING' | 'NOON' | 'AFTERNOON' | 'EVENING' | 'NIGHT' | 'DAWN'
export type Weather = 'SUNNY' | 'CLOUDY' | 'RAINY' | 'SNOWY' | 'FOGGY'

// ============================================================
// 5.9 分镜表 storyboard
// ============================================================

export interface Storyboard {
  id?: number
  episodeId: number
  sequence: number
  timeRange: string          // "0-4s"
  continuity: string         // 承接上镜描述
  dialogue?: string          // [角色名, 情绪]:"台词"
  action?: string            // 动作描述
  emotion?: string           // 情绪标签
  shotSize: ShotSize         // 特写/中景/远景 etc.
  cameraAngle: CameraAngle   // 机位角度
  cameraMovement: CameraMovement // 运镜方式
  involvedCharacters: string // JSON array of character names
  involvedCharacterIds?: string // JSON array of character IDs (角色定妆图引用)
  involvedSceneName: string
  involvedSceneId?: number   // 场景ID (场景图引用)
  referenceImageUrls?: string // JSON array of collected reference image URLs
  bgSound?: string
  generatedImageUrl?: string // 生成的分镜图URL
  generatedVideoUrl?: string // 生成的视频URL
  generationPurpose: GenerationPurpose
  status: StoryboardStatus
  createdAt: string
  updatedAt: string
}

export type ShotSize = 'EXTREME_CLOSE_UP' | 'CLOSE_UP' | 'MEDIUM_CLOSE_UP' | 'MEDIUM' | 'MEDIUM_WIDE' | 'WIDE' | 'EXTREME_WIDE'
export type CameraAngle = 'EYE_LEVEL' | 'HIGH_ANGLE' | 'LOW_ANGLE' | 'BIRD_EYE' | 'DUTCH_ANGLE'
export type CameraMovement = 'STATIC' | 'PAN_LEFT' | 'PAN_RIGHT' | 'TILT_UP' | 'TILT_DOWN' | 'ZOOM_IN' | 'ZOOM_OUT' | 'TRACKING' | 'CRANE' | 'HANDHELD'
export type GenerationPurpose = 'CHARACTER_MAKEUP' | 'SCENE_VIEW' | 'SCENE_QUAD_VIEW' | 'STORYBOARD_IMAGE' | 'STORYBOARD_VIDEO'
export type StoryboardStatus = 'PENDING' | 'IMAGE_GENERATING' | 'IMAGE_DONE' | 'VIDEO_GENERATING' | 'VIDEO_DONE' | 'ERROR'

// ============================================================
// 5.10~5.28 其他核心实体（精简定义）
// ============================================================

/** 生成任务表 generation_task */
export interface GenerationTask {
  id?: number
  purpose: GenerationPurpose
  targetId: number
  targetType: string          // CHARACTER / SCENE / STORYBOARD
  modelProviderId: number
  status: TaskStatus
  progress: number            // 0-100
  resultImageUrl?: string
  resultVideoUrl?: string
  errorMessage?: string
  startedAt?: string
  completedAt?: string
  createdAt: string
}

export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

/** 模型配置表 model_config */
export interface ModelConfig {
  id?: number
  name: string
  provider: ModelProvider
  type: ModelType             // TEXT / IMAGE / VIDEO / AUDIO
  apiUrl: string
  apiKey: string              // encrypted
  modelName: string
  maxTokens?: number
  isActive: boolean
  priority: number
  createdAt: string
  updatedAt: string
}

export type ModelProvider = 'OPENAI' | 'ANTHROPIC' | 'QWEN' | 'ERNIE' | 'DALL_E' | 'MIDJOURNEY' | 'SD' | 'RUNWAY' | 'PIKA' | 'COZE' | 'TTS_OPENAI' | 'TTS_QWEN' | 'VOLCENGINE'
export type ModelType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO'

/** 扣子工作流配置表 coze_workflow_config */
export interface CozeWorkflowConfig {
  id?: number
  name: string
  workflowId: string
  inputMapping: string        // JSON
  outputField: string
  botId: string
  appId: string
  purpose: CozePurpose
  createdAt: string
  updatedAt: string
}

export type CozePurpose = 'SCRIPT_TO_STORYBOARD' | 'CHARACTER_MAKEUP' | 'SCENE_VIEW_GENERATION' | 'OTHER'

/** 提示词模板表 prompt_template */
export interface PromptTemplate {
  id?: number
  name: string
  category: TemplateCategory  // SCRIPT / CHARACTER / SCENE / STORYBOARD / SYSTEM
  content: string             // prompt template with placeholders
  variables: string           // JSON array of variable names
  version: number
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export type TemplateCategory = 'SCRIPT' | 'CHARACTER' | 'SCENE' | 'STORYBOARD' | 'SYSTEM'

/** 应用全局配置表 app_config */
export interface AppConfig {
  id?: number
  key: string
  value: string
  description: string
  updatedAt: string
}

/** 流水线状态表 pipeline_state */
export interface PipelineState {
  id?: number
  projectId: number
  scriptDirty: boolean
  characterDirty: boolean
  sceneDirty: boolean
  storyboardDirty: boolean
  directorDirty: boolean
  sLevelDirty: boolean
  currentStage: PipelineStage
  updatedAt: string
}

/** 素材资产表 asset */
export interface AssetItem {
  id?: number
  projectId: number
  name: string
  type: AssetType            // IMAGE / VIDEO / AUDIO / DOCUMENT
  filePath: string
  fileSize: number
  mimeType: string
  tags: string               // JSON array
  source: AssetSource         // UPLOAD / GENERATED / EXTRACTED
  refCharacterId?: number
  refSceneId?: number
  createdAt: string
}

export type AssetType = 'IMAGE' | 'VIDEO' | 'AUDIO' | 'DOCUMENT'
export type AssetSource = 'UPLOAD' | 'GENERATED' | 'EXTRACTED'

/** 待确认资产表 extracted_asset */
export interface ExtractedAsset {
  id?: number
  projectId: number
  type: ExtractedAssetType   // CHARACTER / SCENE / PROP
  name: string
  description: string
  sourceText: string          // 原文引用
  suggestedImagePrompt: string
  status: ExtractedStatus     // PENDING / CONFIRMED / REJECTED
  confirmedRefId?: number     // 确认后关联的 character_id / scene_id
  createdAt: string
}

export type ExtractedAssetType = 'CHARACTER' | 'SCENE' | 'PROP'
export type ExtractedStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

/** 版本历史表 version_history */
export interface VersionHistory {
  id?: number
  objectType: string         // SCRIPT / EPISODE / CHARACTER / SCENE / STORYBOARD
  objectId: number
  versionNumber: number
  snapshotData: string        // JSON snapshot
  changeNote: string
  createdBy: string
  createdAt: string
}

// ============================================================
// S级模块请求类型
// ============================================================

/** 成片合成请求 */
export interface CompositeRequest {
  episodeId: number
  addSubtitles?: boolean
  mixAudio?: boolean
  transitionType?: string      // fade / slideleft / slideup / zoom
  transitionDuration?: number  // 转场时长(秒)
}

/** 导出配置 */
export interface ExportForm {
  format: string               // mp4 / mov / avi
  resolution: string           // 720p / 1080p / 2K / 4K
  bitrate?: number             // kbps
  fps?: number                 // 帧率
}

/** 水印配置 */
export interface WatermarkForm {
  watermarkType: 'TEXT' | 'IMAGE'
  watermarkContent?: string
  imagePath?: string           // 图片水印路径
  position?: string            // 位置: top-left / top-right / bottom-left / bottom-right / center
  opacity?: number             // 透明度 0-1
  fontSize?: number            // 字体大小
  fontColor?: string           // 字体颜色 (十六进制)
}
