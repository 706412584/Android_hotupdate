<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="item in stats" :key="item.title">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: item.color }">
              <el-icon :size="30"><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-title">{{ item.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card>
          <template #header>
            <span>下载趋势</span>
          </template>
          <div ref="chartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>最新补丁</span>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="patch in recentPatches"
              :key="patch.id"
              :timestamp="patch.created_at"
              placement="top"
            >
              <el-tag size="small">{{ patch.version }}</el-tag>
              <p>{{ patch.description }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/api'
import * as echarts from 'echarts'

const stats = ref([
  { title: '补丁总数', value: 0, icon: 'Files', color: '#409eff' },
  { title: '总下载量', value: 0, icon: 'Download', color: '#67c23a' },
  { title: '成功率', value: '0%', icon: 'SuccessFilled', color: '#e6a23c' },
  { title: '活跃用户', value: 0, icon: 'User', color: '#f56c6c' }
])

const recentPatches = ref([])
const chartRef = ref()

onMounted(async () => {
  await loadOverview()
  await loadRecentPatches()
  await loadChart()
})

const loadOverview = async () => {
  try {
    const data = await api.getOverview()
    stats.value[0].value = data.totalPatches
    stats.value[1].value = data.totalDownloads
    stats.value[2].value = data.successRate + '%'
    stats.value[3].value = data.activeUsers
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

const loadRecentPatches = async () => {
  try {
    const data = await api.getPatches({ page: 1, limit: 5 })
    recentPatches.value = data.patches
  } catch (error) {
    console.error('加载最新补丁失败:', error)
  }
}

const loadChart = async () => {
  try {
    const data = await api.getDownloadsTrend(7)
    
    const chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: data.map(d => d.date)
      },
      yAxis: { type: 'value' },
      series: [{
        data: data.map(d => d.count),
        type: 'line',
        smooth: true,
        areaStyle: {}
      }]
    })
  } catch (error) {
    console.error('加载图表失败:', error)
  }
}
</script>

<style scoped>
.stat-card {
  cursor: pointer;
  transition: all 0.3s;
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
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}
</style>
