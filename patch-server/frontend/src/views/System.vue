<template>
  <div class="system-container">
    <h2>系统管理</h2>

    <el-tabs v-model="activeTab">
      <!-- 定时任务 -->
      <el-tab-pane label="定时任务" name="tasks">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>定时任务列表</span>
              <el-button size="small" @click="loadTasks">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <el-table :data="tasks" v-loading="loading">
            <el-table-column prop="name" label="任务名称" width="200">
              <template #default="{ row }">
                <el-tag v-if="row.name === 'backup'" type="primary">{{ getTaskLabel(row.name) }}</el-tag>
                <el-tag v-else-if="row.name === 'clean-logs'" type="warning">{{ getTaskLabel(row.name) }}</el-tag>
                <el-tag v-else type="info">{{ getTaskLabel(row.name) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="schedule" label="执行时间" width="150">
              <template #default="{ row }">
                <span style="font-family: monospace;">{{ row.schedule }}</span>
              </template>
            </el-table-column>
            <el-table-column label="说明">
              <template #default="{ row }">
                {{ getTaskDescription(row.name, row.schedule) }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '运行中' : '已停止' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button 
                  v-if="!row.enabled" 
                  size="small" 
                  type="success" 
                  @click="startTask(row.name)"
                >
                  启动
                </el-button>
                <el-button 
                  v-else 
                  size="small" 
                  type="warning" 
                  @click="stopTask(row.name)"
                >
                  停止
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 备份管理 -->
      <el-tab-pane label="备份管理" name="backups">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>备份文件列表</span>
              <div>
                <el-button size="small" type="primary" @click="runBackup" :loading="backing">
                  <el-icon><FolderAdd /></el-icon>
                  立即备份
                </el-button>
                <el-button size="small" @click="loadBackups">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="backups" v-loading="loading">
            <el-table-column prop="name" label="文件名" />
            <el-table-column prop="size" label="大小" width="120">
              <template #default="{ row }">
                {{ formatSize(row.size) }}
              </template>
            </el-table-column>
            <el-table-column prop="created" label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.created) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="restoreBackup(row.name)">
                  恢复
                </el-button>
                <el-popconfirm
                  title="确定要删除这个备份吗？"
                  @confirm="deleteBackup(row.name)"
                >
                  <template #reference>
                    <el-button size="small" type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="backups.length === 0 && !loading" description="暂无备份文件" />
        </el-card>
      </el-tab-pane>

      <!-- 数据清理 -->
      <el-tab-pane label="数据清理" name="clean">
        <el-card>
          <el-alert
            title="数据清理说明"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <ul style="margin: 0; padding-left: 20px;">
              <li>清理日志：删除 30 天前的操作日志</li>
              <li>清理下载记录：删除 90 天前的下载记录</li>
              <li>清理操作不可恢复，请谨慎操作</li>
            </ul>
          </el-alert>

          <el-space direction="vertical" :size="20" style="width: 100%;">
            <el-card shadow="hover">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <h3 style="margin: 0 0 8px 0;">清理操作日志</h3>
                  <p style="margin: 0; color: #999; font-size: 14px;">
                    删除 30 天前的操作日志，释放数据库空间
                  </p>
                </div>
                <el-button type="warning" @click="cleanLogs" :loading="cleaning.logs">
                  <el-icon><Delete /></el-icon>
                  清理日志
                </el-button>
              </div>
            </el-card>

            <el-card shadow="hover">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <h3 style="margin: 0 0 8px 0;">清理下载记录</h3>
                  <p style="margin: 0; color: #999; font-size: 14px;">
                    删除 90 天前的下载记录，释放数据库空间
                  </p>
                </div>
                <el-button type="warning" @click="cleanDownloads" :loading="cleaning.downloads">
                  <el-icon><Delete /></el-icon>
                  清理记录
                </el-button>
              </div>
            </el-card>
          </el-space>
        </el-card>
      </el-tab-pane>

      <!-- 通知配置 -->
      <el-tab-pane label="通知配置" name="notifications">
        <el-card>
          <el-alert
            title="通知说明"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <ul style="margin: 0; padding-left: 20px;">
              <li>邮件通知：通过 SMTP 发送邮件</li>
              <li>Webhook 通知：发送 HTTP POST 请求到指定 URL</li>
              <li>配置需要在 .env 文件中设置</li>
            </ul>
          </el-alert>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="邮件通知">
              <el-tag :type="notificationConfig.email?.enabled ? 'success' : 'info'">
                {{ notificationConfig.email?.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="SMTP 服务器" v-if="notificationConfig.email?.enabled">
              {{ notificationConfig.email?.host }}:{{ notificationConfig.email?.port }}
            </el-descriptions-item>
            <el-descriptions-item label="发件人" v-if="notificationConfig.email?.enabled">
              {{ notificationConfig.email?.from }}
            </el-descriptions-item>
            <el-descriptions-item label="收件人" v-if="notificationConfig.email?.enabled">
              {{ notificationConfig.email?.recipients?.join(', ') || '未配置' }}
            </el-descriptions-item>
            <el-descriptions-item label="Webhook 通知">
              <el-tag :type="notificationConfig.webhook?.enabled ? 'success' : 'info'">
                {{ notificationConfig.webhook?.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Webhook URL" v-if="notificationConfig.webhook?.enabled">
              {{ notificationConfig.webhook?.url }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider />

          <el-space>
            <el-button @click="loadNotificationConfig">
              <el-icon><Refresh /></el-icon>
              刷新配置
            </el-button>
            <el-button 
              type="primary" 
              @click="showTestEmailDialog = true"
              :disabled="!notificationConfig.email?.enabled"
            >
              测试邮件
            </el-button>
            <el-button 
              type="primary" 
              @click="testWebhook"
              :disabled="!notificationConfig.webhook?.enabled"
            >
              测试 Webhook
            </el-button>
          </el-space>
        </el-card>
      </el-tab-pane>

      <!-- 审核设置 -->
      <el-tab-pane label="审核设置" name="review">
        <el-card>
          <el-alert
            title="审核设置说明"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <ul style="margin: 0; padding-left: 20px;">
              <li>启用审核后，普通用户创建的应用需要管理员审核通过后才能使用</li>
              <li>管理员创建的应用可以设置为自动通过审核</li>
              <li>待审核的应用可以在"应用审核"标签中查看和处理</li>
            </ul>
          </el-alert>

          <el-form label-width="150px">
            <el-form-item label="启用应用审核">
              <el-switch 
                v-model="reviewSettings.app_review_enabled" 
                @change="handleReviewSettingChange"
              />
              <span style="margin-left: 12px; color: #999; font-size: 14px;">
                {{ reviewSettings.app_review_enabled ? '已启用' : '已关闭' }}
              </span>
            </el-form-item>

            <el-form-item label="管理员自动通过">
              <el-switch 
                v-model="reviewSettings.auto_approve_admin" 
                @change="handleReviewSettingChange"
                :disabled="!reviewSettings.app_review_enabled"
              />
              <span style="margin-left: 12px; color: #999; font-size: 14px;">
                管理员创建的应用自动通过审核
              </span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 应用审核 -->
      <el-tab-pane label="应用审核" name="app-review">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>待审核应用</span>
              <el-button size="small" @click="loadPendingApps">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <el-table :data="pendingApps" v-loading="loadingPending">
            <el-table-column prop="app_name" label="应用名称" width="150" />
            <el-table-column prop="package_name" label="包名" width="200" />
            <el-table-column prop="owner_name" label="创建者" width="120" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="created_at" label="提交时间" width="180" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" @click="handleReview(row, 'approve')">
                  通过
                </el-button>
                <el-button size="small" type="danger" @click="handleReview(row, 'reject')">
                  拒绝
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="pendingApps.length === 0 && !loadingPending" description="暂无待审核应用" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 测试邮件对话框 -->
    <el-dialog v-model="showTestEmailDialog" title="测试邮件" width="500px">
      <el-form label-width="100px">
        <el-form-item label="收件人">
          <el-input v-model="testEmail" placeholder="请输入邮箱地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTestEmailDialog = false">取消</el-button>
        <el-button type="primary" @click="sendTestEmail" :loading="testing.email">
          发送测试邮件
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, FolderAdd, Delete } from '@element-plus/icons-vue';
import { api } from '@/api';

const activeTab = ref('tasks');
const loading = ref(false);
const backing = ref(false);
const tasks = ref([]);
const backups = ref([]);
const cleaning = reactive({
  logs: false,
  downloads: false
});

const notificationConfig = ref({});
const showTestEmailDialog = ref(false);
const testEmail = ref('');
const testing = reactive({
  email: false,
  webhook: false
});

const reviewSettings = reactive({
  app_review_enabled: false,
  auto_approve_admin: true
});

const pendingApps = ref([]);
const loadingPending = ref(false);

const taskLabels = {
  'backup': '数据备份',
  'clean-logs': '日志清理',
  'clean-downloads': '记录清理'
};

const getTaskLabel = (name) => {
  return taskLabels[name] || name;
};

const getTaskDescription = (name, schedule) => {
  const descriptions = {
    'backup': '每天凌晨 2 点自动备份数据库和文件',
    'clean-logs': '每周日凌晨 3 点清理 30 天前的日志',
    'clean-downloads': '每月 1 号凌晨 4 点清理 90 天前的下载记录'
  };
  return descriptions[name] || `定时执行 (${schedule})`;
};

const loadTasks = async () => {
  try {
    loading.value = true;
    const { data } = await api.getTasks();
    tasks.value = data.tasks;
  } catch (error) {
    ElMessage.error('加载任务列表失败');
  } finally {
    loading.value = false;
  }
};

const startTask = async (name) => {
  try {
    await api.startTask(name);
    ElMessage.success(`任务 ${getTaskLabel(name)} 已启动`);
    loadTasks();
  } catch (error) {
    ElMessage.error('启动任务失败');
  }
};

const stopTask = async (name) => {
  try {
    await api.stopTask(name);
    ElMessage.success(`任务 ${getTaskLabel(name)} 已停止`);
    loadTasks();
  } catch (error) {
    ElMessage.error('停止任务失败');
  }
};

const loadBackups = async () => {
  try {
    loading.value = true;
    const { data } = await api.getBackups();
    backups.value = data.backups;
  } catch (error) {
    ElMessage.error('加载备份列表失败');
  } finally {
    loading.value = false;
  }
};

const runBackup = async () => {
  try {
    backing.value = true;
    await api.runBackup();
    ElMessage.success('备份完成');
    loadBackups();
  } catch (error) {
    ElMessage.error('备份失败');
  } finally {
    backing.value = false;
  }
};

const deleteBackup = async (filename) => {
  try {
    await api.deleteBackup(filename);
    ElMessage.success('备份已删除');
    loadBackups();
  } catch (error) {
    ElMessage.error('删除失败');
  }
};

const restoreBackup = async (filename) => {
  try {
    await ElMessageBox.confirm(
      `确定要从备份 "${filename}" 恢复数据吗？\n\n⚠️ 警告：\n1. 当前数据将被覆盖\n2. 恢复后需要重启服务\n3. 恢复过程中请勿操作系统\n\n建议先备份当前数据！`,
      '确认恢复',
      {
        confirmButtonText: '确定恢复',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: true
      }
    );

    const { data } = await api.restoreBackup(filename);
    
    ElMessageBox.alert(
      data.message + '\n\n' + (data.warning || ''),
      '恢复任务已启动',
      {
        confirmButtonText: '知道了',
        type: 'success'
      }
    );
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('恢复失败: ' + (error.response?.data?.error || error.message));
    }
  }
};

const cleanLogs = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清理 30 天前的操作日志吗？此操作不可恢复。',
      '确认清理',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );

    cleaning.logs = true;
    await api.cleanLogs();
    ElMessage.success('日志清理完成');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清理失败');
    }
  } finally {
    cleaning.logs = false;
  }
};

const cleanDownloads = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清理 90 天前的下载记录吗？此操作不可恢复。',
      '确认清理',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );

    cleaning.downloads = true;
    await api.cleanDownloads();
    ElMessage.success('下载记录清理完成');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清理失败');
    }
  } finally {
    cleaning.downloads = false;
  }
};

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
};

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN');
};

const loadNotificationConfig = async () => {
  try {
    const { data } = await api.getNotificationConfig();
    notificationConfig.value = data;
  } catch (error) {
    // 静默处理 404 错误（功能未实现）
    if (error.response?.status !== 404) {
      console.error('加载通知配置失败:', error);
    }
  }
};

const sendTestEmail = async () => {
  if (!testEmail.value) {
    ElMessage.warning('请输入邮箱地址');
    return;
  }

  try {
    testing.email = true;
    await api.testEmail(testEmail.value);
    ElMessage.success('测试邮件已发送，请检查收件箱');
    showTestEmailDialog.value = false;
  } catch (error) {
    ElMessage.error('发送失败: ' + (error.response?.data?.error || error.message));
  } finally {
    testing.email = false;
  }
};

const testWebhook = async () => {
  try {
    testing.webhook = true;
    await api.testWebhook();
    ElMessage.success('Webhook 测试请求已发送');
  } catch (error) {
    ElMessage.error('发送失败: ' + (error.response?.data?.error || error.message));
  } finally {
    testing.webhook = false;
  }
};

const loadReviewSettings = async () => {
  try {
    const { data } = await api.getSystemConfig();
    reviewSettings.app_review_enabled = data.app_review_enabled?.value === 'true';
    reviewSettings.auto_approve_admin = data.auto_approve_admin?.value === 'true';
  } catch (error) {
    console.error('加载审核设置失败:', error);
  }
};

const handleReviewSettingChange = async () => {
  try {
    await api.batchUpdateSystemConfig({
      app_review_enabled: reviewSettings.app_review_enabled ? 'true' : 'false',
      auto_approve_admin: reviewSettings.auto_approve_admin ? 'true' : 'false'
    });
    ElMessage.success('设置已保存');
  } catch (error) {
    ElMessage.error('保存失败');
    loadReviewSettings(); // 恢复原值
  }
};

const loadPendingApps = async () => {
  try {
    loadingPending.value = true;
    const { data } = await api.getPendingApps();
    pendingApps.value = data || [];
  } catch (error) {
    console.error('加载待审核应用失败:', error);
  } finally {
    loadingPending.value = false;
  }
};

const handleReview = async (app, action) => {
  try {
    let note = '';
    if (action === 'reject') {
      const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝审核', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /.+/,
        inputErrorMessage: '请输入拒绝原因'
      });
      note = value;
    } else {
      await ElMessageBox.confirm(
        `确定要通过应用 "${app.app_name}" 的审核吗？`,
        '通过审核',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success'
        }
      );
    }
    
    await api.reviewApp(app.id, action, note);
    ElMessage.success(action === 'approve' ? '审核通过' : '已拒绝');
    loadPendingApps();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
    }
  }
};

onMounted(() => {
  loadTasks();
  loadBackups();
  loadNotificationConfig();
  loadReviewSettings();
  loadPendingApps();
});
</script>

<style scoped>
.system-container {
  padding: 24px;
}
</style>
