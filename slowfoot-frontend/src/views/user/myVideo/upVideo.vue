<template>
  <el-card class="upload-page">
    <div class="upload-intro">
      <el-card class="upload-dropzone" shadow="never" @drop.prevent="handleDrop">
        <div class="upload-icon"><i class="mdi mdi-upload" /></div>
        <div>拖到此处可上传</div>
        <el-button type="primary" @click="selectVideo">上传视频</el-button>
        <el-tag type="success" effect="plain">
          <i class="mdi mdi-file-cloud-outline" />
          审核队列: {{ queueState }}
        </el-tag>
        <input ref="videoFileRef" hidden type="file" accept="video/*" @change="uploadVideo" />
      </el-card>

      <el-card class="notice-card" shadow="never">
        <div class="notice-title"><i class="mdi mdi-menu-down" /> 用户须知</div>
        <div v-for="(item, index) in items" :key="index" class="notice-item">
          <el-image :src="item.image" fit="cover" class="notice-image" />
          <div>
            <div class="notice-category">{{ item.category }}</div>
            <div>{{ item.title }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card v-if="uploadList.length" class="upload-workspace" shadow="never">
      <div class="workspace-title">基本信息</div>
      <el-divider />
      <div class="upload-items">
        <el-card
          v-for="(uploadItem, index) in uploadList"
          :key="index"
          :class="['upload-item', { active: currentVideoIndex === index }]"
          shadow="hover"
          @click="currentVideoIndex = index"
        >
          <div class="upload-item-title">{{ uploadItem.title }}</div>
          <el-progress :percentage="Math.ceil(uploadItem.progress)" :status="uploadItem.status < 0 ? 'exception' : undefined" />
          <div v-if="uploadItem.msg" class="upload-error">{{ uploadItem.msg }}</div>
        </el-card>
      </div>
      <VideoEdit v-if="currentVideo" :current-video="currentVideo" :clear="clearUp" :save="pushVideo" />
    </el-card>

    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'info'" closable class="upload-alert" @close="snackbar.show = false" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { apiUploadFile } from '../../../apis/file'
import { apiGetAuditQueueState } from '../../../apis/video'
import VideoEdit from './edit.vue'

const snackbar = ref({ show: false, text: '', color: 'info' })
const items = [
  { image: '/logo.png', title: '视频格式支持: mp4、avi、av、cawd', category: '视频' },
  { image: '/logo.png', title: '发布视频后会进行视频审核，七牛云服务自动审核', category: '审核' },
  { image: '/logo.png', title: '您的视频将会被储存在七牛云的对象存储服务中', category: '存储' }
]
const currentVideoIndex = ref(null)
const videoFileRef = ref(null)
const uploadList = ref([])
const queueState = ref('快')
const currentVideo = computed(() => currentVideoIndex.value !== null && currentVideoIndex.value > -1
  ? uploadList.value[currentVideoIndex.value]
  : null)

const getQueueState = () => {
  apiGetAuditQueueState().then(({ data }) => {
    if (data.success) queueState.value = data.message
  })
}

const selectVideo = () => videoFileRef.value?.click()
const handleDrop = (event) => {
  const file = [...(event.dataTransfer?.files || [])].find((item) => item.type.startsWith('video/'))
  if (file) uploadFile(file)
}

const clearUp = () => {
  if (!currentVideo.value) return
  if (currentVideo.value.status < 1 && currentVideo.value.subscription) currentVideo.value.subscription.unsubscribe()
  const index = currentVideoIndex.value
  uploadList.value.splice(index, 1)
  currentVideoIndex.value = uploadList.value.length ? Math.min(index, uploadList.value.length - 1) : null
}

const pushVideo = (data) => {
  snackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
  if (data.success) {
    clearUp()
    getQueueState()
  }
}

const uploadVideo = () => {
  const file = videoFileRef.value?.files?.[0]
  if (file) uploadFile(file)
  if (videoFileRef.value) videoFileRef.value.value = ''
}

const uploadFile = async (file) => {
  const uploadInfo = {
    progress: 0,
    status: 0,
    msg: '',
    result: '',
    title: file.name,
    description: '',
    primaryFileId: '',
    coverFileId: '',
    file,
    labelNames: []
  }
  uploadList.value.push(uploadInfo)
  if (uploadList.value.length === 1) currentVideoIndex.value = 0
  uploadInfo.subscription = await apiUploadFile(file, {
    next: (event) => {
      uploadInfo.progress = event.total.percent
      uploadList.value = [...uploadList.value]
    },
    error: (error) => {
      uploadInfo.status = -1
      uploadInfo.msg = `上传失败: ${error}`
      uploadList.value = [...uploadList.value]
    },
    complete: (event, result) => {
      if (!result.success) {
        uploadInfo.status = -1
        uploadInfo.msg = result.message
        uploadList.value = [...uploadList.value]
        snackbar.value = { text: result.message, show: true, color: 'error' }
        return
      }
      uploadInfo.result = event
      uploadInfo.status = 1
      uploadInfo.progress = 100
      uploadInfo.primaryFileId = result.data
      uploadInfo.coverFileId = null
      uploadList.value = [...uploadList.value]
    }
  })
}

onMounted(getQueueState)
</script>

<style scoped>
.upload-page { min-height: 100%; }
.upload-intro { display: grid; grid-template-columns: minmax(0, 1fr) minmax(260px, 1fr); gap: 16px; }
.upload-dropzone { display: grid; place-items: center; align-content: center; gap: 14px; min-height: 300px; border: 1px dashed rgba(255, 255, 255, 0.32); text-align: center; }
.upload-icon { color: #409eff; font-size: 50px; line-height: 1; }
.upload-dropzone .el-tag { margin-top: 4px; }
.notice-card { min-height: 300px; }
.notice-title { margin-bottom: 10px; font-weight: 600; }
.notice-item { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.notice-item:last-child { border-bottom: 0; }
.notice-image { width: 50px; height: 50px; flex: 0 0 50px; border-radius: 4px; }
.notice-category { color: #aeb4c0; font-size: 12px; text-transform: uppercase; }
.upload-workspace { margin-top: 16px; }
.workspace-title { font-size: 16px; font-weight: 600; }
.upload-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; margin-bottom: 20px; }
.upload-item { cursor: pointer; border-color: transparent; }
.upload-item.active { border-color: #409eff; }
.upload-item-title { overflow: hidden; margin-bottom: 8px; text-overflow: ellipsis; white-space: nowrap; }
.upload-error { color: #f56c6c; font-size: 12px; }
.upload-alert { position: fixed; right: 24px; bottom: 24px; z-index: 20; width: min(360px, calc(100vw - 48px)); }
@media (max-width: 760px) { .upload-intro { grid-template-columns: 1fr; } }
</style>
