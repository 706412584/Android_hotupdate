<template>
  <div class="patches">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>补丁列表</span>
          <el-button type="primary" @click="$router.push('/upload')">
            <el-icon><Upload /></el-icon>
            上传补丁
          </el-button>
        </div>
      </template>
      
      <el-table :data="patches" v-loading="loading">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const loading = ref(false)
const patches = ref([])
const dialogVisible = ref(false)
const editForm = reactive({
  id: null,
  description: '',
  forceUpdate: false,
  rolloutPercentage: 100,
  status: 'active'
})

const pagination = reactive({
  page: 1,
  limit: 10,
  total: 0
})

onMounted(() => {
  loadPatches()
})

const loadPatches = async () => {
  try {
    loading.value = true
    const data = await api.getPatches({
      page: pagination.page,
      limit: pagination.limit
    })
    patches.value = data.patches
    pagination.total = data.pagination.total
  } catch (error) {
    console.error('加载补丁列表失败:', error)
  } finally {
    loading.value = false
  }
}

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
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
