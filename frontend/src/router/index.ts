import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/config'
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/ConfigView.vue'),
    meta: { title: 'API配置' }
  },
  {
    path: '/submit',
    name: 'Submit',
    component: () => import('@/views/SubmitView.vue'),
    meta: { title: '提交作文' }
  },
  {
    path: '/result/:id',
    name: 'Result',
    component: () => import('@/views/ResultView.vue'),
    meta: { title: '评分结果' }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/HistoryView.vue'),
    meta: { title: '历史记录' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = (to.meta.title as string) || 'AI英语作文评分系统'
  next()
})

export default router
