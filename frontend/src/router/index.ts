import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/useAuthStore'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/submit'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', public: true }
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/ConfigView.vue'),
    meta: { title: 'API配置' }
  },
  {
    path: '/embedding-config',
    name: 'EmbeddingConfig',
    component: () => import('@/views/EmbeddingConfigView.vue'),
    meta: { title: 'Embedding 配置' }
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
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '学习看板' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '个人中心' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  document.title = (to.meta.title as string) || '英作评析'
  const authStore = useAuthStore()
  const isPublic = Boolean(to.meta.public)
  try {
    await authStore.fetchMe()
  } catch {
    // /api/auth/me 失败时按未登录处理，避免守卫卡死。
  }

  if (!isPublic && !authStore.isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (isPublic && authStore.isAuthenticated && (to.path === '/login' || to.path === '/register')) {
    next((to.query.redirect as string) || '/submit')
    return
  }

  next()
})

export default router
