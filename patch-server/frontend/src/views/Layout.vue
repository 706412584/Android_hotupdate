<template>
  <el-container class="layout-container">
    <el-aside :width="collapsed ? '64px' : '240px'" class="sidebar">
      <div class="logo">
        <el-icon v-if="collapsed" :size="28" color="#667eea"><Box /></el-icon>
        <h2 v-else>补丁管理</h2>
      </div>
      
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        router
        class="sidebar-menu"
      >
        <el-menu-item
          v-for="route in visibleRoutes"
          :key="route.path"
          :index="route.path"
        >
          <el-icon><component :is="route.meta.icon" /></el-icon>
          <span>{{ route.meta.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button text @click="collapsed = !collapsed">
            <el-icon :size="20"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          
          <el-input
            v-model="searchQuery"
            placeholder="搜索应用、补丁、用户..."
            class="global-search"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        
        <div class="header-right">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
            <el-button text @click="showNotifications = true">
              <el-icon :size="20"><Bell /></el-icon>
            </el-button>
          </el-badge>
          
          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-info">
              <el-avatar :size="32" :style="{ background: '#667eea' }">
                {{ user.username?.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="username">{{ user.username }}</span>
              <el-tag v-if="user.role === 'admin'" type="warning" size="small">管理员</el-tag>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <div style="padding: 8px 0;">
                    <div style="font-weight: 600;">{{ user.username }}</div>
                    <div style="font-size: 12px; color: #999;">{{ user.email || '未设置邮箱' }}</div>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
    
    <!-- 通知抽屉 -->
    <el-drawer
      v-model="showNotifications"
      title="通知中心"
      direction="rtl"
      size="400px"
    >
      <div class="notifications-container">
        <div class="notifications-header">
          <el-button size="small" @click="markAllRead" :disabled="unreadCount === 0">
            全部已读
          </el-button>
          <el-button size="small" @click="clearRead">
            清空已读
          </el-button>
        </div>
        
        <el-tabs v-model="notificationTab">
          <el-tab-pane label="全部" name="all">
            <div class="notifications-list" v-loading="notificationsLoading">
              <div
                v-for="notification in notifications"
                :key="notification.id"
                class="notification-item"
                :class="{ unread: !notification.is_read }"
                @click="handleNotificationClick(notification)"
              >
                <div class="notification-icon" :class="getNotificationTypeClass(notification.type)">
                  <el-icon><component :is="getNotificationIcon(notification.type)" /></el-icon>
                </div>
                <div class="notification-content">
                  <div class="notification-title">{{ notification.title }}</div>
                  <div class="notification-message">{{ notification.message }}</div>
                  <div class="notification-time">{{ formatRelativeTime(notification.created_at) }}</div>
                </div>
                <el-button
                  text
                  size="small"
                  @click.stop="deleteNotification(notification.id)"
                >
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
              
              <el-empty v-if="notifications.length === 0" description="暂无通知" :image-size="80" />
            </div>
          </el-tab-pane>
          
          <el-tab-pane :label="`未读 (${unreadCount})`" name="unread">
            <div class="notifications-list" v-loading="notificationsLoading">
              <div
                v-for="notification in unreadNotifications"
                :key="notification.id"
                class="notification-item unread"
                @click="handleNotificationClick(notification)"
              >
                <div class="notification-icon" :class="getNotificationTypeClass(notification.type)">
                  <el-icon><component :is="getNotificationIcon(notification.type)" /></el-icon>
                </div>
                <div class="notification-content">
                  <div class="notification-title">{{ notification.title }}</div>
                  <div class="notification-message">{{ notification.message }}</div>
                  <div class="notification-time">{{ formatRelativeTime(notification.created_at) }}</div>
                </div>
                <el-button
                  text
                  size="small"
                  @click.stop="deleteNotification(notification.id)"
                >
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
              
              <el-empty v-if="unreadNotifications.length === 0" description="暂无未读通知" :image-size="80" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessageBox, ElMessage } from 'element-plus';
import { Box, Fold, Expand, SwitchButton, Bell, Close, SuccessFilled, WarningFilled, InfoFilled, CircleCheck, Search } from '@element-plus/icons-vue';
import api from '../api';

const router = useRouter();
const route = useRoute();
const collapsed = ref(false);
const showNotifications = ref(false);
const notificationTab = ref('all');
const notifications = ref([]);
const unreadCount = ref(0);
const notificationsLoading = ref(false);
const searchQuery = ref('');

const user = ref(JSON.parse(localStorage.getItem('user') || '{}'));

const allRoutes = [
  { path: '/apps', meta: { title: '我的应用', icon: 'Box' } },
  { path: '/all-apps', meta: { title: '应用列表', icon: 'List', adminOnly: true } },
  { path: '/dashboard', meta: { title: '仪表板', icon: 'DataAnalysis' } },
  { path: '/patches', meta: { title: '补丁管理', icon: 'Files' } },
  { path: '/stats', meta: { title: '统计分析', icon: 'TrendCharts' } },
  { path: '/users', meta: { title: '用户管理', icon: 'User', adminOnly: true } },
  { path: '/logs', meta: { title: '操作日志', icon: 'Document', adminOnly: true } },
  { path: '/system', meta: { title: '系统管理', icon: 'Setting', adminOnly: true } }
];

const visibleRoutes = computed(() => {
  return allRoutes.filter(route => {
    if (route.meta.adminOnly && user.value.role !== 'admin') {
      return false;
    }
    return true;
  });
});

const unreadNotifications = computed(() => {
  return notifications.value.filter(n => !n.is_read);
});

onMounted(() => {
  loadNotifications();
  // 每30秒刷新一次通知
  setInterval(loadNotifications, 30000);
});

const loadNotifications = async () => {
  try {
    const { data } = await api.get('/notifications', { params: { limit: 50 } });
    notifications.value = data.notifications || [];
    unreadCount.value = data.unreadCount || 0;
  } catch (error) {
    console.error('加载通知失败:', error);
  }
};

const markAllRead = async () => {
  try {
    await api.put('/notifications/read-all');
    ElMessage.success('已全部标记为已读');
    loadNotifications();
  } catch (error) {
    ElMessage.error('操作失败');
  }
};

const clearRead = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有已读通知吗？', '提示', {
      type: 'warning'
    });
    
    await api.delete('/notifications/clear-read');
    ElMessage.success('已清空已读通知');
    loadNotifications();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
    }
  }
};

const handleNotificationClick = async (notification) => {
  try {
    // 标记为已读
    if (!notification.is_read) {
      await api.put(`/notifications/${notification.id}/read`);
      loadNotifications();
    }
    
    // 跳转到相关页面
    if (notification.link) {
      showNotifications.value = false;
      router.push(notification.link);
    }
  } catch (error) {
    console.error('处理通知失败:', error);
  }
};

const deleteNotification = async (id) => {
  try {
    await api.delete(`/notifications/${id}`);
    loadNotifications();
  } catch (error) {
    ElMessage.error('删除失败');
  }
};

const getNotificationIcon = (type) => {
  const iconMap = {
    app_created: 'SuccessFilled',
    app_submitted: 'InfoFilled',
    app_approved: 'CircleCheck',
    app_rejected: 'WarningFilled',
    app_review: 'InfoFilled',
    patch_uploaded: 'SuccessFilled',
    patch_failed: 'WarningFilled'
  };
  return iconMap[type] || 'InfoFilled';
};

const getNotificationTypeClass = (type) => {
  if (type.includes('approved') || type.includes('created') || type.includes('uploaded')) {
    return 'success';
  }
  if (type.includes('rejected') || type.includes('failed')) {
    return 'danger';
  }
  if (type.includes('review') || type.includes('submitted')) {
    return 'warning';
  }
  return 'info';
};

const formatRelativeTime = (date) => {
  const now = new Date();
  const past = new Date(date);
  const diff = now - past;
  
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;
  return new Date(date).toLocaleString('zh-CN');
};

const handleSearch = () => {
  if (!searchQuery.value.trim()) {
    return;
  }
  
  router.push({
    path: '/search',
    query: { q: searchQuery.value }
  });
};

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      });
      
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      router.push('/login');
    } catch {}
  }
};
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
}

.sidebar {
  background: white;
  border-right: 1px solid var(--border-color);
  transition: width 0.3s;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--border-color);
}

.logo h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--primary-color);
  margin: 0;
}

.sidebar-menu {
  border-right: none;
  padding: 8px;
}

.sidebar-menu .el-menu-item {
  border-radius: 8px;
  margin-bottom: 4px;
  height: 48px;
  line-height: 48px;
  color: var(--text-secondary);
}

.sidebar-menu .el-menu-item:hover {
  background: var(--primary-light);
  color: var(--primary-color);
}

.sidebar-menu .el-menu-item.is-active {
  background: var(--primary-color);
  color: white;
}

.header {
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #e5e7eb;
  height: 64px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: background 0.3s;
}

.user-info:hover {
  background: #f5f7fa;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
}

.main-content {
  background: #f5f7fa;
  padding: 0;
  min-height: calc(100vh - 64px);
}

.notification-badge {
  margin-right: 16px;
}

.notifications-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.notifications-header {
  display: flex;
  gap: 8px;
  padding: 0 0 16px 0;
  border-bottom: 1px solid #e5e7eb;
}

.notifications-list {
  flex: 1;
  overflow-y: auto;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.3s;
}

.notification-item:hover {
  background: #f5f7fa;
}

.notification-item.unread {
  background: #f0f7ff;
}

.notification-item.unread:hover {
  background: #e6f2ff;
}

.notification-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
}

.notification-icon.success {
  background: #67c23a;
}

.notification-icon.danger {
  background: #f56c6c;
}

.notification-icon.warning {
  background: #e6a23c;
}

.notification-icon.info {
  background: #409eff;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.notification-message {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  word-break: break-word;
}

.notification-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.global-search {
  width: 300px;
  margin-left: 16px;
}

@media (max-width: 768px) {
  .global-search {
    width: 200px;
  }
}
</style>
