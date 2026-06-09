// API配置相关类型
export type ProviderType =
  | 'OPENAI_CHAT_COMPLETIONS'
  | 'OPENAI_RESPONSES'
  | 'ANTHROPIC_MESSAGES'
  | 'GEMINI_GENERATE_CONTENT'

export interface ApiConfig {
  id: number
  configName: string
  providerType: ProviderType
  providerLabel?: string
  baseUrl: string
  modelName: string
  temperature?: number
  maxTokens?: number
  timeoutSeconds?: number
  modelParametersJson?: string
  inputTokenPricePerMillion?: number
  outputTokenPricePerMillion?: number
  currency?: string
  isDefault: boolean
  hasApiKey?: boolean
  apiKeyPreview?: string
  lastTestStatus?: string
  lastTestErrorCode?: string
  lastTestMessage?: string
  lastTestLatencyMs?: number
  lastTestedAt?: string
  createdAt: string
  updatedAt: string
}

export interface ApiConfigRequest {
  configName: string
  providerType: ProviderType
  providerLabel?: string
  baseUrl: string
  apiKey?: string
  modelName: string
  temperature?: number
  maxTokens?: number
  timeoutSeconds?: number
  modelParametersJson?: string
  inputTokenPricePerMillion?: number
  outputTokenPricePerMillion?: number
  currency?: string
  isDefault?: boolean
}

export interface ProviderModelInfo {
  id: string
  displayName?: string
  ownedBy?: string
}

export interface ProviderModelsFetchRequest {
  providerType: ProviderType
  baseUrl: string
  apiKey: string
  forceRefresh?: boolean
}

export interface ProviderModelsResponse {
  success: boolean
  fromCache: boolean
  models: ProviderModelInfo[]
  latencyMillis: number
}

export interface ProviderTestRequest {
  providerType: ProviderType
  providerLabel?: string
  baseUrl: string
  apiKey: string
  modelName: string
  temperature?: number
  maxTokens?: number
  timeoutSeconds?: number
  modelParametersJson?: string
}

export interface ProviderTestResponse {
  success: boolean
  providerType: ProviderType
  modelName: string
  latencyMillis: number
  message: string
  errorCode?: string
  jsonValid?: boolean
  schemaValid?: boolean
}

export interface ConfigSecurityPolicy {
  allowApiKeyReveal: boolean
}

export type BackgroundJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'

export type EmbeddingProviderType = 'OPENAI_EMBEDDINGS'

export interface EmbeddingConfig {
  id: number
  configName: string
  providerType: EmbeddingProviderType
  baseUrl: string
  modelName: string
  dimensions: number
  timeoutSeconds?: number
  isDefault: boolean
  hasApiKey: boolean
  apiKeyPreview?: string
  lastTestStatus?: string
  lastTestMessage?: string
  lastTestLatencyMs?: number
  lastTestedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface EmbeddingConfigRequest {
  configName: string
  providerType: EmbeddingProviderType
  baseUrl: string
  apiKey?: string
  modelName: string
  dimensions: number
  timeoutSeconds?: number
  isDefault?: boolean
}

export interface EmbeddingTestResponse {
  success: boolean
  providerType: EmbeddingProviderType
  modelName: string
  dimensions: number
  latencyMillis: number
  message: string
  errorCode?: string
}

export interface RagIndexStatus {
  configured: boolean
  ownerUserId?: number
  embeddingConfigId?: number
  embeddingVersion?: string
  indexedChunks?: number
  jobId?: number
  status?: BackgroundJobStatus
  resultJson?: string
  errorCode?: string
  errorMessage?: string
  updatedAt?: string
  message?: string
}

export interface RagFeedbackCitation {
  id?: number
  chunkId?: number
  sourceTitle?: string
  sourceType?: string
  snippet?: string
  rankNo?: number
  reason?: string
}

export interface RagFeedback {
  essayId: number
  feedbackId?: number
  jobId?: number
  status?: BackgroundJobStatus
  resultJson?: string
  errorCode?: string
  errorMessage?: string
  feedbackJson?: string
  citations: RagFeedbackCitation[]
  updatedAt?: string
  message?: string
}

export interface RagFeedbackContent {
  overall: string
  items: Array<{
    title: string
    problem: string
    whyItMatters: string
    howToImprove: string
    example?: { before?: string; after?: string }
    citationIds: number[]
  }>
  nextPractice: string[]
}

// 用户认证相关类型
export interface AuthUser {
  id: number
  username: string
  email: string
  displayName?: string
  role: 'USER' | 'ADMIN' | string
  status: 'ACTIVE' | 'DISABLED' | string
}

export interface AuthResponse {
  authenticated: boolean
  user: AuthUser | null
}

export interface LoginRequest {
  usernameOrEmail: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  displayName?: string
}

export type EssayTypeCode =
  | 'GENERAL'
  | 'JUNIOR_GENERAL'
  | 'JUNIOR_ZHONGKAO'
  | 'SENIOR_GENERAL'
  | 'SENIOR_GAOKAO'
  | 'CET4'
  | 'CET6'
  | 'IELTS_TASK_1'
  | 'IELTS_TASK_2'
  | 'TOEFL_INDEPENDENT'
  | 'TOEFL_INTEGRATED'

export interface EssayTypeOption {
  code: EssayTypeCode
  label: string
  description: string
  wordRange: string
  taskPromptRequired: boolean
  enabled: boolean
  taskPromptPlaceholder?: string
  charLimit: number
}

export const ESSAY_TYPE_OPTIONS: EssayTypeOption[] = [
  { code: 'GENERAL', label: '通用英语作文', description: '适合普通英语作文练习，不绑定具体考试。', wordRange: '建议 80-800 词', taskPromptRequired: false, enabled: true, charLimit: 12000 },
  { code: 'JUNIOR_GENERAL', label: '初中英语作文', description: '面向初中阶段，关注基础准确性和任务完成。', wordRange: '建议 50-120 词', taskPromptRequired: true, enabled: true, charLimit: 3000 },
  { code: 'JUNIOR_ZHONGKAO', label: '中考英语作文', description: '按常见中考 20 分制评分。', wordRange: '建议 80-120 词', taskPromptRequired: true, enabled: true, charLimit: 3000 },
  { code: 'SENIOR_GENERAL', label: '高中英语作文', description: '面向高中阶段，关注内容展开与语言质量。', wordRange: '建议 100-250 词', taskPromptRequired: true, enabled: true, charLimit: 5000 },
  { code: 'SENIOR_GAOKAO', label: '高考英语作文', description: '按常见高考 25 分制评分。', wordRange: '建议 80-150 词', taskPromptRequired: true, enabled: true, charLimit: 5000 },
  { code: 'CET4', label: '大学英语四级作文', description: '按四级作文 15 分制评分。', wordRange: '建议 120-180 词', taskPromptRequired: true, enabled: true, charLimit: 6000 },
  { code: 'CET6', label: '大学英语六级作文', description: '按六级作文 15 分制评分，更强调论证和表达成熟度。', wordRange: '建议 150-220 词', taskPromptRequired: true, enabled: true, charLimit: 6000 },
  { code: 'IELTS_TASK_1', label: '雅思 Task 1 图表作文', description: '按 IELTS Task 1 四项标准评分。请在题目中写清图表/流程/地图关键信息。', wordRange: '建议 150-220 词', taskPromptRequired: true, enabled: true, taskPromptPlaceholder: '请粘贴题目，并补充图表类型、关键数据、趋势或对比信息。', charLimit: 8000 },
  { code: 'IELTS_TASK_2', label: '雅思 Task 2 议论文', description: '按 IELTS Task 2 四项标准评分。', wordRange: '建议 250-350 词', taskPromptRequired: true, enabled: true, charLimit: 8000 },
  { code: 'TOEFL_INDEPENDENT', label: '托福独立写作', description: '按 TOEFL 独立写作 0-5 原生分制评分。', wordRange: '建议 300-450 词', taskPromptRequired: true, enabled: true, charLimit: 8000 },
  { code: 'TOEFL_INTEGRATED', label: '托福综合写作（暂缓开放）', description: '需要阅读材料和听力材料，第一版暂缓开放。', wordRange: '暂缓开放', taskPromptRequired: true, enabled: false, charLimit: 8000 }
]

export interface Essay {
  id: number
  essayType: EssayTypeCode
  essayTypeDisplayName: string
  taskPrompt?: string
  taskPromptSummary?: string
  content: string
  wordCount: number
  charCount: number
  essayGroupId?: number
  versionNo?: number
  parentEssayId?: number
  createdAt: string
}

export interface EssaySubmitRequest {
  content: string
  essayType: EssayTypeCode
  taskPrompt?: string
  configId?: number
  idempotencyKey?: string
  parentEssayId?: number
}

export interface ScoreValue {
  scale: string
  value: number
  max: number
  display: string
}

export interface RubricInfo {
  type: EssayTypeCode
  version: string
  name: string
}

export interface Confidence {
  level: 'HIGH' | 'MEDIUM' | 'LOW' | string
  score: number
  reasons: string[]
  warnings: string[]
}

export interface RubricDimensionScore {
  key: string
  label: string
  score: number
  maxScore: number
  level: string
  reason: string
  evidence: string[]
  improvement: string
}

export interface RubricAnnotation {
  type: string
  severity: string
  quote?: string
  original: string
  context: string
  message: string
  suggestion: string
  explanation: string
}

export interface RubricSummary {
  strengths: string[]
  priorityImprovements: string[]
  overallFeedback: string
}

export interface ReferenceEssay {
  title?: string
  content: string
  notes: string[]
}

export interface InputAnalysis {
  status: 'PASS' | 'WARN' | 'REJECT' | string
  wordCount: number
  charCount: number
  warnings: string[]
  rejections: string[]
}

export interface RubricScoringResult {
  nativeScore: ScoreValue
  normalizedScore: ScoreValue
  rubric: RubricInfo
  gradeLabel: string
  confidence: Confidence
  dimensions: RubricDimensionScore[]
  annotations: RubricAnnotation[]
  summary: RubricSummary
  safetyNotice?: string
  inputAnalysis?: InputAnalysis
  referenceEssay?: ReferenceEssay | null
}

export interface AiInvocationLog {
  id: number
  purpose: string
  provider?: string
  endpointType?: string
  model?: string
  providerRequestId?: string
  status: string
  latencyMs?: number
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  usageSource: 'PROVIDER' | 'LOCAL_ESTIMATE' | 'MIXED' | 'UNAVAILABLE' | string
  estimatedCost?: number
  currency?: string
  failureCode?: string
  failureMessage?: string
  createdAt?: string
}

export interface AiUsageSummary {
  provider?: string
  endpointType?: string
  model?: string
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  latencyMs?: number
  usageSource: 'PROVIDER' | 'LOCAL_ESTIMATE' | 'MIXED' | 'UNAVAILABLE' | string
  estimatedCost?: number
  currency?: string
  invocationCount: number
  invocations: AiInvocationLog[]
}

export interface EssayScoreResponse {
  essayId: number
  scoreId?: number
  essay: Essay
  result: RubricScoringResult | null
  scoringStatus: string
  aiModel?: string
  tokensUsed?: number
  processingTime?: number
  aiUsage?: AiUsageSummary
  rubricType?: EssayTypeCode
  rubricVersion?: string
  nativeScore?: number
  nativeScoreDisplay?: string
  normalizedScore?: number
  gradeLabel?: string
  confidenceLevel?: string
  idempotencyKey?: string
  contentHash?: string
  errorCode?: string
  errorMessage?: string
  attemptCount?: number
  retryable?: boolean
  createdAt?: string
}

export interface EssayHistoryItem {
  essayId: number
  essayType: EssayTypeCode
  essayTypeDisplayName: string
  taskPromptSummary: string
  contentSummary: string
  wordCount: number
  essayGroupId?: number
  versionNo?: number
  parentEssayId?: number
  nativeScoreDisplay?: string
  normalizedScore?: number
  gradeLabel?: string
  confidenceLevel?: string
  scoringStatus: string
  aiModel?: string
  createdAt: string
}

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
}

export interface DashboardTypeCount {
  essayType: EssayTypeCode
  essayTypeDisplayName: string
  count: number
}

export interface DashboardSummary {
  totalEssays: number
  completedEssays: number
  failedEssays: number
  scoringEssays: number
  averageNormalizedScore?: number
  bestNormalizedScore?: number
  submissionsLast7Days: number
  submissionsLast30Days: number
  submissionsLast90Days: number
  typeDistribution: DashboardTypeCount[]
}
