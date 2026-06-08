import request from './request'
import type { DashboardSummary } from '@/types'

export function getDashboardSummary() {
  return request.get<DashboardSummary>('/dashboard/summary')
}
