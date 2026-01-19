<template>
  <div class="upload">
    <el-card>
      <template #header>
        <span>上传补丁</span>
      </template>
      
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="补丁文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            accept=".zip"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                只支持 .zip 文件，大小不超过 100MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        
        <el-form-item label="版本号" prop="version">
          <el-input v-model="form.version" placeholder="例如: 1.4.1" />
        </el-form-item>
        
        <el-form-item label="基础版本" prop="baseVersion">
          <el-input v-model="form.baseVersion" placeholder="例如: 1.4.0" />
        </el-form-item>
        
        <el-form-item label="更新说明" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入更新说明"
          />
        </el-form-item>
        
        <el-form-item label="强制更新">
          <el-switch v-model="form.forceUpdate" />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="uploading">
            {{ uploading ? `上传中 ${uploadProgress}%` : '上传' }}
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      
      <el-progress
        v-if="uploading"
        :percentage="uploadProgress"
        :status="uploadProgress === 100 ? 'success' : undefined"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { api } from '@/api'

const router = useRouter()
const formRef = ref()
const uploadRef = ref()
const uploading = ref(false)
const uploadProgress = ref(0)

const form = reactive({
  file: null,
  version: '',
  baseVersion: '',
  description: '',
  forceUpdate: false
})

const rules = {
  file: [{ required: true, message: '请选择补丁文件', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  baseVersion: [{ required: true, message: '请输入基础版本', trigger: 'blur' }],
  description: [{ required: true, message: '请输入更新说明', trigger: 'blur' }]
}

const handleFileChange = (file) => {
  form.file = file.raw
}

const handleFileRemove = () => {
  form.file = null
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    
    const formData = new FormData()
    formData.append('file', form.file)
    formData.append('version', form.version)
    formData.append('baseVersion', form.baseVersion)
    formData.append('description', form.description)
    formData.append('forceUpdate', form.forceUpdate)
    
    uploading.value = true
    uploadProgress.value = 0
    
    await api.uploadPatch(formData, (progressEvent) => {
      uploadProgress.value = Math.round(
        (progressEvent.loaded * 100) / progressEvent.total
      )
    })
    
    ElMessage.success('上传成功')
    router.push('/patches')
  } catch (error) {
    console.error('上传失败:', error)
  } finally {
    uploading.value = false
  }
}

const handleReset = () => {
  formRef.value.resetFields()
  uploadRef.value.clearFiles()
  form.file = null
}
</script>

<style scoped>
.el-upload {
  width: 100%;
}
</style>
