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

// 作文相关类型
export interface Essay {
  id: number
  content: string
  wordCount: number
  essayType: string
  createdAt: string
}

export interface EssaySubmitRequest {
  content: string
  essayType?: string
  configId?: number
}

export interface ScoreDetail {
  overallScore: number
  contentScore: number
  languageScore: number
  structureScore: number
  coherenceScore: number
  aiModel?: string
  strengths: string[]
  suggestions: string[]
  errors: ErrorDetail[]
  detailedFeedback: string
}

export interface ErrorDetail {
  sentence: string
  type: string
  description: string
  correction: string
}

export interface EssayScoreResponse {
  essayId: number
  score: ScoreDetail
  processingTime: number
}

// API响应类型
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
}

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
}
