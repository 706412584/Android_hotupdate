import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    const message = error.response?.data?.error || error.message || '请求失败'
    ElMessage.error(message)
    
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    
    return Promise.reject(error)
  }
)

// API 接口
export const api = {
  // 认证
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  
  // 补丁管理
  getPatches: (params) => request.get('/patches', { params }),
  getPatch: (id) => request.get(`/patches/${id}`),
  uploadPatch: (formData, onProgress) => request.post('/patches/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  }),
  updatePatch: (id, data) => request.put(`/patches/${id}`, data),
  deletePatch: (id) => request.delete(`/patches/${id}`),
  
  // 统计
  getOverview: () => request.get('/stats/overview'),
  getDownloadsTrend: (days) => request.get('/stats/downloads-trend', { params: { days } }),
  getVersionDistribution: () => request.get('/stats/version-distribution'),
  getDeviceDistribution: () => request.get('/stats/device-distribution'),
  getPatchStats: (id) => request.get(`/stats/patch/${id}`),
  
  // 用户管理
  getUsers: () => request.get('/users'),
  createUser: (data) => request.post('/users', data),
  deleteUser: (id) => request.delete(`/users/${id}`)
}

export default request
