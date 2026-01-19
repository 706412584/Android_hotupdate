<template>
  <div class="users">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="dialogVisible = true">
            <el-icon><Plus /></el-icon>
            添加用户
          </el-button>
        </div>
      </template>
      
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="email" label="邮箱" width="200" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应用/补丁" width="120">
          <template #default="{ row }">
            <el-space>
              <el-tag type="primary">{{ row.app_count || 0 }} 应用</el-tag>
              <el-tag type="success">{{ row.patch_count || 0 }} 补丁</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '正常' : '已封禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="180" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              @click="handleViewDetail(row)"
            >
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'active'"
              size="small"
              type="warning"
              @click="handleBan(row)"
              :disabled="row.role === 'admin'"
            >
              封禁
            </el-button>
            <el-button
              v-else
              size="small"
              type="success"
              @click="handleUnban(row)"
            >
              解封
            </el-button>
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(row)"
              :disabled="row.role === 'admin'"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 添加用户对话框 -->
    <el-dialog v-model="dialogVisible" title="添加用户" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio label="user">普通用户</el-radio>
            <el-radio label="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 用户详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="用户详情" width="900px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名">{{ userDetail.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userDetail.user?.email || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="userDetail.user?.role === 'admin' ? 'danger' : 'info'">
                {{ userDetail.user?.role === 'admin' ? '管理员' : '普通用户' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="userDetail.user?.status === 'active' ? 'success' : 'danger'">
                {{ userDetail.user?.status === 'active' ? '正常' : '已封禁' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="2">
              {{ userDetail.user?.created_at }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="应用列表" name="apps">
          <div style="margin-bottom: 16px;">
            <el-button 
              type="warning" 
              size="small" 
              @click="handleBanAllApps"
              :disabled="!userDetail.apps || userDetail.apps.length === 0"
            >
              封禁所有应用
            </el-button>
          </div>
          <el-table :data="userDetail.apps" max-height="400">
            <el-table-column prop="app_name" label="应用名称" width="150" />
            <el-table-column prop="package_name" label="包名" width="200" />
            <el-table-column prop="patch_count" label="补丁数" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
                  {{ row.status === 'active' ? '活跃' : '已封禁' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" width="180" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'active'"
                  size="small"
                  type="warning"
                  @click="handleBanApp(row)"
                >
                  封禁
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="success"
                  @click="handleUnbanApp(row)"
                >
                  解封
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!userDetail.apps || userDetail.apps.length === 0" description="暂无应用" />
        </el-tab-pane>

        <el-tab-pane label="补丁列表" name="patches">
          <div style="margin-bottom: 16px;">
            <el-button 
              type="danger" 
              size="small" 
              @click="handleDeleteAllPatches"
              :disabled="!userDetail.patches || userDetail.patches.length === 0"
            >
              删除所有补丁
            </el-button>
          </div>
          <el-table :data="userDetail.patches" max-height="400">
            <el-table-column prop="app_name" label="所属应用" width="150" />
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column prop="base_version" label="基础版本" width="100" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="file_size" label="大小" width="100">
              <template #default="{ row }">
                {{ formatSize(row.file_size) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
                  {{ row.status === 'active' ? '启用' : '已封禁' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" width="180" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'active'"
                  size="small"
                  type="warning"
                  @click="handleBanPatch(row)"
                >
                  封禁
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="success"
                  @click="handleUnbanPatch(row)"
                >
                  解封
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!userDetail.patches || userDetail.patches.length === 0" description="暂无补丁" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '@/api'

const loading = ref(false)
const users = ref([])
const dialogVisible = ref(false)
const detailDialogVisible = ref(false)
const formRef = ref()
const activeTab = ref('info')
const currentUserId = ref(null)

const userDetail = reactive({
  user: null,
  apps: [],
  patches: []
})

const form = reactive({
  username: '',
  password: '',
  email: '',
  role: 'user'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }]
}

const loadUsers = async () => {
  try {
    loading.value = true;
    const { data } = await api.getUsers();
    users.value = data || [];
  } catch (error) {
    console.error('加载用户列表失败:', error);
  } finally {
    loading.value = false;
  }
};

const handleCreate = async () => {
  try {
    await formRef.value.validate()
    await api.createUser(form)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    console.error('创建失败:', error)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个用户吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await api.deleteUser(row.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

const handleBan = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要封禁用户 "${row.username}" 吗？封禁后该用户将无法登录。`,
      '封禁用户',
      {
        confirmButtonText: '确定封禁',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await api.updateUserStatus(row.id, { status: 'banned' })
    ElMessage.success('用户已封禁')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('封禁失败')
    }
  }
}

const handleUnban = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要解封用户 "${row.username}" 吗？`,
      '解封用户',
      {
        confirmButtonText: '确定解封',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    await api.updateUserStatus(row.id, { status: 'active' })
    ElMessage.success('用户已解封')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('解封失败')
    }
  }
}

const handleViewDetail = async (row) => {
  try {
    currentUserId.value = row.id
    const { data } = await api.getUserDetail(row.id)
    userDetail.user = data.user
    userDetail.apps = data.apps
    userDetail.patches = data.patches
    detailDialogVisible.value = true
    activeTab.value = 'info'
  } catch (error) {
    ElMessage.error('加载用户详情失败')
  }
}

const handleBanAllApps = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要封禁该用户的所有应用吗？',
      '封禁应用',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await api.banUserApps(currentUserId.value)
    ElMessage.success('应用已全部封禁')
    handleViewDetail({ id: currentUserId.value })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('封禁失败')
    }
  }
}

const handleDeleteAllPatches = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要删除该用户的所有补丁吗？此操作不可恢复！',
      '删除补丁',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    const { data } = await api.deleteUserPatches(currentUserId.value)
    ElMessage.success(`已删除 ${data.count} 个补丁`)
    handleViewDetail({ id: currentUserId.value })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleBanApp = async (app) => {
  try {
    await ElMessageBox.confirm(
      `确定要封禁应用 "${app.app_name}" 吗？`,
      '封禁应用',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await api.updateAppStatus(app.id, 'inactive')
    ElMessage.success('应用已封禁')
    handleViewDetail({ id: currentUserId.value })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('封禁失败')
    }
  }
}

const handleUnbanApp = async (app) => {
  try {
    await ElMessageBox.confirm(
      `确定要解封应用 "${app.app_name}" 吗？`,
      '解封应用',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    await api.updateAppStatus(app.id, 'active')
    ElMessage.success('应用已解封')
    handleViewDetail({ id: currentUserId.value })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('解封失败')
    }
  }
}

const handleBanPatch = async (patch) => {
  try {
    await ElMessageBox.confirm(
      `确定要封禁补丁 "${patch.version}" 吗？`,
      '封禁补丁',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await api.updatePatchStatus(patch.id, 'inactive')
    ElMessage.success('补丁已封禁')
    handleViewDetail({ id: currentUserId.value })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('封禁失败')
    }
  }
}

const handleUnbanPatch = async (patch) => {
  try {
    await ElMessageBox.confirm(
      `确定要解封补丁 "${patch.version}" 吗？`,
      '解封补丁',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    await api.updatePatchStatus(patch.id, 'active')
    ElMessage.success('补丁已解封')
    handleViewDetail({ id: currentUserId.value })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('解封失败')
    }
  }
}

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

loadUsers()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
