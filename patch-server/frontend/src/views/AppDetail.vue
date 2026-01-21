<template>
  <div class="app-detail-container" v-if="app">
    <div class="app-header">
      <el-button @click="$router.back()" text>
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      
      <div class="app-title">
        <div class="app-icon">
          <img v-if="app.icon" :src="app.icon" alt="">
          <el-icon v-else :size="32"><Box /></el-icon>
        </div>
        <div>
          <h2>{{ app.app_name }}</h2>
          <p>{{ app.package_name }}</p>
        </div>
      </div>

      <el-button type="primary" @click="showUploadDialog = true">
        <el-icon><Upload /></el-icon>
        上传补丁
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="app-tabs">
      <el-tab-pane label="补丁列表" name="patches">
        <div class="patches-list" v-if="app.patches && app.patches.length > 0">
          <div class="patch-item" v-for="patch in app.patches" :key="patch.id">
            <div class="patch-info">
              <div class="patch-version">
                <el-tag type="primary" size="large">v{{ patch.version }}</el-tag>
                <el-icon v-if="patch.force_update" color="#f56c6c"><Warning /></el-icon>
                <el-tag v-if="patch.rollout_percentage < 100" type="warning" size="small" style="margin-left: 8px;">
                  灰度 {{ patch.rollout_percentage }}%
                </el-tag>
              </div>
              <div class="patch-details">
                <p class="patch-desc">{{ patch.description || '无描述' }}</p>
                <div class="patch-meta">
                  <span>基础版本: {{ patch.base_version }}</span>
                  <span>大小: {{ formatSize(patch.file_size) }}</span>
                  <span>下载: {{ patch.download_count }}</span>
                  <span>成功率: {{ calculateSuccessRate(patch) }}</span>
                  <span>{{ formatDate(patch.created_at) }}</span>
                </div>
              </div>
            </div>
            <div class="patch-actions">
              <el-tag :type="patch.status === 'active' ? 'success' : 'info'">
                {{ patch.status === 'active' ? '活跃' : '停用' }}
              </el-tag>
              <el-button size="small" @click="showRolloutDialog(patch)">灰度配置</el-button>
              <el-button size="small" @click="downloadPatch(patch)">下载</el-button>
              <el-button size="small" type="danger" @click="deletePatch(patch.id)">删除</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有补丁" />
      </el-tab-pane>

      <el-tab-pane label="应用设置" name="settings">
        <el-form :model="app" label-width="140px" class="settings-form">
          <el-divider content-position="left">基本信息</el-divider>
          <el-form-item label="App ID">
            <el-input v-model="app.app_id" disabled>
              <template #append>
                <el-button @click="copyAppId" :icon="DocumentCopy">复制</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="应用名称">
            <el-input v-model="app.app_name" />
          </el-form-item>
          <el-form-item label="包名">
            <el-input v-model="app.package_name" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="app.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="图标 URL">
            <el-input v-model="app.icon" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="app.status">
              <el-radio label="active">活跃</el-radio>
              <el-radio label="inactive">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <el-divider content-position="left">强制更新配置</el-divider>
          <el-alert
            title="强制大版本更新"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <p style="margin: 0;">当用户版本低于设定的最新版本时，将强制用户更新到最新版本，无法使用热更新补丁。</p>
          </el-alert>
          
          <el-form-item label="启用强制更新">
            <el-switch v-model="app.force_update_enabled" :active-value="1" :inactive-value="0" />
            <span style="margin-left: 12px; color: #909399; font-size: 13px;">
              开启后，低于最新版本的用户将被强制更新
            </span>
          </el-form-item>
          
          <el-form-item label="最新版本号" v-if="app.force_update_enabled">
            <el-input v-model="app.latest_version" placeholder="如: 1.5.0">
              <template #prepend>v</template>
            </el-input>
            <div style="color: #909399; font-size: 12px; margin-top: 4px;">
              低于此版本的用户将被强制更新
            </div>
          </el-form-item>
          
          <el-form-item label="下载地址" v-if="app.force_update_enabled">
            <el-input v-model="app.force_update_url" placeholder="APK 下载地址" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px;">
              可以使用版本管理中上传的 APK，或填写外部下载链接
            </div>
          </el-form-item>
          
          <el-form-item label="更新提示" v-if="app.force_update_enabled">
            <el-input 
              v-model="app.force_update_message" 
              type="textarea" 
              :rows="3"
              placeholder="发现新版本，请更新到最新版本"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button type="primary" @click="updateApp">保存设置</el-button>
            <el-button type="danger" @click="deleteApp">删除应用</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="版本管理" name="versions">
        <div class="versions-section">
          <div class="section-header">
            <div>
              <h3>大版本管理</h3>
              <p style="color: #909399; margin: 4px 0 0 0;">管理应用的完整 APK 版本，用于强制更新</p>
            </div>
            <el-button type="primary" @click="showUploadVersionDialog = true">
              <el-icon><Upload /></el-icon>
              上传新版本
            </el-button>
          </div>

          <div class="versions-list" v-if="versions && versions.length > 0" v-loading="versionsLoading">
            <div class="version-item" v-for="version in versions" :key="version.id">
              <div class="version-info">
                <div class="version-header">
                  <el-tag type="primary" size="large">v{{ version.version_name }}</el-tag>
                  <el-tag size="small" style="margin-left: 8px;">Code: {{ version.version_code }}</el-tag>
                  <el-icon v-if="version.is_force_update" color="#f56c6c" style="margin-left: 8px;">
                    <Warning />
                  </el-icon>
                  <span v-if="version.is_force_update" style="color: #f56c6c; font-size: 13px; margin-left: 4px;">
                    强制更新
                  </span>
                </div>
                <div class="version-details">
                  <p class="version-desc">{{ version.description || '无描述' }}</p>
                  <div class="version-changelog" v-if="version.changelog">
                    <strong>更新说明：</strong>
                    <pre>{{ version.changelog }}</pre>
                  </div>
                  <div class="version-meta">
                    <span>大小: {{ formatSize(version.file_size) }}</span>
                    <span>下载: {{ version.download_count }}</span>
                    <span>MD5: {{ version.md5.substring(0, 8) }}...</span>
                    <span>{{ formatDate(version.created_at) }}</span>
                    <span v-if="version.creator_name">上传者: {{ version.creator_name }}</span>
                  </div>
                </div>
              </div>
              <div class="version-actions">
                <el-tag :type="version.status === 'active' ? 'success' : 'info'">
                  {{ version.status === 'active' ? '活跃' : '停用' }}
                </el-tag>
                <el-button size="small" @click="editVersion(version)">编辑</el-button>
                <el-button size="small" @click="downloadVersion(version)">下载</el-button>
                <el-button size="small" @click="copyVersionUrl(version)">复制链接</el-button>
                <el-button size="small" type="danger" @click="deleteVersion(version.id)">删除</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="还没有上传版本" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="API 对接" name="api">
        <div class="api-docs">
          <el-alert
            title="客户端对接指南"
            type="info"
            :closable="false"
            style="margin-bottom: 24px;"
          >
            <p style="margin: 0;">使用以下 API 接口在您的 Android 应用中集成热更新功能。</p>
          </el-alert>

          <!-- 基本信息 -->
          <div class="api-section">
            <h3>基本信息</h3>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="App ID">
                <div class="copy-field">
                  <code>{{ app.app_id }}</code>
                  <el-button size="small" text @click="copyText(app.app_id)">
                    <el-icon><DocumentCopy /></el-icon>
                  </el-button>
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="API 地址">
                <div class="copy-field">
                  <code>{{ apiBaseUrl }}</code>
                  <el-button size="small" text @click="copyText(apiBaseUrl)">
                    <el-icon><DocumentCopy /></el-icon>
                  </el-button>
                </div>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 检查更新 API -->
          <div class="api-section">
            <h3>1. 检查更新</h3>
            <p class="api-desc">客户端调用此接口检查是否有可用的补丁更新。</p>
            
            <div class="api-block">
              <div class="api-header">
                <el-tag type="success">GET</el-tag>
                <code class="api-url">/api/client/check-update</code>
                <el-button size="small" @click="copyApiExample('checkUpdate')">
                  <el-icon><DocumentCopy /></el-icon>
                  复制示例
                </el-button>
              </div>
              
              <el-collapse>
                <el-collapse-item title="请求参数" name="1">
                  <el-table :data="checkUpdateParams" size="small" border>
                    <el-table-column prop="name" label="参数名" width="150" />
                    <el-table-column prop="type" label="类型" width="100" />
                    <el-table-column prop="required" label="必填" width="80">
                      <template #default="{ row }">
                        <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                          {{ row.required ? '是' : '否' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="desc" label="说明" />
                  </el-table>
                </el-collapse-item>
                
                <el-collapse-item title="请求示例" name="2">
                  <pre class="code-block">{{ checkUpdateExample }}</pre>
                </el-collapse-item>
                
                <el-collapse-item title="响应示例" name="3">
                  <pre class="code-block">{{ checkUpdateResponse }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <!-- 下载补丁 API -->
          <div class="api-section">
            <h3>2. 下载补丁</h3>
            <p class="api-desc">获取到更新信息后，使用 download_url 下载补丁文件。</p>
            
            <div class="api-block">
              <div class="api-header">
                <el-tag type="success">GET</el-tag>
                <code class="api-url">/downloads/{file_name}</code>
              </div>
              
              <el-collapse>
                <el-collapse-item title="下载示例" name="1">
                  <pre class="code-block">{{ downloadExample }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <!-- 上报结果 API -->
          <div class="api-section">
            <h3>3. 上报应用结果</h3>
            <p class="api-desc">补丁应用完成后，上报应用结果（成功或失败）。</p>
            
            <div class="api-block">
              <div class="api-header">
                <el-tag type="primary">POST</el-tag>
                <code class="api-url">/api/client/report</code>
                <el-button size="small" @click="copyApiExample('report')">
                  <el-icon><DocumentCopy /></el-icon>
                  复制示例
                </el-button>
              </div>
              
              <el-collapse>
                <el-collapse-item title="请求参数" name="1">
                  <el-table :data="reportParams" size="small" border>
                    <el-table-column prop="name" label="参数名" width="150" />
                    <el-table-column prop="type" label="类型" width="100" />
                    <el-table-column prop="required" label="必填" width="80">
                      <template #default="{ row }">
                        <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                          {{ row.required ? '是' : '否' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="desc" label="说明" />
                  </el-table>
                </el-collapse-item>
                
                <el-collapse-item title="请求示例" name="2">
                  <pre class="code-block">{{ reportExample }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <!-- Android 集成示例 -->
          <div class="api-section">
            <h3>Android 客户端集成示例</h3>
            <el-tabs>
              <el-tab-pane label="Kotlin">
                <div class="code-actions">
                  <el-button size="small" @click="copyText(kotlinExample)">
                    <el-icon><DocumentCopy /></el-icon>
                    复制代码
                  </el-button>
                </div>
                <pre class="code-block">{{ kotlinExample }}</pre>
              </el-tab-pane>
              
              <el-tab-pane label="Java">
                <div class="code-actions">
                  <el-button size="small" @click="copyText(javaExample)">
                    <el-icon><DocumentCopy /></el-icon>
                    复制代码
                  </el-button>
                </div>
                <pre class="code-block">{{ javaExample }}</pre>
              </el-tab-pane>
            </el-tabs>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="生成补丁" name="generate">
        <div class="generate-section">
          <!-- patch-cli 状态检查 -->
          <el-alert
            v-if="!patchCliAvailable && patchCliChecked"
            title="patch-cli 不可用"
            type="error"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <div style="line-height: 1.8;">
              <p style="margin: 0 0 12px 0; font-weight: 600;">自动生成补丁功能需要以下环境：</p>
              <ul style="margin: 0; padding-left: 20px;">
                <li><strong>Java 11+</strong>：运行 patch-cli 需要 Java 环境</li>
                <li><strong>patch-cli JAR</strong>：补丁生成工具</li>
              </ul>
              <p style="margin: 12px 0 0 0; color: #666;">
                <strong>解决方案：</strong>
              </p>
              <ol style="margin: 4px 0 0 0; padding-left: 20px; color: #666;">
                <li>检查服务器是否安装 Java：<code>java -version</code></li>
                <li>确认 patch-cli JAR 文件路径配置正确</li>
                <li>或者使用"上传补丁"功能手动上传本地生成的补丁</li>
              </ol>
              <p style="margin: 12px 0 0 0;">
                <el-link type="primary" href="https://github.com/706412584/Android_hotupdate/blob/main/patch-server/docs/PATCH-CLI-INTEGRATION.md" target="_blank">
                  查看完整配置指南 →
                </el-link>
              </p>
            </div>
          </el-alert>

          <el-alert
            v-else-if="patchCliAvailable"
            title="patch-cli 已就绪"
            type="success"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <p style="margin: 0;">✅ Java 环境和 patch-cli 工具已配置，可以自动生成补丁。</p>
          </el-alert>

          <el-alert
            v-else
            title="正在检查 patch-cli 环境..."
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <p style="margin: 0;">请稍候...</p>
          </el-alert>

          <el-alert
            v-if="patchCliAvailable"
            title="使用 patch-cli 自动生成补丁"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <p style="margin: 0;">上传基准 APK 和新版本 APK，服务端将自动生成补丁文件。</p>
            <p style="margin: 8px 0 0 0;">
              <span v-if="app.require_signature && app.keystore_path">
                ✅ 已配置签名，生成的补丁将自动签名
              </span>
              <span v-else-if="app.require_signature && !app.keystore_path" style="color: #e6a23c;">
                ⚠️ 已开启签名验证，但未上传 Keystore 文件，补丁将不会签名
              </span>
              <span v-else style="color: #909399;">
                ℹ️ 未配置签名，生成的补丁不会签名（可在"安全配置"中配置）
              </span>
            </p>
          </el-alert>

          <el-form 
            v-if="patchCliAvailable" 
            :model="generateForm" 
            label-width="120px" 
            class="settings-form"
          >
            <el-form-item label="版本号" required>
              <el-input v-model="generateForm.version" placeholder="如: 1.0.1" />
            </el-form-item>
            
            <el-form-item label="基础版本" required>
              <el-input v-model="generateForm.base_version" placeholder="如: 1.0.0" />
            </el-form-item>

            <el-form-item label="基准 APK" required>
              <el-upload
                ref="baseApkRef"
                :auto-upload="false"
                :limit="1"
                accept=".apk"
                :on-change="handleBaseApkChange"
              >
                <el-button>选择基准 APK</el-button>
                <template #tip>
                  <div style="font-size: 12px; color: #999; margin-top: 4px;">
                    当前应用版本的 APK 文件
                  </div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="新版本 APK" required>
              <el-upload
                ref="newApkRef"
                :auto-upload="false"
                :limit="1"
                accept=".apk"
                :on-change="handleNewApkChange"
              >
                <el-button>选择新版本 APK</el-button>
                <template #tip>
                  <div style="font-size: 12px; color: #999; margin-top: 4px;">
                    要更新到的新版本 APK 文件
                  </div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="描述">
              <el-input v-model="generateForm.description" type="textarea" :rows="3" placeholder="补丁更新内容说明" />
            </el-form-item>

            <el-form-item label="强制更新">
              <el-switch v-model="generateForm.force_update" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleGenerate" :loading="generating">
                <el-icon v-if="!generating"><Tools /></el-icon>
                {{ generating ? '生成中...' : '生成补丁' }}
              </el-button>
            </el-form-item>
          </el-form>

          <el-progress 
            v-if="generateProgress > 0" 
            :percentage="generateProgress" 
            :status="generateProgress === 100 ? 'success' : undefined"
            style="margin-top: 20px;"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="安全配置" name="security">
        <el-form :model="app" label-width="140px" class="settings-form">
          <el-alert
            title="安全建议"
            type="info"
            :closable="false"
            style="margin-bottom: 20px;"
          >
            <ul style="margin: 0; padding-left: 20px;">
              <li>APK 签名验证：防止补丁被篡改，推荐开启</li>
              <li>补丁加密：保护补丁内容，敏感应用建议开启</li>
              <li>开发测试时可以关闭验证</li>
            </ul>
          </el-alert>

          <el-form-item label="APK 签名验证">
            <el-switch v-model="app.require_signature" />
            <div style="font-size: 12px; color: #999; margin-top: 4px;">
              开启后，只能应用已签名的补丁
            </div>
          </el-form-item>

          <template v-if="app.require_signature">
            <el-divider content-position="left">JKS 签名配置</el-divider>
            
            <el-form-item label="Keystore 文件">
              <el-upload
                ref="keystoreUploadRef"
                :auto-upload="false"
                :limit="1"
                accept=".jks,.keystore,.bks"
                :on-change="handleKeystoreChange"
                :file-list="keystoreFileList"
              >
                <el-button size="small">
                  <el-icon><Upload /></el-icon>
                  选择 Keystore 文件
                </el-button>
                <template #tip>
                  <div style="font-size: 12px; color: #999; margin-top: 4px;">
                    支持 .jks、.keystore 或 .bks 文件
                  </div>
                </template>
              </el-upload>
              <div v-if="app.keystore_path" style="margin-top: 8px; font-size: 13px; color: #67c23a;">
                ✓ 已上传：{{ app.keystore_path.split('/').pop() || app.keystore_path.split('\\').pop() }}
              </div>
            </el-form-item>

            <el-form-item label="密钥库密码">
              <el-input 
                v-model="app.keystore_password" 
                type="password" 
                show-password
                placeholder="Keystore Password"
              />
            </el-form-item>

            <el-form-item label="密钥别名">
              <el-input 
                v-model="app.key_alias" 
                placeholder="Key Alias"
              />
            </el-form-item>

            <el-form-item label="密钥密码">
              <el-input 
                v-model="app.key_password" 
                type="password" 
                show-password
                placeholder="Key Password"
              />
            </el-form-item>
          </template>

          <el-form-item label="强制补丁加密">
            <el-switch v-model="app.require_encryption" />
            <div style="font-size: 12px; color: #999; margin-top: 4px;">
              开启后，只能应用已加密的补丁
            </div>
          </el-form-item>

          <template v-if="app.require_encryption">
            <el-divider content-position="left">加密配置</el-divider>
            
            <el-form-item label="加密密钥">
              <el-input 
                v-model="encryptionKey" 
                type="password" 
                show-password
                placeholder="64 位十六进制密钥"
                style="width: 400px;"
              >
                <template #append>
                  <el-button @click="generateKey" :loading="generatingKey">
                    <el-icon><Refresh /></el-icon>
                    生成
                  </el-button>
                </template>
              </el-input>
              <div style="font-size: 12px; color: #999; margin-top: 4px;">
                AES-256 加密密钥（64 位十六进制字符）
              </div>
              <div v-if="encryptionKeyStatus" :style="{ fontSize: '12px', marginTop: '4px', color: encryptionKeyStatus.valid ? '#67c23a' : '#f56c6c' }">
                {{ encryptionKeyStatus.message }}
              </div>
            </el-form-item>

            <el-form-item label="测试加密">
              <el-button size="small" @click="testEncryption" :disabled="!encryptionKey">
                <el-icon><CircleCheck /></el-icon>
                测试加密/解密
              </el-button>
              <div style="font-size: 12px; color: #999; margin-top: 4px;">
                验证密钥是否可以正常加密和解密
              </div>
            </el-form-item>
          </template>

          <el-form-item>
            <el-button type="primary" @click="updateSecurityConfig">保存安全配置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- 上传补丁对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传补丁" width="600px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="版本号" required>
          <el-input v-model="uploadForm.version" placeholder="如: 1.0.1" />
          <template #extra>
            <span style="font-size: 12px; color: #999;">补丁的目标版本号</span>
          </template>
        </el-form-item>
        <el-form-item label="基础版本" required>
          <el-input v-model="uploadForm.base_version" placeholder="如: 1.0.0" />
          <template #extra>
            <span style="font-size: 12px; color: #999;">当前应用的版本号</span>
          </template>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="3" placeholder="补丁更新内容说明" />
        </el-form-item>
        <el-form-item label="补丁文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".patch,.zip"
            :on-change="handleFileChange"
          >
            <el-button>选择文件</el-button>
            <template #tip>
              <div style="font-size: 12px; color: #999; margin-top: 4px;">
                支持 .patch 或 .zip 格式，最大 100MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="uploadForm.force_update" />
          <template #extra>
            <span style="font-size: 12px; color: #999;">开启后用户必须更新才能使用</span>
          </template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>

    <!-- 上传版本对话框 -->
    <el-dialog v-model="showUploadVersionDialog" title="上传新版本" width="600px">
      <el-form :model="versionForm" label-width="120px">
        <el-form-item label="版本名称" required>
          <el-input v-model="versionForm.versionName" placeholder="如: 1.5.0">
            <template #prepend>v</template>
          </el-input>
        </el-form-item>
        <el-form-item label="版本号" required>
          <el-input-number v-model="versionForm.versionCode" :min="1" placeholder="如: 5" style="width: 100%;" />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            整数版本号，必须大于之前的版本
          </div>
        </el-form-item>
        <el-form-item label="APK 文件" required>
          <el-upload
            ref="versionUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".apk"
            :on-change="handleVersionFileChange"
          >
            <el-button>选择 APK 文件</el-button>
            <template #tip>
              <div style="font-size: 12px; color: #999; margin-top: 4px;">
                支持 .apk 格式，最大 500MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="版本描述">
          <el-input v-model="versionForm.description" type="textarea" :rows="2" placeholder="简短描述" />
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input v-model="versionForm.changelog" type="textarea" :rows="4" placeholder="详细的更新内容" />
        </el-form-item>
        <el-form-item label="下载地址">
          <el-input v-model="versionForm.downloadUrl" placeholder="留空则使用服务器地址" />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            可选，填写外部下载链接（如应用商店）
          </div>
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="versionForm.isForceUpdate" />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            开启后，低于此版本的用户将被强制更新
          </div>
        </el-form-item>
        <el-form-item label="最低支持版本" v-if="versionForm.isForceUpdate">
          <el-input v-model="versionForm.minSupportedVersion" placeholder="如: 1.0.0" />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            低于此版本的用户将被强制更新
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadVersionDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUploadVersion" :loading="uploadingVersion">
          {{ uploadingVersion ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑版本对话框 -->
    <el-dialog v-model="showEditVersionDialog" title="编辑版本" width="600px">
      <el-form :model="editVersionForm" label-width="120px">
        <el-form-item label="版本">
          <el-input :value="`v${editVersionForm.version_name} (${editVersionForm.version_code})`" disabled />
        </el-form-item>
        <el-form-item label="版本描述">
          <el-input v-model="editVersionForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input v-model="editVersionForm.changelog" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="下载地址">
          <el-input v-model="editVersionForm.download_url" />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="editVersionForm.is_force_update" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="最低支持版本" v-if="editVersionForm.is_force_update">
          <el-input v-model="editVersionForm.min_supported_version" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editVersionForm.status">
            <el-radio label="active">活跃</el-radio>
            <el-radio label="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditVersionDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateVersion">保存</el-button>
      </template>
    </el-dialog>

    <!-- 灰度发布配置对话框 -->
    <el-dialog v-model="showRolloutDialogVisible" title="灰度发布配置" width="600px">
      <el-form :model="rolloutForm" label-width="120px">
        <el-form-item label="补丁版本">
          <el-input v-model="rolloutForm.version" disabled />
        </el-form-item>
        
        <el-form-item label="灰度百分比">
          <el-slider 
            v-model="rolloutForm.percentage" 
            :marks="{ 0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%' }"
            :step="5"
          />
          <div style="text-align: center; margin-top: 8px; font-size: 14px; color: #666;">
            当前灰度: <strong style="color: #d4af7a; font-size: 18px;">{{ rolloutForm.percentage }}%</strong>
          </div>
        </el-form-item>

        <el-alert
          :title="getRolloutTip()"
          type="info"
          :closable="false"
          style="margin-bottom: 20px;"
        >
          <p style="margin: 0; font-size: 13px;">
            {{ getRolloutDescription() }}
          </p>
        </el-alert>

        <el-form-item label="补丁状态">
          <el-radio-group v-model="rolloutForm.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="强制更新">
          <el-switch v-model="rolloutForm.forceUpdate" />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            开启后用户必须更新才能使用应用
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showRolloutDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateRollout" :loading="rolloutUpdating">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, Box, Upload, Warning, Tools, Refresh, CircleCheck, DocumentCopy } from '@element-plus/icons-vue';
import api from '../api';

const route = useRoute();
const router = useRouter();
const app = ref(null);
const activeTab = ref('patches');
const showUploadDialog = ref(false);
const uploading = ref(false);
const uploadRef = ref(null);

// 版本管理相关
const versions = ref([]);
const versionsLoading = ref(false);
const showUploadVersionDialog = ref(false);
const showEditVersionDialog = ref(false);
const uploadingVersion = ref(false);
const versionUploadRef = ref(null);

const versionForm = reactive({
  versionName: '',
  versionCode: null,
  description: '',
  changelog: '',
  downloadUrl: '',
  isForceUpdate: false,
  minSupportedVersion: '',
  file: null
});

const editVersionForm = reactive({
  id: null,
  version_name: '',
  version_code: null,
  description: '',
  changelog: '',
  download_url: '',
  is_force_update: 0,
  min_supported_version: '',
  status: 'active'
});

const uploadForm = reactive({
  version: '',
  base_version: '',
  description: '',
  force_update: false,
  file: null
});

const generateForm = reactive({
  version: '',
  base_version: '',
  description: '',
  force_update: false,
  baseApk: null,
  newApk: null
});

const generating = ref(false);
const generateProgress = ref(0);
const baseApkRef = ref(null);
const newApkRef = ref(null);
const patchCliAvailable = ref(false);
const patchCliChecked = ref(false);
const keystoreUploadRef = ref(null);
const keystoreFileList = ref([]);
const keystoreFile = ref(null);
const showRolloutDialogVisible = ref(false);
const rolloutUpdating = ref(false);
const rolloutForm = reactive({
  patchId: null,
  version: '',
  percentage: 100,
  status: 'active',
  forceUpdate: false
});

// 加密相关
const encryptionKey = ref('');
const encryptionKeyStatus = ref(null);
const generatingKey = ref(false);

// API 文档相关
const apiBaseUrl = computed(() => {
  return window.location.origin;
});

const checkUpdateParams = [
  { name: 'app_id', type: 'string', required: true, desc: '应用的唯一标识' },
  { name: 'version', type: 'string', required: true, desc: '当前应用版本号' },
  { name: 'device_id', type: 'string', required: true, desc: '设备唯一标识（用于灰度发布）' }
];

const reportParams = [
  { name: 'app_id', type: 'string', required: true, desc: '应用的唯一标识' },
  { name: 'patch_id', type: 'string', required: true, desc: '补丁 ID' },
  { name: 'device_id', type: 'string', required: true, desc: '设备唯一标识' },
  { name: 'success', type: 'boolean', required: true, desc: '应用是否成功' },
  { name: 'error_message', type: 'string', required: false, desc: '失败时的错误信息' }
];

const checkUpdateExample = computed(() => {
  return `GET ${apiBaseUrl.value}/api/client/check-update?app_id=${app.value?.app_id || 'YOUR_APP_ID'}&version=1.0.0&device_id=device123

// 使用 OkHttp
val url = "${apiBaseUrl.value}/api/client/check-update" +
    "?app_id=${app.value?.app_id || 'YOUR_APP_ID'}" +
    "&version=1.0.0" +
    "&device_id=\${getDeviceId()}"

val request = Request.Builder()
    .url(url)
    .get()
    .build()

client.newCall(request).execute()`;
});

const checkUpdateResponse = `{
  "hasUpdate": true,
  "patch": {
    "id": 1,
    "version": "1.0.1",
    "patch_id": "patch_123456",
    "base_version": "1.0.0",
    "file_size": 1048576,
    "md5": "abc123def456...",
    "download_url": "${apiBaseUrl.value}/downloads/patch-123456.zip",
    "force_update": false,
    "description": "修复了一些问题"
  }
}`;

const downloadExample = computed(() => {
  return `// 下载补丁文件
val downloadUrl = patchInfo.download_url
val file = File(context.cacheDir, "patch.zip")

val request = Request.Builder()
    .url(downloadUrl)
    .get()
    .build()

client.newCall(request).execute().use { response ->
    if (response.isSuccessful) {
        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}`;
});

const reportExample = computed(() => {
  return `POST ${apiBaseUrl.value}/api/client/report
Content-Type: application/json

{
  "app_id": "${app.value?.app_id || 'YOUR_APP_ID'}",
  "patch_id": "patch_123456",
  "device_id": "device123",
  "success": true,
  "error_message": null
}

// 使用 OkHttp
val json = JSONObject().apply {
    put("app_id", "${app.value?.app_id || 'YOUR_APP_ID'}")
    put("patch_id", patchId)
    put("device_id", getDeviceId())
    put("success", true)
}

val body = json.toString()
    .toRequestBody("application/json".toMediaType())

val request = Request.Builder()
    .url("${apiBaseUrl.value}/api/client/report")
    .post(body)
    .build()

client.newCall(request).execute()`;
});

const kotlinExample = computed(() => {
  return `class PatchManager(private val context: Context) {
    private val client = OkHttpClient()
    private val appId = "${app.value?.app_id || 'YOUR_APP_ID'}"
    private val baseUrl = "${apiBaseUrl.value}"
    
    // 检查更新
    suspend fun checkUpdate(currentVersion: String): PatchInfo? {
        val deviceId = getDeviceId()
        val url = "\$baseUrl/api/client/check-update" +
            "?app_id=\$appId" +
            "&version=\$currentVersion" +
            "&device_id=\$deviceId"
        
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("hasUpdate")) {
                        parsePatchInfo(json.getJSONObject("patch"))
                    } else null
                } else null
            }
        }
    }
    
    // 下载补丁
    suspend fun downloadPatch(downloadUrl: String): File? {
        val file = File(context.cacheDir, "patch_\${System.currentTimeMillis()}.zip")
        
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()
        
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        file
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    // 上报结果
    suspend fun reportResult(patchId: String, success: Boolean, errorMsg: String? = null) {
        val json = JSONObject().apply {
            put("app_id", appId)
            put("patch_id", patchId)
            put("device_id", getDeviceId())
            put("success", success)
            errorMsg?.let { put("error_message", it) }
        }
        
        val body = json.toString()
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("\$baseUrl/api/client/report")
            .post(body)
            .build()
        
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }
    
    private fun parsePatchInfo(json: JSONObject): PatchInfo {
        return PatchInfo(
            id = json.getInt("id"),
            version = json.getString("version"),
            patchId = json.getString("patch_id"),
            baseVersion = json.getString("base_version"),
            fileSize = json.getLong("file_size"),
            md5 = json.getString("md5"),
            downloadUrl = json.getString("download_url"),
            forceUpdate = json.getBoolean("force_update"),
            description = json.optString("description")
        )
    }
}

data class PatchInfo(
    val id: Int,
    val version: String,
    val patchId: String,
    val baseVersion: String,
    val fileSize: Long,
    val md5: String,
    val downloadUrl: String,
    val forceUpdate: Boolean,
    val description: String
)`;
});

const javaExample = computed(() => {
  return `public class PatchManager {
    private final Context context;
    private final OkHttpClient client;
    private final String appId = "${app.value?.app_id || 'YOUR_APP_ID'}";
    private final String baseUrl = "${apiBaseUrl.value}";
    
    public PatchManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
    }
    
    // 检查更新
    public PatchInfo checkUpdate(String currentVersion) throws IOException {
        String deviceId = getDeviceId();
        String url = baseUrl + "/api/client/check-update" +
            "?app_id=" + appId +
            "&version=" + currentVersion +
            "&device_id=" + deviceId;
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.getBoolean("hasUpdate")) {
                    return parsePatchInfo(json.getJSONObject("patch"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // 下载补丁
    public File downloadPatch(String downloadUrl) throws IOException {
        File file = new File(context.getCacheDir(), 
            "patch_" + System.currentTimeMillis() + ".zip");
        
        Request request = new Request.Builder()
            .url(downloadUrl)
            .get()
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                try (InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
                return file;
            }
        }
        return null;
    }
    
    // 上报结果
    public void reportResult(String patchId, boolean success, String errorMsg) 
            throws IOException, JSONException {
        JSONObject json = new JSONObject();
        json.put("app_id", appId);
        json.put("patch_id", patchId);
        json.put("device_id", getDeviceId());
        json.put("success", success);
        if (errorMsg != null) {
            json.put("error_message", errorMsg);
        }
        
        RequestBody body = RequestBody.create(
            json.toString(),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/client/report")
            .post(body)
            .build();
        
        client.newCall(request).execute();
    }
    
    private String getDeviceId() {
        return Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
    }
    
    private PatchInfo parsePatchInfo(JSONObject json) throws JSONException {
        return new PatchInfo(
            json.getInt("id"),
            json.getString("version"),
            json.getString("patch_id"),
            json.getString("base_version"),
            json.getLong("file_size"),
            json.getString("md5"),
            json.getString("download_url"),
            json.getBoolean("force_update"),
            json.optString("description")
        );
    }
}`;
});

const loadApp = async () => {
  try {
    const { data } = await api.get(`/apps/${route.params.id}`);
    app.value = data;
    
    // 如果在版本管理标签页，加载版本列表
    if (activeTab.value === 'versions') {
      loadVersions();
    }
  } catch (error) {
    ElMessage.error('加载应用详情失败');
    router.back();
  }
};

// 加载版本列表
const loadVersions = async () => {
  versionsLoading.value = true;
  try {
    const { data } = await api.get(`/versions/${app.value.app_id}`);
    versions.value = data.versions;
  } catch (error) {
    ElMessage.error('加载版本列表失败');
  } finally {
    versionsLoading.value = false;
  }
};

// 处理版本文件选择
const handleVersionFileChange = (file) => {
  versionForm.file = file.raw;
};

// 上传新版本
const handleUploadVersion = async () => {
  if (!versionForm.versionName || !versionForm.versionCode || !versionForm.file) {
    ElMessage.warning('请填写完整信息并选择 APK 文件');
    return;
  }

  uploadingVersion.value = true;
  try {
    const formData = new FormData();
    formData.append('file', versionForm.file);
    formData.append('versionName', versionForm.versionName);
    formData.append('versionCode', versionForm.versionCode);
    formData.append('description', versionForm.description);
    formData.append('changelog', versionForm.changelog);
    formData.append('downloadUrl', versionForm.downloadUrl);
    formData.append('isForceUpdate', versionForm.isForceUpdate);
    formData.append('minSupportedVersion', versionForm.minSupportedVersion);

    await api.post(`/versions/${app.value.app_id}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });

    ElMessage.success('版本上传成功');
    showUploadVersionDialog.value = false;
    
    // 重置表单
    Object.assign(versionForm, {
      versionName: '',
      versionCode: null,
      description: '',
      changelog: '',
      downloadUrl: '',
      isForceUpdate: false,
      minSupportedVersion: '',
      file: null
    });
    
    if (versionUploadRef.value) {
      versionUploadRef.value.clearFiles();
    }
    
    loadVersions();
    loadApp(); // 重新加载应用信息（更新强制更新配置）
  } catch (error) {
    ElMessage.error(error.response?.data?.error || '上传版本失败');
  } finally {
    uploadingVersion.value = false;
  }
};

// 编辑版本
const editVersion = (version) => {
  Object.assign(editVersionForm, {
    id: version.id,
    version_name: version.version_name,
    version_code: version.version_code,
    description: version.description || '',
    changelog: version.changelog || '',
    download_url: version.download_url || '',
    is_force_update: version.is_force_update,
    min_supported_version: version.min_supported_version || '',
    status: version.status
  });
  showEditVersionDialog.value = true;
};

// 更新版本信息
const handleUpdateVersion = async () => {
  try {
    await api.put(`/versions/${editVersionForm.id}`, {
      description: editVersionForm.description,
      changelog: editVersionForm.changelog,
      downloadUrl: editVersionForm.download_url,
      isForceUpdate: editVersionForm.is_force_update === 1,
      minSupportedVersion: editVersionForm.min_supported_version,
      status: editVersionForm.status
    });

    ElMessage.success('版本更新成功');
    showEditVersionDialog.value = false;
    loadVersions();
    loadApp();
  } catch (error) {
    ElMessage.error(error.response?.data?.error || '更新版本失败');
  }
};

// 下载版本
const downloadVersion = (version) => {
  const url = version.download_url || `${window.location.origin}/api/versions/download/${version.id}`;
  window.open(url, '_blank');
};

// 复制版本下载链接
const copyVersionUrl = (version) => {
  const url = version.download_url || `${window.location.origin}/api/versions/download/${version.id}`;
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('下载链接已复制');
  });
};

// 删除版本
const deleteVersion = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除此版本吗？删除后无法恢复。', '确认删除', {
      type: 'warning'
    });

    await api.delete(`/versions/${id}`);
    ElMessage.success('版本删除成功');
    loadVersions();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.error || '删除版本失败');
    }
  }
};

// 监听标签页切换
watch(activeTab, (newTab) => {
  if (newTab === 'versions') {
    loadVersions();
  }
});

const checkPatchCli = async () => {
  try {
    const { data } = await api.get('/generate/check');
    patchCliAvailable.value = data.available;
    patchCliChecked.value = true;
    
    if (!data.available) {
      console.warn('patch-cli 不可用:', data.error);
    }
  } catch (error) {
    console.error('检查 patch-cli 失败:', error);
    patchCliAvailable.value = false;
    patchCliChecked.value = true;
  }
};

const handleFileChange = (file) => {
  uploadForm.file = file.raw;
};

const handleUpload = async () => {
  // 验证表单
  if (!uploadForm.version) {
    ElMessage.warning('请输入版本号');
    return;
  }
  
  if (!uploadForm.base_version) {
    ElMessage.warning('请输入基础版本号');
    return;
  }
  
  if (!uploadForm.file) {
    ElMessage.warning('请选择补丁文件');
    return;
  }

  try {
    uploading.value = true;
    const formData = new FormData();
    formData.append('file', uploadForm.file);
    formData.append('app_id', route.params.id);
    formData.append('version', uploadForm.version);
    formData.append('base_version', uploadForm.base_version);
    formData.append('description', uploadForm.description);
    formData.append('force_update', uploadForm.force_update);
    // 🔒 添加包名和 app_id 用于强制验证
    formData.append('package_name', app.value.package_name);
    formData.append('app_id_string', app.value.app_id);

    console.log('📤 上传补丁，验证信息:');
    console.log('  - 应用名称:', app.value.app_name);
    console.log('  - 包名:', app.value.package_name);
    console.log('  - app_id:', app.value.app_id);

    await api.post('/patches/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });

    ElMessage.success('补丁上传成功');
    showUploadDialog.value = false;
    
    // 重置表单
    Object.assign(uploadForm, {
      version: '',
      base_version: '',
      description: '',
      force_update: false,
      file: null
    });
    
    loadApp();
  } catch (error) {
    ElMessage.error(error.response?.data?.error || '上传失败');
  } finally {
    uploading.value = false;
  }
};

const updateApp = async () => {
  try {
    await api.put(`/apps/${route.params.id}`, {
      app_name: app.value.app_name,
      package_name: app.value.package_name,
      description: app.value.description,
      icon: app.value.icon,
      status: app.value.status
    });
    ElMessage.success('更新成功');
  } catch (error) {
    ElMessage.error('更新失败');
  }
};

const updateSecurityConfig = async () => {
  try {
    // 验证签名配置完整性
    if (app.value.require_signature) {
      if (!app.value.keystore_password || !app.value.key_alias || !app.value.key_password) {
        ElMessage.warning('请完整配置 JKS 签名信息');
        return;
      }
    }

    // 验证加密配置
    if (app.value.require_encryption && encryptionKey.value) {
      try {
        const { data } = await api.validateEncryptionKey(encryptionKey.value);
        if (!data.valid) {
          ElMessage.error('加密密钥格式无效');
          return;
        }
      } catch (error) {
        ElMessage.error('验证加密密钥失败');
        return;
      }
    }

    // 如果有新的 keystore 文件，先上传
    if (keystoreFile.value) {
      const formData = new FormData();
      formData.append('keystore', keystoreFile.value);
      formData.append('app_id', route.params.id);
      
      try {
        const { data } = await api.post('/apps/upload-keystore', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        app.value.keystore_path = data.keystore_path;
        ElMessage.success('Keystore 文件上传成功');
      } catch (error) {
        ElMessage.error('Keystore 文件上传失败');
        return;
      }
    }

    // 更新应用配置
    await api.put(`/apps/${route.params.id}`, {
      app_name: app.value.app_name,
      package_name: app.value.package_name,
      description: app.value.description,
      icon: app.value.icon,
      status: app.value.status,
      require_signature: app.value.require_signature,
      require_encryption: app.value.require_encryption,
      keystore_path: app.value.keystore_path,
      keystore_password: app.value.keystore_password,
      key_alias: app.value.key_alias,
      key_password: app.value.key_password
    });
    
    // 更新加密配置
    if (app.value.require_encryption) {
      await api.updateEncryptionConfig(route.params.id, {
        enabled: true,
        key: encryptionKey.value
      });
    } else {
      await api.updateEncryptionConfig(route.params.id, {
        enabled: false,
        key: null
      });
    }
    
    ElMessage.success('安全配置已保存');
    keystoreFile.value = null;
    keystoreFileList.value = [];
    loadApp();
  } catch (error) {
    ElMessage.error('保存失败');
  }
};

// 生成加密密钥
const generateKey = async () => {
  try {
    generatingKey.value = true;
    const { data } = await api.generateEncryptionKey();
    encryptionKey.value = data.key;
    encryptionKeyStatus.value = { valid: true, message: '✓ 密钥已生成' };
    ElMessage.success('密钥生成成功');
  } catch (error) {
    ElMessage.error('生成密钥失败');
  } finally {
    generatingKey.value = false;
  }
};

// 测试加密
const testEncryption = async () => {
  if (!encryptionKey.value) {
    ElMessage.warning('请先输入或生成密钥');
    return;
  }

  try {
    const testText = 'Hello, Patch Server!';
    const { data } = await api.testEncryption(encryptionKey.value, testText);
    
    if (data.success && data.match) {
      ElMessage.success('✓ 加密测试成功！密钥可以正常使用');
      encryptionKeyStatus.value = { valid: true, message: '✓ 密钥验证通过' };
    } else {
      ElMessage.error('加密测试失败');
      encryptionKeyStatus.value = { valid: false, message: '✗ 密钥验证失败' };
    }
  } catch (error) {
    ElMessage.error('测试失败: ' + (error.response?.data?.error || error.message));
    encryptionKeyStatus.value = { valid: false, message: '✗ 密钥验证失败' };
  }
};

// 加载加密配置
const loadEncryptionConfig = async () => {
  try {
    const { data } = await api.getEncryptionConfig(route.params.id);
    if (data.hasKey) {
      encryptionKeyStatus.value = { valid: true, message: '✓ 已配置加密密钥' };
    }
  } catch (error) {
    console.error('加载加密配置失败:', error);
  }
};

const handleKeystoreChange = (file) => {
  keystoreFile.value = file.raw;
};

const showRolloutDialog = (patch) => {
  rolloutForm.patchId = patch.id;
  rolloutForm.version = patch.version;
  rolloutForm.percentage = patch.rollout_percentage || 100;
  rolloutForm.status = patch.status;
  rolloutForm.forceUpdate = patch.force_update === 1;
  showRolloutDialogVisible.value = true;
};

const updateRollout = async () => {
  try {
    rolloutUpdating.value = true;
    
    await api.put(`/patches/${rolloutForm.patchId}`, {
      rolloutPercentage: rolloutForm.percentage,
      status: rolloutForm.status,
      forceUpdate: rolloutForm.forceUpdate
    });
    
    ElMessage.success('灰度配置已更新');
    showRolloutDialogVisible.value = false;
    loadApp();
  } catch (error) {
    ElMessage.error('更新失败');
  } finally {
    rolloutUpdating.value = false;
  }
};

const getRolloutTip = () => {
  const p = rolloutForm.percentage;
  if (p === 0) return '补丁未发布';
  if (p < 10) return '小范围灰度测试';
  if (p < 50) return '中等规模灰度';
  if (p < 100) return '大规模灰度';
  return '全量发布';
};

const getRolloutDescription = () => {
  const p = rolloutForm.percentage;
  if (p === 0) return '补丁不会推送给任何用户';
  if (p === 100) return '补丁会推送给所有符合条件的用户';
  return `补丁会推送给约 ${p}% 的用户（基于设备 ID 哈希）`;
};

const calculateSuccessRate = (patch) => {
  const total = (patch.success_count || 0) + (patch.fail_count || 0);
  if (total === 0) return 'N/A';
  const rate = ((patch.success_count || 0) / total * 100).toFixed(1);
  return `${rate}%`;
};

const deleteApp = async () => {
  try {
    await ElMessageBox.confirm('确定要删除此应用吗？此操作不可恢复', '警告', {
      type: 'warning'
    });
    await api.delete(`/apps/${route.params.id}`);
    ElMessage.success('删除成功');
    router.push('/apps');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const deletePatch = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除此补丁吗？', '警告', {
      type: 'warning'
    });
    await api.delete(`/patches/${id}`);
    ElMessage.success('删除成功');
    loadApp();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const downloadPatch = (patch) => {
  window.open(`${api.defaults.baseURL.replace('/api', '')}/downloads/${patch.file_name}`, '_blank');
};

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
};

const formatDate = (date) => {
  return new Date(date).toLocaleString('zh-CN');
};

// 复制功能
const copyAppId = () => {
  copyText(app.value.app_id);
};

const copyText = (text) => {
  // 优先使用 Clipboard API
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(() => {
      ElMessage.success('已复制到剪贴板');
    }).catch(() => {
      // 降级到传统方法
      fallbackCopyText(text);
    });
  } else {
    // 降级到传统方法
    fallbackCopyText(text);
  }
};

// 降级复制方法
const fallbackCopyText = (text) => {
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    const successful = document.execCommand('copy');
    document.body.removeChild(textarea);
    
    if (successful) {
      ElMessage.success('已复制到剪贴板');
    } else {
      ElMessage.error('复制失败，请手动复制');
    }
  } catch (err) {
    ElMessage.error('复制失败，请手动复制');
  }
};

const copyApiExample = (type) => {
  let text = '';
  if (type === 'checkUpdate') {
    text = checkUpdateExample.value;
  } else if (type === 'report') {
    text = reportExample.value;
  }
  copyText(text);
};

const handleBaseApkChange = (file) => {
  generateForm.baseApk = file.raw;
};

const handleNewApkChange = (file) => {
  generateForm.newApk = file.raw;
};

const handleGenerate = async () => {
  // 验证表单
  if (!generateForm.version) {
    ElMessage.warning('请输入版本号');
    return;
  }
  
  if (!generateForm.base_version) {
    ElMessage.warning('请输入基础版本号');
    return;
  }
  
  if (!generateForm.baseApk) {
    ElMessage.warning('请选择基准 APK');
    return;
  }
  
  if (!generateForm.newApk) {
    ElMessage.warning('请选择新版本 APK');
    return;
  }

  try {
    generating.value = true;
    generateProgress.value = 0;

    const formData = new FormData();
    formData.append('baseApk', generateForm.baseApk);
    formData.append('newApk', generateForm.newApk);
    formData.append('app_id', route.params.id);
    formData.append('version', generateForm.version);
    formData.append('base_version', generateForm.base_version);
    formData.append('description', generateForm.description);
    formData.append('force_update', generateForm.force_update);
    // 🔒 添加包名和 app_id 用于强制验证
    formData.append('package_name', app.value.package_name);
    formData.append('app_id_string', app.value.app_id);

    console.log('🔨 生成补丁，验证信息:');
    console.log('  - 应用名称:', app.value.app_name);
    console.log('  - 包名:', app.value.package_name);
    console.log('  - app_id:', app.value.app_id);

    // 模拟进度
    const progressInterval = setInterval(() => {
      if (generateProgress.value < 90) {
        generateProgress.value += 10;
      }
    }, 500);

    const { data } = await api.post('/generate/patch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });

    clearInterval(progressInterval);
    generateProgress.value = 100;

    ElMessage.success('补丁生成成功！');
    
    // 重置表单
    Object.assign(generateForm, {
      version: '',
      base_version: '',
      description: '',
      force_update: false,
      baseApk: null,
      newApk: null
    });
    
    // 清空文件选择
    if (baseApkRef.value) baseApkRef.value.clearFiles();
    if (newApkRef.value) newApkRef.value.clearFiles();
    
    // 延迟重置进度条
    setTimeout(() => {
      generateProgress.value = 0;
    }, 2000);
    
    // 切换到补丁列表
    activeTab.value = 'patches';
    loadApp();
  } catch (error) {
    const errorMsg = error.response?.data?.error || '生成补丁失败';
    const errorDetails = error.response?.data?.details;
    
    if (errorDetails) {
      ElMessageBox.alert(errorDetails, errorMsg, {
        confirmButtonText: '确定',
        type: 'warning'
      });
    } else {
      ElMessage.error(errorMsg);
    }
    
    generateProgress.value = 0;
  } finally {
    generating.value = false;
  }
};

onMounted(() => {
  loadApp();
  checkPatchCli();
  loadEncryptionConfig();
});
</script>

<style scoped>
.app-detail-container {
  padding: 24px;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #e5e7eb;
}

.app-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
}

.app-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: #d4af7a;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.app-icon img {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  object-fit: cover;
}

.app-title h2 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.app-title p {
  font-size: 14px;
  color: #666;
  margin: 0;
  font-family: 'Courier New', monospace;
}

.app-tabs {
  background: white;
  border-radius: 12px;
  padding: 24px;
}

.patches-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.patch-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  transition: all 0.3s;
}

.patch-item:hover {
  border-color: #d4af7a;
  box-shadow: 0 2px 8px rgba(212, 175, 122, 0.1);
}

.patch-info {
  flex: 1;
  display: flex;
  gap: 16px;
}

.patch-version {
  display: flex;
  align-items: center;
  gap: 8px;
}

.patch-details {
  flex: 1;
}

.patch-desc {
  font-size: 14px;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.patch-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #888;
}

.patch-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.settings-form {
  max-width: 600px;
}

.generate-section {
  max-width: 800px;
}

.generate-section code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #e74c3c;
}

.generate-section ul,
.generate-section ol {
  line-height: 1.8;
}

.generate-section li {
  margin: 4px 0;
}

/* API 文档样式 */
.api-docs {
  max-width: 1000px;
}

.api-section {
  margin-bottom: 32px;
}

.api-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #d4af7a;
}

.api-desc {
  font-size: 14px;
  color: #666;
  margin: 0 0 16px 0;
}

.api-block {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.api-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.api-url {
  flex: 1;
  background: white;
  padding: 8px 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: #d4af7a;
  font-weight: 600;
}

.code-block {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 6px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 0;
}

.copy-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.copy-field code {
  flex: 1;
  background: #f5f5f5;
  padding: 6px 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #d4af7a;
  font-weight: 600;
}

.code-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

/* 版本管理样式 */
.versions-section {
  max-width: 1200px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #e5e7eb;
}

.section-header h3 {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.versions-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.version-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  transition: all 0.3s;
}

.version-item:hover {
  border-color: #d4af7a;
  box-shadow: 0 2px 8px rgba(212, 175, 122, 0.1);
}

.version-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.version-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-details {
  flex: 1;
}

.version-desc {
  font-size: 14px;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.version-changelog {
  background: #f8f9fa;
  padding: 12px;
  border-radius: 6px;
  margin: 8px 0;
}

.version-changelog strong {
  display: block;
  margin-bottom: 8px;
  color: #1a1a1a;
}

.version-changelog pre {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: #666;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.version-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #888;
  flex-wrap: wrap;
}

.version-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  flex-wrap: wrap;
}

</style>
