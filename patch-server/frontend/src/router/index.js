import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/views/Layout.vue'
import Auth from '@/views/Auth.vue'

import Logs from '../views/Logs.vue'

const routes = [
  {
    path: '/auth',
    name: 'Auth',
    component: Auth
  },
  // 兼容旧路由
  {
    path: '/login',
    redirect: '/auth'
  },
  {
    path: '/register',
    redirect: '/auth'
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'apps',
        name: 'Apps',
        component: () => import('@/views/Apps.vue'),
        meta: { title: '我的应用', icon: 'Box' }
      },
      {
        path: 'all-apps',
        name: 'AllApps',
        component: () => import('@/views/AllApps.vue'),
        meta: { title: '应用列表', icon: 'List', adminOnly: true }
      },
      {
        path: 'apps/:id',
        name: 'AppDetail',
        component: () => import('@/views/AppDetail.vue'),
        meta: { title: '应用详情', hidden: true }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表板', icon: 'DataAnalysis' }
      },
      {
        path: 'patches',
        name: 'Patches',
        component: () => import('@/views/Patches.vue'),
        meta: { title: '补丁管理', icon: 'Files' }
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/Stats.vue'),
        meta: { title: '统计分析', icon: 'TrendCharts' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/Users.vue'),
        meta: { title: '用户管理', icon: 'User', adminOnly: true }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: Logs,
        meta: { title: '操作日志', icon: 'Document', adminOnly: true }
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@/views/System.vue'),
        meta: { title: '系统管理', icon: 'Setting', adminOnly: true }
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/Search.vue'),
        meta: { title: '搜索结果', hidden: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  
  if (to.path !== '/auth' && !token) {
    next('/auth')
  } else if (to.path === '/auth' && token) {
    next('/')
  } else if (to.meta.adminOnly && user.role !== 'admin') {
    next('/')
  } else {
    next()
  }
})

export default router
