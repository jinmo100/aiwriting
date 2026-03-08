import request from './request'
import type { ApiConfig, ApiConfigRequest } from '@/types'

// 获取所有配置
export function getConfigs() {
  return request.get<ApiConfig[]>('/configs')
}

// 获取单个配置
export function getConfig(id: number) {
  return request.get<ApiConfig>(`/configs/${id}`)
}

// 创建配置
export function createConfig(data: ApiConfigRequest) {
  return request.post<ApiConfig>('/configs', data)
}

// 更新配置
export function updateConfig(id: number, data: ApiConfigRequest) {
  return request.put<ApiConfig>(`/configs/${id}`, data)
}

// 删除配置
export function deleteConfig(id: number) {
  return request.delete(`/configs/${id}`)
}

// 设置默认配置
export function setDefaultConfig(id: number) {
  return request.put(`/configs/${id}/default`)
}
