<template>
  <div class="patches">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>补丁列表</span>
          <el-space>
            <el-dropdown v-if="selectedPatches.length > 0" @command="handleBatchCommand">
              <el-button type="primary">
                批量操作 ({{ selectedPatches.length }})
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="enable">批量启用</el-dropdown-item>
                  <el-dropdown-item command="disable">批量停用</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>批量删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-select v-model="filterAppId" placeholder="筛选应用" clearable @change="loadPatches" style="width: 200px">
              <el-option
                v-for="app in apps"
                :key="app.id"
                :label="app.app_name"
                :value="app.id"
              />
            </el-select>
            <el-button type="primary" @click="showUploadDialog">
              <el-icon><Upload /></el-icon>
              上传补丁
            </el-button>
          </el-space>
        </div>
      </template>
      
      <el-table :data="patches" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="base_version" label="基础版本" width="120" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="file_size" label="大小" width="100">
          <template #default="{ row }">
            {{ formatSize(row.file_size) }}
          </template>
        </el-table-column>
        <el-table-column prop="download_count" label="下载次数" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.limit"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadPatches"
        @size-change="loadPatches"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
    
    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="编辑补丁" width="500px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="editForm.forceUpdate" />
        </el-form-item>
        <el-form-item label="灰度百分比">
          <el-slider v-model="editForm.rolloutPercentage" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 上传补丁对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传补丁" width="600px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="选择应用" required>
          <el-select v-model="uploadForm.app_id" placeholder="请选择应用" style="width: 100%">
            <el-option
              v-for="app in apps"
              :key="app.id"
              :label="app.app_name"
              :value="app.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="补丁版本" required>
          <el-input v-model="uploadForm.version" placeholder="例如: 1.0.1" />
        </el-form-item>
        <el-form-item label="基础版本" required>
          <el-input v-model="uploadForm.baseVersion" placeholder="例如: 1.0.0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="3" placeholder="补丁说明" />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="uploadForm.forceUpdate" />
        </el-form-item>
        <el-form-item label="补丁文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".zip"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">只能上传 zip 文件，且不超过 100MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">
          {{ uploading ? '上传中...' : '确定上传' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { api } from '@/api'

const loading = ref(false)
const patches = ref([])
const apps = ref([])
const filterAppId = ref(null)
const dialogVisible = ref(false)
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const selectedPatches = ref([])

const editForm = reactive({
  id: null,
  description: '',
  forceUpdate: false,
  rolloutPercentage: 100,
  status: 'active'
})

const uploadForm = reactive({
  app_id: null,
  version: '',
  baseVersion: '',
  description: '',
  forceUpdate: false
})

const pagination = reactive({
  page: 1,
  limit: 10,
  total: 0
})

onMounted(() => {
  loadApps()
  loadPatches()
})

const loadApps = async () => {
  try {
    const { data } = await api.getApps()
    // 只显示审核通过的应用
    apps.value = data.filter(app => app.review_status === 'approved')
  } catch (error) {
    console.error('加载应用列表失败:', error)
  }
}

const loadPatches = async () => {
  try {
    loading.value = true;
    const params = {
      page: pagination.page,
      limit: pagination.limit
    };
    
    if (filterAppId.value) {
      params.app_id = filterAppId.value;
    }
    
    const { data } = await api.getPatches(params);
    patches.value = data.patches || [];
    pagination.total = data.pagination?.total || 0;
  } catch (error) {
    console.error('加载补丁列表失败:', error);
  } finally {
    loading.value = false;
  }
};

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

const handleEdit = (row) => {
  editForm.id = row.id
  editForm.description = row.description
  editForm.forceUpdate = row.force_update === 1
  editForm.rolloutPercentage = row.rollout_percentage
  editForm.status = row.status
  dialogVisible.value = true
}

const handleUpdate = async () => {
  try {
    await api.updatePatch(editForm.id, {
      description: editForm.description,
      forceUpdate: editForm.forceUpdate,
      rolloutPercentage: editForm.rolloutPercentage,
      status: editForm.status
    })
    ElMessage.success('更新成功')
    dialogVisible.value = false
    loadPatches()
  } catch (error) {
    console.error('更新失败:', error)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个补丁吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await api.deletePatch(row.id)
    ElMessage.success('删除成功')
    loadPatches()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

const showUploadDialog = () => {
  if (apps.value.length === 0) {
    ElMessage.warning('请先创建应用')
    return
  }
  
  // 重置表单
  uploadForm.app_id = null
  uploadForm.version = ''
  uploadForm.baseVersion = ''
  uploadForm.description = ''
  uploadForm.forceUpdate = false
  selectedFile.value = null
  
  uploadDialogVisible.value = true
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const handleSelectionChange = (selection) => {
  selectedPatches.value = selection
}

const handleBatchCommand = async (command) => {
  if (selectedPatches.value.length === 0) {
    ElMessage.warning('请先选择补丁')
    return
  }
  
  try {
    let confirmMessage = ''
    let action = ''
    
    if (command === 'enable') {
      confirmMessage = `确定要启用选中的 ${selectedPatches.value.length} 个补丁吗？`
      action = 'enable'
    } else if (command === 'disable') {
      confirmMessage = `确定要停用选中的 ${selectedPatches.value.length} 个补丁吗？`
      action = 'disable'
    } else if (command === 'delete') {
      confirmMessage = `确定要删除选中的 ${selectedPatches.value.length} 个补丁吗？此操作不可恢复！`
      action = 'delete'
    }
    
    await ElMessageBox.confirm(confirmMessage, '批量操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const ids = selectedPatches.value.map(p => p.id)
    
    if (action === 'delete') {
      await api.post('/patches/batch-delete', { ids })
      ElMessage.success('批量删除成功')
    } else {
      const status = action === 'enable' ? 'active' : 'inactive'
      await api.post('/patches/batch-update-status', { ids, status })
      ElMessage.success(`批量${action === 'enable' ? '启用' : '停用'}成功`)
    }
    
    selectedPatches.value = []
    loadPatches()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
      console.error(error)
    }
  }
}

const handleUpload = async () => {
  if (!uploadForm.app_id) {
    ElMessage.warning('请选择应用')
    return
  }
  
  if (!uploadForm.version || !uploadForm.baseVersion) {
    ElMessage.warning('请填写版本号和基础版本号')
    return
  }
  
  if (!selectedFile.value) {
    ElMessage.warning('请选择补丁文件')
    return
  }
  
  try {
    uploading.value = true
    
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('app_id', uploadForm.app_id)
    formData.append('version', uploadForm.version)
    formData.append('baseVersion', uploadForm.baseVersion)
    formData.append('description', uploadForm.description)
    formData.append('forceUpdate', uploadForm.forceUpdate)
    
    await api.uploadPatch(formData, (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total)
      console.log('上传进度:', percentCompleted + '%')
    })
    
    ElMessage.success('补丁上传成功')
    uploadDialogVisible.value = false
    loadPatches()
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('上传失败: ' + (error.response?.data?.error || error.message))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
