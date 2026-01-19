<template>
  <div class="search-container">
    <div class="search-header">
      <h2>搜索结果</h2>
      <p>关键词: "{{ searchQuery }}"</p>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部" name="all">
        <div class="search-results" v-loading="loading">
          <div v-if="results.apps.length > 0" class="result-section">
            <h3>应用 ({{ results.apps.length }})</h3>
            <div class="apps-grid">
              <div class="app-card" v-for="app in results.apps" :key="'app-' + app.id" @click="goToApp(app.id)">
                <div class="app-icon-small">
                  <img v-if="app.icon" :src="app.icon" alt="">
                  <el-icon v-else><Box /></el-icon>
                </div>
                <div class="app-info">
                  <div class="app-name" v-html="highlight(app.app_name)"></div>
                  <div class="package-name" v-html="highlight(app.package_name)"></div>
                </div>
                <el-tag :type="app.status === 'active' ? 'success' : 'info'" size="small">
                  {{ app.status === 'active' ? '活跃' : '停用' }}
                </el-tag>
              </div>
            </div>
          </div>

          <div v-if="results.patches.length > 0" class="result-section">
            <h3>补丁 ({{ results.patches.length }})</h3>
            <el-table :data="results.patches" @row-click="goToPatch">
              <el-table-column label="版本" width="120">
                <template #default="{ row }">
                  <el-tag type="primary" size="small">v{{ row.version }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="app_name" label="应用" width="200" />
              <el-table-column label="描述">
                <template #default="{ row }">
                  <span v-html="highlight(row.description || '无描述')"></span>
                </template>
              </el-table-column>
              <el-table-column prop="created_at" label="创建时间" width="180" />
            </el-table>
          </div>

          <div v-if="user.role === 'admin' && results.users.length > 0" class="result-section">
            <h3>用户 ({{ results.users.length }})</h3>
            <el-table :data="results.users" @row-click="goToUser">
              <el-table-column label="用户名" width="150">
                <template #default="{ row }">
                  <span v-html="highlight(row.username)"></span>
                </template>
              </el-table-column>
              <el-table-column label="邮箱">
                <template #default="{ row }">
                  <span v-html="highlight(row.email || '未设置')"></span>
                </template>
              </el-table-column>
              <el-table-column prop="role" label="角色" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.role === 'admin' ? 'warning' : 'info'" size="small">
                    {{ row.role === 'admin' ? '管理员' : '普通用户' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="created_at" label="注册时间" width="180" />
            </el-table>
          </div>

          <el-empty
            v-if="!loading && results.apps.length === 0 && results.patches.length === 0 && results.users.length === 0"
            description="未找到相关结果"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`应用 (${results.apps.length})`" name="apps">
        <div class="apps-grid" v-loading="loading">
          <div class="app-card" v-for="app in results.apps" :key="app.id" @click="goToApp(app.id)">
            <div class="app-icon-small">
              <img v-if="app.icon" :src="app.icon" alt="">
              <el-icon v-else><Box /></el-icon>
            </div>
            <div class="app-info">
              <div class="app-name" v-html="highlight(app.app_name)"></div>
              <div class="package-name" v-html="highlight(app.package_name)"></div>
            </div>
            <el-tag :type="app.status === 'active' ? 'success' : 'info'" size="small">
              {{ app.status === 'active' ? '活跃' : '停用' }}
            </el-tag>
          </div>
          <el-empty v-if="!loading && results.apps.length === 0" description="未找到相关应用" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="`补丁 (${results.patches.length})`" name="patches">
        <el-table :data="results.patches" v-loading="loading" @row-click="goToPatch">
          <el-table-column label="版本" width="120">
            <template #default="{ row }">
              <el-tag type="primary" size="small">v{{ row.version }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="app_name" label="应用" width="200" />
          <el-table-column label="描述">
            <template #default="{ row }">
              <span v-html="highlight(row.description || '无描述')"></span>
            </template>
          </el-table-column>
          <el-table-column prop="created_at" label="创建时间" width="180" />
        </el-table>
        <el-empty v-if="!loading && results.patches.length === 0" description="未找到相关补丁" />
      </el-tab-pane>

      <el-tab-pane v-if="user.role === 'admin'" :label="`用户 (${results.users.length})`" name="users">
        <el-table :data="results.users" v-loading="loading" @row-click="goToUser">
          <el-table-column label="用户名" width="150">
            <template #default="{ row }">
              <span v-html="highlight(row.username)"></span>
            </template>
          </el-table-column>
          <el-table-column label="邮箱">
            <template #default="{ row }">
              <span v-html="highlight(row.email || '未设置')"></span>
            </template>
          </el-table-column>
          <el-table-column prop="role" label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="row.role === 'admin' ? 'warning' : 'info'" size="small">
                {{ row.role === 'admin' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="created_at" label="注册时间" width="180" />
        </el-table>
        <el-empty v-if="!loading && results.users.length === 0" description="未找到相关用户" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Box } from '@element-plus/icons-vue'
import api from '../api'

const route = useRoute()
const router = useRouter()
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))

const searchQuery = ref('')
const activeTab = ref('all')
const loading = ref(false)

const results = reactive({
  apps: [],
  patches: [],
  users: []
})

onMounted(() => {
  searchQuery.value = route.query.q || ''
  if (searchQuery.value) {
    performSearch()
  }
})

watch(() => route.query.q, (newQuery) => {
  searchQuery.value = newQuery || ''
  if (searchQuery.value) {
    performSearch()
  }
})

const performSearch = async () => {
  if (!searchQuery.value.trim()) {
    return
  }
  
  try {
    loading.value = true
    const { data } = await api.get('/search', {
      params: { q: searchQuery.value }
    })
    
    results.apps = data.apps || []
    results.patches = data.patches || []
    results.users = data.users || []
  } catch (error) {
    console.error('搜索失败:', error)
  } finally {
    loading.value = false
  }
}

const highlight = (text) => {
  if (!text || !searchQuery.value) return text
  const regex = new RegExp(`(${searchQuery.value})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

const goToApp = (id) => {
  router.push(`/apps/${id}`)
}

const goToPatch = (row) => {
  router.push(`/apps/${row.app_id}`)
}

const goToUser = (row) => {
  router.push('/users')
}
</script>

<style scoped>
.search-container {
  padding: 24px;
}

.search-header {
  margin-bottom: 24px;
}

.search-header h2 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.search-header p {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.search-results {
  min-height: 400px;
}

.result-section {
  margin-bottom: 32px;
}

.result-section h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #d4af7a;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.app-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.app-card:hover {
  border-color: #d4af7a;
  box-shadow: 0 2px 8px rgba(212, 175, 122, 0.15);
}

.app-icon-small {
  width: 48px;
  height: 48px;
  border-radius: 8px;
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
  border-radius: 8px;
  object-fit: cover;
}

.app-info {
  flex: 1;
  min-width: 0;
}

.app-name {
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.package-name {
  font-size: 12px;
  color: #909399;
  font-family: 'Courier New', monospace;
}

:deep(mark) {
  background: #fff3cd;
  color: #856404;
  padding: 2px 4px;
  border-radius: 2px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
