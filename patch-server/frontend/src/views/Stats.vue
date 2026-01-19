<template>
  <div class="stats">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>版本分布</template>
          <div ref="versionChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>设备分布</template>
          <div ref="deviceChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/api'
import * as echarts from 'echarts'

const versionChartRef = ref()
const deviceChartRef = ref()

onMounted(async () => {
  await loadVersionChart()
  await loadDeviceChart()
})

const loadVersionChart = async () => {
  try {
    const data = await api.getVersionDistribution()
    
    const chart = echarts.init(versionChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: '50%',
        data: data.map(d => ({ name: d.version, value: d.count }))
      }]
    })
  } catch (error) {
    console.error('加载版本分布失败:', error)
  }
}

const loadDeviceChart = async () => {
  try {
    const data = await api.getDeviceDistribution()
    
    const chart = echarts.init(deviceChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: data.map(d => d.model) },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: data.map(d => d.count)
      }]
    })
  } catch (error) {
    console.error('加载设备分布失败:', error)
  }
}
</script>
