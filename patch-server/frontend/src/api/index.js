import axios from 'axios';
import { ElMessage } from 'element-plus';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
});

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  response => {
    return response;
  },
  error => {
    const message = error.response?.data?.error || error.message || '请求失败';
    
    // 只在非登录/注册接口时显示错误消息
    const isAuthRequest = error.config?.url?.includes('/auth/');
    // 静默处理 notifications/config 的 404 错误（功能未实现）
    const isNotificationConfig404 = error.config?.url?.includes('/notifications/config') && error.response?.status === 404;
    
    if (!isAuthRequest && !isNotificationConfig404) {
      ElMessage.error(message);
    }
    
    // 401 错误且不在登录页面时，跳转到登录页
    if (error.response?.status === 401 && !window.location.pathname.includes('/auth')) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/auth';
    }
    
    return Promise.reject(error);
  }
);

// API 接口封装
export const api = {
  // 认证
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  
  // 应用管理
  getApps: () => request.get('/apps'),
  getApp: (id) => request.get(`/apps/${id}`),
  createApp: (data) => request.post('/apps', data),
  updateApp: (id, data) => request.put(`/apps/${id}`, data),
  deleteApp: (id) => request.delete(`/apps/${id}`),
  
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
  getAlerts: () => request.get('/stats/alerts'),
  getTopApps: (limit = 5) => request.get('/stats/top-apps', { params: { limit } }),
  
  // 日志
  getLogs: (params) => request.get('/logs', { params }),
  
  // 用户管理
  getUsers: () => request.get('/users'),
  createUser: (data) => request.post('/users', data),
  deleteUser: (id) => request.delete(`/users/${id}`),
  updateUserStatus: (id, data) => request.put(`/users/${id}/status`, data),
  
  // 补丁生成
  checkPatchCli: () => request.get('/generate/check'),
  generatePatch: (formData, onProgress) => request.post('/generate/patch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  }),
  
  // 加密管理
  generateEncryptionKey: () => request.get('/encryption/generate-key'),
  getEncryptionConfig: (appId) => request.get(`/encryption/config/${appId}`),
  updateEncryptionConfig: (appId, data) => request.put(`/encryption/config/${appId}`, data),
  validateEncryptionKey: (key) => request.post('/encryption/validate-key', { key }),
  testEncryption: (key, text) => request.post('/encryption/test', { key, text }),
  
  // 调度器管理
  getTasks: () => request.get('/scheduler/tasks'),
  startTask: (name) => request.post(`/scheduler/tasks/${name}/start`),
  stopTask: (name) => request.post(`/scheduler/tasks/${name}/stop`),
  runBackup: () => request.post('/scheduler/backup/run'),
  cleanLogs: () => request.post('/scheduler/clean-logs/run'),
  cleanDownloads: () => request.post('/scheduler/clean-downloads/run'),
  getBackups: () => request.get('/scheduler/backups'),
  deleteBackup: (filename) => request.delete(`/scheduler/backups/${filename}`),
  restoreBackup: (filename) => request.post('/scheduler/restore', { filename }),
  
  // 通知管理
  getNotificationConfig: () => request.get('/notifications/config'),
  testEmail: (email) => request.post('/notifications/test/email', { email }),
  testWebhook: () => request.post('/notifications/test/webhook'),
  sendNotification: (data) => request.post('/notifications/send', data),
  
  // 系统配置
  getSystemConfig: () => request.get('/system-config'),
  updateSystemConfig: (key, value) => request.put(`/system-config/${key}`, { value }),
  batchUpdateSystemConfig: (configs) => request.post('/system-config/batch', { configs }),
  
  // 用户管理增强
  getUserDetail: (id) => request.get(`/users/${id}/detail`),
  banUserApps: (id) => request.put(`/users/${id}/ban-apps`),
  deleteUserPatches: (id) => request.delete(`/users/${id}/patches`),
  
  // 应用审核
  getPendingApps: () => request.get('/apps/pending-review'),
  reviewApp: (id, action, note) => request.post(`/apps/${id}/review`, { action, note }),
  updateAppStatus: (id, status) => request.put(`/apps/${id}/status`, { status }),
  
  // 补丁状态管理
  updatePatchStatus: (id, status) => request.put(`/patches/${id}/status`, { status })
};

export default request;
