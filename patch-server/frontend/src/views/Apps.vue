<template>
  <div class="apps-container">
    <div class="page-header">
      <div>
        <h2>我的应用</h2>
        <p>管理你的 Android 应用和补丁</p>
      </div>
      <el-button type="primary" @click="showCreateDialog = true" size="large">
        <el-icon><Plus /></el-icon>
        创建应用
      </el-button>
    </div>

    <div class="apps-grid" v-if="apps.length > 0">
      <div 
        class="app-card" 
        v-for="app in apps" 
        :key="app.id" 
        @click="goToAppDetail(app)"
        :class="{ 
          'pending-review': app.review_status === 'pending',
          'inactive-app': app.status === 'inactive'
        }"
      >
        <div class="app-icon">
          <img v-if="app.icon" :src="app.icon" alt="">
          <el-icon v-else :size="40"><Box /></el-icon>
        </div>
        <div class="app-info">
          <h3>{{ app.app_name }}</h3>
          <p class="package-name">{{ app.package_name }}</p>
          <p class="app-desc" v-if="app.description">{{ app.description }}</p>
        </div>
        <div class="app-stats">
          <div class="stat-item">
            <span class="stat-value">{{ app.patch_count || 0 }}</span>
            <span class="stat-label">补丁</span>
          </div>
          <div class="stat-item">
            <el-tag 
              v-if="app.review_status === 'pending'" 
              type="warning" 
              size="small"
            >
              待审核
            </el-tag>
            <el-tag 
              v-else-if="app.review_status === 'rejected'" 
              type="danger" 
              size="small"
            >
              已拒绝
            </el-tag>
            <el-tag 
              v-else
              :type="app.status === 'active' ? 'success' : 'info'" 
              size="small"
            >
              {{ app.status === 'active' ? '活跃' : '停用' }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else description="还没有应用，创建一个开始吧" />

    <!-- 创建应用对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建应用"
      width="500px"
    >
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="100px">
        <el-form-item label="应用名称" prop="app_name">
          <el-input v-model="createForm.app_name" placeholder="如: 我的应用" />
        </el-form-item>
        <el-form-item label="包名" prop="package_name">
          <el-input v-model="createForm.package_name" placeholder="如: com.example.app" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="应用描述（可选）"
          />
        </el-form-item>
        <el-form-item label="图标 URL">
          <el-input v-model="createForm.icon" placeholder="图标地址（可选）" />
        </el-form-item>
        <el-alert
          title="App ID 将由系统自动生成"
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 10px;"
        />
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, Box } from '@element-plus/icons-vue';
import api from '../api';

const router = useRouter();
const apps = ref([]);
const showCreateDialog = ref(false);
const creating = ref(false);
const createFormRef = ref(null);

const createForm = reactive({
  app_name: '',
  package_name: '',
  description: '',
  icon: ''
});

const createRules = {
  app_name: [
    { required: true, message: '请输入应用名称', trigger: 'blur' }
  ],
  package_name: [
    { required: true, message: '请输入包名', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/, message: '请输入有效的包名', trigger: 'blur' }
  ]
};

const loadApps = async () => {
  try {
    const { data } = await api.get('/apps');
    apps.value = data;
  } catch (error) {
    ElMessage.error('加载应用列表失败');
  }
};

const handleCreate = async () => {
  try {
    await createFormRef.value.validate();
    creating.value = true;

    const response = await api.post('/apps', createForm);
    
    // 检查是否需要审核
    if (response.data.app?.review_status === 'pending') {
      ElMessage.warning('应用已提交，请等待管理员审核');
    } else {
      ElMessage.success(`应用创建成功，App ID: ${response.data.app?.app_id}`);
    }
    
    showCreateDialog.value = false;
    Object.assign(createForm, {
      app_name: '',
      package_name: '',
      description: '',
      icon: ''
    });
    
    loadApps();
  } catch (error) {
    ElMessage.error(error.response?.data?.error || '创建失败');
  } finally {
    creating.value = false;
  }
};

const goToAppDetail = (app) => {
  // 如果应用待审核或被拒绝，不允许进入
  if (app.review_status === 'pending') {
    ElMessage.warning('应用正在审核中，暂时无法管理');
    return;
  }
  if (app.review_status === 'rejected') {
    ElMessage.error('应用已被拒绝，无法管理');
    return;
  }
  // 如果应用已停用，不允许进入
  if (app.status === 'inactive') {
    ElMessage.warning('应用已停用，无法管理');
    return;
  }
  
  router.push(`/apps/${app.id}`);
};

onMounted(() => {
  loadApps();
});
</script>

<style scoped>
.apps-container {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
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

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.app-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  transition: all 0.3s;
}

.app-card:hover {
  border-color: #d4af7a;
  box-shadow: 0 4px 12px rgba(212, 175, 122, 0.15);
  transform: translateY(-2px);
}

.app-card.pending-review {
  opacity: 0.7;
  cursor: not-allowed;
}

.app-card.pending-review:hover {
  border-color: #e5e7eb;
  box-shadow: none;
  transform: none;
}

.app-card.inactive-app {
  opacity: 0.6;
  cursor: not-allowed;
}

.app-card.inactive-app:hover {
  border-color: #e5e7eb;
  box-shadow: none;
  transform: none;
}

.app-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  background: #d4af7a;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 16px;
}

.app-icon img {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  object-fit: cover;
}

.app-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 4px 0;
}

.package-name {
  font-size: 13px;
  color: #666;
  font-family: 'Courier New', monospace;
  margin: 0 0 8px 0;
}

.app-desc {
  font-size: 14px;
  color: #888;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.app-stats {
  display: flex;
  gap: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #d4af7a;
}

.stat-label {
  font-size: 12px;
  color: #888;
}
</style>
