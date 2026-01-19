<template>
  <div class="all-apps-container">
    <div class="page-header">
      <div>
        <h2>应用列表</h2>
        <p>管理所有用户的应用</p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="应用名称">
          <el-input 
            v-model="filters.app_name" 
            placeholder="搜索应用名称"
            clearable
            @clear="loadApps"
          />
        </el-form-item>
        <el-form-item label="包名">
          <el-input 
            v-model="filters.package_name" 
            placeholder="搜索包名"
            clearable
            @clear="loadApps"
          />
        </el-form-item>
        <el-form-item label="所有者">
          <el-input 
            v-model="filters.owner_name" 
            placeholder="搜索用户名"
            clearable
            @clear="loadApps"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select 
            v-model="filters.status" 
            placeholder="全部状态"
            clearable
            @clear="loadApps"
          >
            <el-option label="活跃" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select 
            v-model="filters.review_status" 
            placeholder="全部"
            clearable
            @clear="loadApps"
          >
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已拒绝" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filters.sort_by" @change="loadApps">
            <el-option label="创建时间（新到旧）" value="created_at_desc" />
            <el-option label="创建时间（旧到新）" value="created_at_asc" />
            <el-option label="应用名称（A-Z）" value="app_name_asc" />
            <el-option label="应用名称（Z-A）" value="app_name_desc" />
            <el-option label="补丁数量（多到少）" value="patch_count_desc" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadApps">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 应用表格 -->
    <el-card class="table-card">
      <el-table 
        :data="apps" 
        v-loading="loading"
        stripe
        @row-click="handleRowClick"
        style="cursor: pointer;"
      >
        <el-table-column prop="app_name" label="应用名称" min-width="150">
          <template #default="{ row }">
            <div class="app-name-cell">
              <div class="app-icon-small">
                <img v-if="row.icon" :src="row.icon" alt="">
                <el-icon v-else :size="24"><Box /></el-icon>
              </div>
              <div>
                <div class="app-name">{{ row.app_name }}</div>
                <div class="package-name-small">{{ row.package_name }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="app_id" label="App ID" width="150" />
        <el-table-column prop="owner_name" label="所有者" width="120" />
        <el-table-column prop="patch_count" label="补丁数" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '活跃' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="review_status" label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag 
              v-if="row.review_status === 'pending'" 
              type="warning" 
              size="small"
            >
              待审核
            </el-tag>
            <el-tag 
              v-else-if="row.review_status === 'rejected'" 
              type="danger" 
              size="small"
            >
              已拒绝
            </el-tag>
            <el-tag 
              v-else
              type="success" 
              size="small"
            >
              已通过
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="goToAppDetail(row.id)">查看</el-button>
            <el-button 
              size="small" 
              :type="row.status === 'active' ? 'warning' : 'success'"
              @click.stop="toggleAppStatus(row)"
            >
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
            <el-button 
              size="small" 
              type="danger"
              @click.stop="deleteApp(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.limit"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadApps"
        @size-change="loadApps"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Box } from '@element-plus/icons-vue';
import api from '../api';

const router = useRouter();
const apps = ref([]);
const loading = ref(false);

const filters = reactive({
  app_name: '',
  package_name: '',
  owner_name: '',
  status: '',
  review_status: '',
  sort_by: 'created_at_desc'
});

const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
});

const loadApps = async () => {
  try {
    loading.value = true;
    const params = {
      page: pagination.page,
      limit: pagination.limit,
      ...filters
    };
    
    // 移除空值
    Object.keys(params).forEach(key => {
      if (params[key] === '' || params[key] === null || params[key] === undefined) {
        delete params[key];
      }
    });
    
    const { data } = await api.get('/apps/admin/all', { params });
    apps.value = data.apps || [];
    pagination.total = data.pagination?.total || 0;
  } catch (error) {
    ElMessage.error('加载应用列表失败');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.app_name = '';
  filters.package_name = '';
  filters.owner_name = '';
  filters.status = '';
  filters.review_status = '';
  filters.sort_by = 'created_at_desc';
  pagination.page = 1;
  loadApps();
};

const handleRowClick = (row) => {
  goToAppDetail(row.id);
};

const goToAppDetail = (id) => {
  router.push(`/apps/${id}`);
};

const toggleAppStatus = async (app) => {
  try {
    const newStatus = app.status === 'active' ? 'inactive' : 'active';
    const action = newStatus === 'inactive' ? '停用' : '启用';
    
    await ElMessageBox.confirm(
      `确定要${action}应用"${app.app_name}"吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    
    await api.put(`/apps/${app.id}/status`, { status: newStatus });
    ElMessage.success(`应用已${action}`);
    loadApps();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
      console.error(error);
    }
  }
};

const deleteApp = async (app) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除应用"${app.app_name}"吗？此操作将同时删除该应用的所有补丁，且无法恢复！`,
      '危险操作',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    );
    
    await api.delete(`/apps/${app.id}`);
    ElMessage.success('应用已删除');
    loadApps();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
      console.error(error);
    }
  }
};

onMounted(() => {
  loadApps();
});
</script>

<style scoped>
.all-apps-container {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 4px 0;
}

.page-header p {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.filter-card {
  margin-bottom: 20px;
}

.table-card {
  background: white;
}

.app-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-icon-small {
  width: 40px;
  height: 40px;
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

.app-name {
  font-weight: 500;
  color: #1a1a1a;
}

.package-name-small {
  font-size: 12px;
  color: #666;
  font-family: 'Courier New', monospace;
}
</style>
