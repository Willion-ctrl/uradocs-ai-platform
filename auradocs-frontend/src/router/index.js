import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'Workspace',
    component: () => import('@/views/Workspace.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('auradoc_token')

  if (to.meta.requiresAuth && !token) {
    // 访问受保护页面但无 Token -> 强制跳转登录页
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录还访问登录页 -> 直接回工作台
    next('/')
  } else {
    next()
  }
})

export default router