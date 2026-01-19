<template>
  <div class="logs-container">
    <div class="logs-header">
      <h2>操作日志</h2>
      <el-button type="danger" @click="showCleanupDialog = true">清理旧日志</el-button>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="操作类型">
          <el-select v-model="filters.action" placeholder="全部" clearable style="width: 180px;">
            <el-option label="创建应用" value="create_app" />
            <el-option label="更新应用" value="update_app" />
            <el-option label="删除应用" value="delete_app" />
            <el-option label="启用/停用应用" value="toggle_app_status" />
            <el-option label="审核应用" value="review_app" />
            <el-option label="上传补丁" value="upload_patch" />
            <el-option label="生成补丁" value="generate_patch" />
            <el-option label="更新补丁" value="update_patch" />
            <el-option label="删除补丁" value="delete_patch" />
            <el-option label="启用/停用补丁" value="toggle_patch_status" />
            <el-option label="用户注册" value="register_user" />
            <el-option label="用户登录" value="login" />
            <el-option label="更新用户" value="update_user" />
            <el-option label="删除用户" value="delete_user" />
            <el-option label="修改用户角色" value="change_user_role" />
            <el-option label="更新系统配置" value="update_system_config" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="用户名">
          <el-input v-model="filters.username" placeholder="搜索用户" clearable style="width: 150px;" />
        </el-form-item>

        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 300px;"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="loadLogs">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="logs-table-card">
      <el-table :data="logs" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="操作" width="150">
          <template #default="{ row }">
            <el-tag :type="getActionType(row.action)">
              {{ getActionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resource_type" label="资源类型" width="120" />
        <el-table-column prop="resource_id" label="资源ID" width="100" />
        <el-table-column prop="ip_address" label="IP地址" width="150" />
        <el-table-column prop="created_at" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.created_at) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="showDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.limit"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadLogs"
        @size-change="loadLogs"
        style="margin-top: 20px; justify-content: center;"
      />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailsDialog" title="日志详情" width="600px">
      <el-descriptions :column="1" border v-if="selectedLog">
        <el-descriptions-item label="ID">{{ selectedLog.id }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ selectedLog.username }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ getActionLabel(selectedLog.action) }}</el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ selectedLog.resource_type || 'N/A' }}</el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ selectedLog.resource_id || 'N/A' }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ selectedLog.ip_address }}</el-descriptions-item>
        <el-descriptions-item label="User Agent">{{ selectedLog.user_agent }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDate(selectedLog.created_at) }}</el-descriptions-item>
        <el-descriptions-item label="详细信息">
          <pre style="max-height: 200px; overflow: auto;">{{ formatDetails(selectedLog.details) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 清理对话框 -->
    <el-dialog v-model="showCleanupDialog" title="清理旧日志" width="400px">
      <el-form :model="cleanupForm" label-width="120px">
        <el-form-item label="保留天数">
          <el-input-number v-model="cleanupForm.days" :min="7" :max="365" />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            将删除 {{ cleanupForm.days }} 天前的日志
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCleanupDialog = false">取消</el-button>
        <el-button type="danger" @click="cleanupLogs" :loading="cleaning">确认清理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import api from '../api';

const logs = ref([]);
const loading = ref(false);
const showDetailsDialog = ref(false);
const showCleanupDialog = ref(false);
const selectedLog = ref(null);
const cleaning = ref(false);
const dateRange = ref([]);

const filters = reactive({
  action: '',
  username: ''
});

const pagination = reactive({
  page: 1,
  limit: 50,
  total: 0
});

const cleanupForm = reactive({
  days: 30
});

const actionLabels = {
  create_app: '创建应用',
  update_app: '更新应用',
  delete_app: '删除应用',
  toggle_app_status: '启用/停用应用',
  review_app: '审核应用',
  upload_patch: '上传补丁',
  generate_patch: '生成补丁',
  update_patch: '更新补丁',
  delete_patch: '删除补丁',
  toggle_patch_status: '启用/停用补丁',
  register_user: '用户注册',
  login: '用户登录',
  update_user: '更新用户',
  delete_user: '删除用户',
  change_user_role: '修改用户角色',
  update_system_config: '更新系统配置'
};

const loadLogs = async () => {
  try {
    loading.value = true;
    const params = {
      page: pagination.page,
      limit: pagination.limit,
      ...filters
    };

    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0].toISOString();
      params.endDate = dateRange.value[1].toISOString();
    }

    const { data } = await api.get('/logs', { params });
    logs.value = data.logs;
    pagination.total = data.pagination.total;
  } catch (error) {
    ElMessage.error('加载日志失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.action = '';
  filters.username = '';
  dateRange.value = [];
  pagination.page = 1;
  loadLogs();
};

const showDetails = (log) => {
  selectedLog.value = log;
  showDetailsDialog.value = true;
};

const cleanupLogs = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除 ${cleanupForm.days} 天前的日志吗？此操作不可恢复。`,
      '警告',
      { type: 'warning' }
    );

    cleaning.value = true;
    const { data } = await api.delete('/logs/cleanup', {
      data: { days: cleanupForm.days }
    });

    ElMessage.success(`已删除 ${data.deletedCount} 条日志`);
    showCleanupDialog.value = false;
    loadLogs();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清理失败');
    }
  } finally {
    cleaning.value = false;
  }
};

const getActionLabel = (action) => {
  return actionLabels[action] || action;
};

const getActionType = (action) => {
  if (action.includes('delete')) return 'danger';
  if (action.includes('create') || action.includes('generate')) return 'success';
  if (action.includes('update')) return 'warning';
  return 'info';
};

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN');
};

const formatDetails = (details) => {
  try {
    return JSON.stringify(JSON.parse(details), null, 2);
  } catch {
    return details || 'N/A';
  }
};

onMounted(() => {
  loadLogs();
});
</script>

<style scoped>
.logs-container {
  padding: 24px;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.logs-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.filter-card {
  margin-bottom: 20px;
}

.logs-table-card {
  background: white;
  border-radius: 12px;
}

pre {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
