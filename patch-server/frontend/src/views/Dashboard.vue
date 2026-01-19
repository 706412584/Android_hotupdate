<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="6" v-for="item in stats" :key="item.title">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: item.color }">
              <el-icon :size="30"><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-title">{{ item.title }}</div>
              <div class="stat-trend" v-if="item.trend">
                <el-icon :color="item.trend > 0 ? '#67c23a' : '#f56c6c'">
                  <component :is="item.trend > 0 ? 'CaretTop' : 'CaretBottom'" />
                </el-icon>
                <span :style="{ color: item.trend > 0 ? '#67c23a' : '#f56c6c' }">
                  {{ Math.abs(item.trend) }}%
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待处理事项 -->
    <el-row :gutter="20" style="margin-top: 20px" v-if="user.role === 'admin' && (pendingApps.length > 0 || alerts.length > 0)">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>待处理事项</span>
              <el-badge :value="pendingApps.length + alerts.length" type="danger" />
            </div>
          </template>
          <el-space direction="vertical" style="width: 100%">
            <el-alert
              v-for="app in pendingApps"
              :key="'app-' + app.id"
              :title="`应用「${app.app_name}」待审核`"
              type="warning"
              show-icon
              :closable="false"
            >
              <template #default>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>提交者: {{ app.owner_name }} | 提交时间: {{ formatDate(app.created_at) }}</span>
                  <el-button size="small" type="primary" @click="goToReview(app.id)">去审核</el-button>
                </div>
              </template>
            </el-alert>
            <el-alert
              v-for="alert in alerts"
              :key="'alert-' + alert.id"
              :title="alert.title"
              :type="alert.type"
              show-icon
              :closable="false"
            >
              <template #default>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>{{ alert.message }}</span>
                  <el-button size="small" @click="handleAlert(alert)">查看</el-button>
                </div>
              </template>
            </el-alert>
          </el-space>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 下载趋势图 -->
      <el-col :xs="24" :lg="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>下载趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadChart">
                <el-radio-button :label="7">7天</el-radio-button>
                <el-radio-button :label="30">30天</el-radio-button>
                <el-radio-button :label="90">90天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="chartRef" style="height: 300px" v-loading="chartLoading"></div>
        </el-card>
      </el-col>
      
      <!-- 最近活动 -->
      <el-col :xs="24" :lg="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近活动</span>
              <el-link type="primary" @click="goToLogs">查看全部</el-link>
            </div>
          </template>
          <el-timeline v-if="recentActivities.length > 0">
            <el-timeline-item
              v-for="activity in recentActivities"
              :key="activity.id"
              :timestamp="formatDate(activity.created_at)"
              placement="top"
              :type="getActivityType(activity.action)"
            >
              <div class="activity-item">
                <div class="activity-user">{{ activity.username }}</div>
                <div class="activity-action">{{ getActionLabel(activity.action) }}</div>
                <div class="activity-detail" v-if="activity.resource_type">
                  {{ activity.resource_type }} #{{ activity.resource_id }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无活动记录" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 热门应用 -->
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <span>热门应用 Top 5</span>
          </template>
          <el-table :data="topApps" style="width: 100%" :show-header="false">
            <el-table-column prop="app_name" label="应用">
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <div class="app-icon-small">
                    <img v-if="row.icon" :src="row.icon" alt="">
                    <el-icon v-else><Box /></el-icon>
                  </div>
                  <div>
                    <div style="font-weight: 500;">{{ row.app_name }}</div>
                    <div style="font-size: 12px; color: #999;">{{ row.package_name }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="download_count" label="下载" width="100" align="right">
              <template #default="{ row }">
                <el-tag type="success">{{ row.download_count }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 最新补丁 -->
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最新补丁</span>
              <el-link type="primary" @click="goToPatches">查看全部</el-link>
            </div>
          </template>
          <el-table :data="recentPatches" style="width: 100%" :show-header="false">
            <el-table-column prop="version" label="版本">
              <template #default="{ row }">
                <div>
                  <el-tag type="primary" size="small">v{{ row.version }}</el-tag>
                  <div style="font-size: 12px; color: #999; margin-top: 4px;">
                    {{ row.description || '无描述' }}
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="时间" width="120" align="right">
              <template #default="{ row }">
                <div style="font-size: 12px; color: #999;">
                  {{ formatRelativeTime(row.created_at) }}
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="goToCreateApp" :icon="Plus">创建应用</el-button>
            <el-button type="success" @click="goToUploadPatch" :icon="Upload">上传补丁</el-button>
            <el-button @click="goToStats" :icon="TrendCharts">查看统计</el-button>
            <el-button v-if="user.role === 'admin'" @click="goToUsers" :icon="User">用户管理</el-button>
            <el-button v-if="user.role === 'admin'" @click="goToSystem" :icon="Setting">系统设置</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Upload, TrendCharts, User, Setting, Box, CaretTop, CaretBottom } from '@element-plus/icons-vue'
import { api } from '@/api'
import * as echarts from 'echarts'

const router = useRouter()
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))

const stats = ref([
  { title: '应用总数', value: 0, icon: 'Box', color: '#409eff', trend: 0 },
  { title: '补丁总数', value: 0, icon: 'Files', color: '#67c23a', trend: 0 },
  { title: '今日下载', value: 0, icon: 'Download', color: '#e6a23c', trend: 0 },
  { title: '成功率', value: '0%', icon: 'SuccessFilled', color: '#f56c6c', trend: 0 }
])

const pendingApps = ref([])
const alerts = ref([])
const recentActivities = ref([])
const topApps = ref([])
const recentPatches = ref([])
const chartRef = ref()
const trendDays = ref(7)
const chartLoading = ref(false)

const actionLabels = {
  create_app: '创建了应用',
  update_app: '更新了应用',
  delete_app: '删除了应用',
  toggle_app_status: '切换了应用状态',
  review_app: '审核了应用',
  upload_patch: '上传了补丁',
  update_patch: '更新了补丁',
  delete_patch: '删除了补丁',
  login: '登录了系统',
  register_user: '注册了账号'
}

onMounted(async () => {
  await Promise.all([
    loadOverview(),
    loadPendingApps(),
    loadAlerts(),
    loadRecentActivities(),
    loadTopApps(),
    loadRecentPatches(),
    loadChart()
  ])
})

const loadOverview = async () => {
  try {
    const { data } = await api.getOverview()
    stats.value[0].value = data.totalApps || 0
    stats.value[0].trend = data.appsTrend || 0
    stats.value[1].value = data.totalPatches || 0
    stats.value[1].trend = data.patchesTrend || 0
    stats.value[2].value = data.todayDownloads || 0
    stats.value[2].trend = data.downloadsTrend || 0
    stats.value[3].value = (data.successRate || 0).toFixed(1) + '%'
    stats.value[3].trend = data.successRateTrend || 0
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

const loadPendingApps = async () => {
  if (user.value.role !== 'admin') return
  try {
    const { data } = await api.getPendingApps()
    pendingApps.value = data.slice(0, 3)
  } catch (error) {
    console.error('加载待审核应用失败:', error)
  }
}

const loadAlerts = async () => {
  try {
    const { data } = await api.getAlerts()
    alerts.value = data || []
  } catch (error) {
    console.error('加载告警失败:', error)
  }
}

const loadRecentActivities = async () => {
  try {
    const { data } = await api.getLogs({ page: 1, limit: 10 })
    recentActivities.value = data.logs || []
  } catch (error) {
    console.error('加载最近活动失败:', error)
  }
}

const loadTopApps = async () => {
  try {
    const { data } = await api.getTopApps(5)
    topApps.value = data || []
  } catch (error) {
    console.error('加载热门应用失败:', error)
  }
}

const loadRecentPatches = async () => {
  try {
    const { data } = await api.getPatches({ page: 1, limit: 5 })
    recentPatches.value = data.patches || []
  } catch (error) {
    console.error('加载最新补丁失败:', error)
  }
}

const loadChart = async () => {
  try {
    chartLoading.value = true
    const { data } = await api.getDownloadsTrend(trendDays.value)
    
    const chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { 
        trigger: 'axis',
        formatter: '{b}<br/>下载量: {c}'
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: (data || []).map(d => d.date)
      },
      yAxis: { 
        type: 'value',
        minInterval: 1
      },
      series: [{
        data: (data || []).map(d => d.count),
        type: 'line',
        smooth: true,
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [{
              offset: 0, color: 'rgba(64, 158, 255, 0.3)'
            }, {
              offset: 1, color: 'rgba(64, 158, 255, 0.05)'
            }]
          }
        },
        lineStyle: {
          color: '#409eff',
          width: 2
        },
        itemStyle: {
          color: '#409eff'
        }
      }]
    })
    
    window.addEventListener('resize', () => chart.resize())
  } catch (error) {
    console.error('加载图表失败:', error)
  } finally {
    chartLoading.value = false
  }
}

const getActionLabel = (action) => {
  return actionLabels[action] || action
}

const getActivityType = (action) => {
  if (action.includes('delete')) return 'danger'
  if (action.includes('create')) return 'success'
  if (action.includes('update')) return 'warning'
  return 'primary'
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}

const formatRelativeTime = (date) => {
  const now = new Date()
  const past = new Date(date)
  const diff = now - past
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return formatDate(date)
}

const goToReview = (id) => {
  router.push('/system')
}

const handleAlert = (alert) => {
  if (alert.link) {
    router.push(alert.link)
  }
}

const goToCreateApp = () => {
  router.push('/apps')
}

const goToUploadPatch = () => {
  router.push('/patches')
}

const goToStats = () => {
  router.push('/stats')
}

const goToUsers = () => {
  router.push('/users')
}

const goToSystem = () => {
  router.push('/system')
}

const goToLogs = () => {
  router.push('/logs')
}

const goToPatches = () => {
  router.push('/patches')
}
</script>

<style scoped>
.dashboard {
  padding: 24px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
  border-radius: 12px;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 15px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 500;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.activity-item {
  font-size: 13px;
}

.activity-user {
  font-weight: 500;
  color: #303133;
}

.activity-action {
  color: #606266;
  margin-top: 2px;
}

.activity-detail {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}

.app-icon-small {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #d4af7a;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.app-icon-small img {
  width: 100%;
  height: 100%;
  border-radius: 6px;
  object-fit: cover;
}

.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .dashboard {
    padding: 16px;
  }
  
  .stat-value {
    font-size: 24px;
  }
  
  .quick-actions {
    flex-direction: column;
  }
  
  .quick-actions .el-button {
    width: 100%;
  }
}
</style>
