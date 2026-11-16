import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('@/components/AppShell.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '学习工作台' }
        },
        {
          path: 'tutor',
          name: 'tutor',
          component: () => import('@/views/TutorView.vue'),
          meta: { title: 'AI 导师' }
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/KnowledgeView.vue'),
          meta: { title: '知识库' }
        },
        {
          path: 'quiz',
          name: 'quiz',
          component: () => import('@/views/QuizView.vue'),
          meta: { title: '智能测验' }
        },
        {
          path: 'plan',
          name: 'plan',
          component: () => import('@/views/PlanView.vue'),
          meta: { title: '学习计划' }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '个人中心' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: to.fullPath === '/' ? undefined : { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
