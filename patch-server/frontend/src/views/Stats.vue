<template>
  <div class="stats-container">
    <h2 style="margin-bottom: 24px;">统计分析</h2>

    <!-- 概览卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon :size="40" color="#d4af7a"><Document /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ overview.totalPatches }}</div>
              <div class="stat-label">补丁总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon :size="40" color="#67c23a"><Download /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ overview.totalDownloads }}</div>
              <div class="stat-label">总下载次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon :size="40" color="#409eff"><TrendCharts /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ overview.successRate }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon :size="40" color="#e6a23c"><User /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ overview.activeUsers }}</div>
              <div class="stat-label">活跃用户（7天）</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 下载趋势 -->
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>下载趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadDownloadsTrend">
            <el-radio-button :label="7">7天</el-radio-button>
            <el-radio-button :label="30">30天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div v-if="downloadsTrend.length > 0" ref="trendChartRef" style="height: 300px"></div>
      <el-empty v-else description="暂无下载数据" />
    </el-card>

    <el-row :gutter="20">
      <!-- 版本分布 -->
      <el-col :span="12">
        <el-card>
          <template #header>版本分布</template>
          <div v-if="versionDistribution.length > 0" ref="versionChartRef" style="height: 300px"></div>
          <el-empty v-else description="暂无版本数据" :image-size="100" />
        </el-card>
      </el-col>
      
      <!-- 设备分布 -->
      <el-col :span="12">
        <el-card>
          <template #header>设备分布</template>
          <div v-if="deviceDistribution.length > 0" ref="deviceChartRef" style="height: 300px"></div>
          <el-empty v-else description="暂无设备数据" :image-size="100" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 补丁列表 -->
    <el-card style="margin-top: 20px;">
      <template #header>补丁统计</template>
      <el-table :data="patches" v-loading="loading">
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="download_count" label="下载次数" width="120" />
        <el-table-column label="成功率" width="120">
          <template #default="{ row }">
            {{ calculateSuccessRate(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '活跃' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.created_at) }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Document, Download, TrendCharts, User } from '@element-plus/icons-vue'
import { api } from '@/api'
import * as echarts from 'echarts'

const versionChartRef = ref()
const deviceChartRef = ref()
const trendChartRef = ref()
const loading = ref(false)
const trendDays = ref(7)

const overview = reactive({
  totalPatches: 0,
  totalDownloads: 0,
  successRate: 0,
  activeUsers: 0
})

const downloadsTrend = ref([])
const versionDistribution = ref([])
const deviceDistribution = ref([])
const patches = ref([])

onMounted(async () => {
  await loadOverview()
  await loadDownloadsTrend()
  await loadVersionChart()
  await loadDeviceChart()
  await loadPatches()
})

const loadOverview = async () => {
  try {
    const { data } = await api.getOverview()
    Object.assign(overview, data)
  } catch (error) {
    console.error('加载概览失败:', error)
  }
}

const loadDownloadsTrend = async () => {
  try {
    const { data } = await api.getDownloadsTrend(trendDays.value)
    downloadsTrend.value = data || []
    
    if (data && data.length > 0) {
      const chart = echarts.init(trendChartRef.value)
      chart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: data.map(d => d.date)
        },
        yAxis: { type: 'value' },
        series: [{
          type: 'line',
          data: data.map(d => d.count),
          smooth: true,
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(212, 175, 122, 0.3)' },
                { offset: 1, color: 'rgba(212, 175, 122, 0.05)' }
              ]
            }
          },
          itemStyle: { color: '#d4af7a' }
        }]
      })
    }
  } catch (error) {
    console.error('加载下载趋势失败:', error)
  }
}

const loadVersionChart = async () => {
  try {
    const { data } = await api.getVersionDistribution()
    versionDistribution.value = data || []
    
    if (data && data.length > 0) {
      const chart = echarts.init(versionChartRef.value)
      chart.setOption({
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie',
          radius: '60%',
          data: data.map(d => ({ name: d.version, value: d.count })),
          itemStyle: {
            borderRadius: 8,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            formatter: '{b}: {c} ({d}%)'
          }
        }]
      })
    }
  } catch (error) {
    console.error('加载版本分布失败:', error)
  }
}

const loadDeviceChart = async () => {
  try {
    const { data } = await api.getDeviceDistribution()
    deviceDistribution.value = data || []
    
    if (data && data.length > 0) {
      const chart = echarts.init(deviceChartRef.value)
      chart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        xAxis: {
          type: 'category',
          data: data.map(d => d.model),
          axisLabel: { rotate: 45 }
        },
        yAxis: { type: 'value' },
        series: [{
          type: 'bar',
          data: data.map(d => d.count),
          itemStyle: {
            color: '#d4af7a',
            borderRadius: [4, 4, 0, 0]
          }
        }]
      })
    }
  } catch (error) {
    console.error('加载设备分布失败:', error)
  }
}

const loadPatches = async () => {
  try {
    loading.value = true
    const { data } = await api.getPatches({ page: 1, limit: 10 })
    patches.value = data.patches || []
  } catch (error) {
    console.error('加载补丁列表失败:', error)
  } finally {
    loading.value = false
  }
}

const calculateSuccessRate = (patch) => {
  const total = (patch.success_count || 0) + (patch.fail_count || 0)
  if (total === 0) return 'N/A'
  const rate = ((patch.success_count || 0) / total * 100).toFixed(1)
  return `${rate}%`
}

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN')
}
</script>

<style scoped>
.stats-container {
  padding: 24px;
}

.stat-card {
  border-radius: 12px;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(212, 175, 122, 0.2);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}
</style>
