<template>
  <el-card shadow="never">
    <div class="card-heading">发布作品</div>
    <el-tabs v-model="mode">
      <el-tab-pane label="视频" name="video">
        <div class="video-upload-page">
          <!-- 断点续传恢复提示 -->
          <el-alert v-if="resumableUploads.length" type="warning" closable class="resume-alert">
            <template #title>
              发现 {{ resumableUploads.length }} 个未完成的上传
              <el-button size="small" type="primary" link @click="triggerResumeSelect">点击选择文件继续上传</el-button>
              <el-button size="small" type="danger" link @click="dismissResumable">忽略</el-button>
            </template>
            <input ref="resumeFileRef" hidden type="file" accept="video/*" @change="handleResumeSelect" />
          </el-alert>

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
              <div v-for="(item, index) in videoNoticeItems" :key="index" class="notice-item">
                <div class="notice-image"><i :class="['mdi', item.icon]" /></div>
                <div>
                  <div class="notice-category">{{ item.category }}</div>
                  <div>{{ item.title }}</div>
                </div>
              </div>
            </el-card>
          </div>

          <el-card v-if="videoUploads.length" class="upload-workspace" shadow="never">
            <div class="workspace-title">基本信息</div>
            <el-divider />
            <div class="upload-items">
              <el-card
                v-for="(uploadItem, index) in videoUploads"
                :key="index"
                :class="['upload-item', { active: currentVideoIndex === index }]"
                shadow="hover"
                @click="uploadStore.selectUpload(index)"
              >
                <div class="upload-item-title">{{ uploadItem.title }}</div>
                <el-progress :percentage="Math.ceil(uploadItem.progress)" :status="uploadItem.status < 0 ? 'exception' : uploadItem.status === 2 ? 'warning' : undefined" />
                <div v-if="uploadItem.msg" class="upload-error">{{ uploadItem.msg }}</div>
                <div v-if="uploadItem.multipart && uploadItem.status === 0" class="upload-actions">
                  <el-button size="small" link @click.stop="uploadStore.pauseUpload(index)">暂停</el-button>
                </div>
                <div v-if="uploadItem.multipart && uploadItem.status === 2" class="upload-actions">
                  <el-button size="small" type="primary" link @click.stop="uploadStore.resumeByIndex(index)">继续</el-button>
                </div>
              </el-card>
            </div>
            <VideoEdit v-if="currentVideo" :current-video="currentVideo" :clear="clearUp" :save="pushVideo" />
          </el-card>

          <el-alert v-if="videoSnackbar.show" :title="videoSnackbar.text" :type="videoSnackbar.color || 'info'" closable class="upload-alert" @close="videoSnackbar.show = false" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="图片" name="image">
        <form v-if="mode === 'image'" class="publish-form" @submit.prevent="publishImage">
          <el-input v-model="imageWork.title" placeholder="标题" required />
          <el-input v-model="imageWork.description" type="textarea" :rows="2" placeholder="简介" />
          <el-select v-model="imageWork.typeId" placeholder="分类" class="full-width" required>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="imageLabels" multiple filterable allow-create default-first-option collapse-tags
            placeholder="标签" class="full-width" />
          <el-switch v-model="imageWork.openState" active-text="仅自己可见" />

          <!-- 图片选择与上传状态 -->
          <div class="image-upload-section">
            <div class="image-thumbnails" v-if="imageUploads.length">
              <div v-for="(img, i) in imageUploads" :key="i" class="image-thumb">
                <el-image :src="img.previewUrl" fit="cover" class="thumb-img" />
                <el-progress v-if="img.status === 0" :percentage="Math.ceil(img.progress)" type="circle" :width="40" />
                <div v-else-if="img.status === 1" class="thumb-overlay success"><i class="mdi mdi-check" /></div>
                <div v-else-if="img.status === -1" class="thumb-overlay error" :title="img.msg"><i class="mdi mdi-close" /></div>
                <span v-else-if="img.status === 0" class="thumb-status">上传中</span>
                <el-icon v-if="img.status !== 0" class="thumb-remove" @click="removeImage(i)"><Close /></el-icon>
              </div>
            </div>
            <el-button size="small" plain @click="imageInput?.click()">
              <i class="mdi mdi-plus" /> 添加图片
            </el-button>
            <input ref="imageInput" type="file" accept="image/*" multiple @change="selectImages" hidden />
          </div>

          <el-tooltip :content="imageUploadHint" :disabled="imageAllUploaded" placement="top">
            <el-button class="mt-4" native-type="submit" type="primary" :disabled="!imageAllUploaded || submitting">
              发布图片作品
            </el-button>
          </el-tooltip>
        </form>
      </el-tab-pane>
      <el-tab-pane label="文字" name="text">
        <form v-if="mode === 'text'" class="publish-form" @submit.prevent="publishText">
          <el-input v-model="textWork.title" placeholder="标题" required />
          <el-input v-model="textWork.description" type="textarea" placeholder="简介" :rows="2" />
          <el-input v-model="textWork.content" type="textarea" placeholder="正文" :rows="8" required />
          <el-select v-model="textWork.typeId" placeholder="分类" class="full-width" required>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="textLabels" multiple filterable allow-create default-first-option collapse-tags
            placeholder="标签" class="full-width" />
          <el-switch v-model="textWork.openState" active-text="仅自己可见" />
          <el-button native-type="submit" type="primary" :loading="submitting">发布文字作品</el-button>
        </form>
      </el-tab-pane>
    </el-tabs>
    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'error'" closable
      @close="snackbar.show = false" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { apiClassifyGetAll } from '../../../apis/classify'
import { apiUploadFile } from '../../../apis/file'
import { apiGetAuditQueueState } from '../../../apis/video'
import { apiPublishImageWork, apiPublishTextWork } from '../../../apis/work'
import { useUploadStore } from '../../../stores'
import VideoEdit from './edit.vue'

const uploadStore = useUploadStore()
const mode = ref('video')
const categories = ref([])
const textLabels = ref([])
const imageLabels = ref([])
const imageFiles = ref([])
const imageInput = ref(null)
const imageUploads = ref([])  // [{file, progress, status, fileId, previewUrl, msg}]
const submitting = ref(false)
const uploading = ref(false)
const progress = ref(0)
const snackbar = ref({ show: false, text: '', color: 'error' })
const textWork = reactive({ title: '', description: '', content: '', typeId: null, openState: false })
const imageWork = reactive({ title: '', description: '', typeId: null, openState: false })
const videoSnackbar = ref({ show: false, text: '', color: 'info' })
const videoNoticeItems = [
  { icon: 'mdi-file-video-outline', title: '视频格式支持: mp4、avi、av、cawd', category: '视频' },
  { icon: 'mdi-shield-check-outline', title: '发布视频后会进行视频审核，七牛云服务自动审核', category: '审核' },
  { icon: 'mdi-cloud-upload-outline', title: '您的视频将会被储存在七牛云的对象存储服务中', category: '存储' }
]
const videoFileRef = ref(null)
const resumeFileRef = ref(null)
const queueState = ref('快')
const resumableUploads = ref([])

// 从全局 store 读取上传状态（切换页面不丢失）
const videoUploads = computed(() => uploadStore.videoUploads)
const currentVideoIndex = computed(() => uploadStore.currentVideoIndex)
const currentVideo = computed(() => uploadStore.currentVideo)

const showMessage = (text, color = 'error') => { snackbar.value = { show: true, text, color } }

// --- 图片上传逻辑 ---
const imageAllUploaded = computed(() =>
  imageUploads.value.length > 0 && imageUploads.value.every(img => img.status === 1)
)
const imageUploadHint = computed(() => {
  if (!imageUploads.value.length) return '请先添加图片'
  const uploadingCount = imageUploads.value.filter(i => i.status === 0).length
  if (uploadingCount > 0) return `${uploadingCount} 张图片上传中...`
  const failedCount = imageUploads.value.filter(i => i.status === -1).length
  if (failedCount > 0) return `${failedCount} 张图片上传失败，请删除后重试`
  return '等待上传完成'
})

const selectImages = (event) => {
  const files = Array.from(event.target?.files || [])
  for (const file of files) {
    const item = reactive({
      file,
      progress: 0,
      status: 0,  // 0=上传中 1=完成 -1=失败
      fileId: null,
      previewUrl: URL.createObjectURL(file),
      msg: ''
    })
    imageUploads.value.push(item)
    uploadSingleImage(item)
  }
  if (imageInput.value) imageInput.value.value = ''
}

const uploadSingleImage = (item) => {
  apiUploadFile(item.file, {
    next: (event) => { item.progress = event.total.percent },
    error: (err) => {
      item.status = -1
      item.msg = err?.message || '上传失败'
    },
    complete: (_result, response) => {
      if (response?.success) {
        item.fileId = response.data
        item.status = 1
      } else {
        item.status = -1
        item.msg = response?.message || '上传失败'
      }
    }
  })
}

const removeImage = (index) => {
  const img = imageUploads.value[index]
  if (img?.previewUrl) URL.revokeObjectURL(img.previewUrl)
  imageUploads.value.splice(index, 1)
}

const previewUrl = (file) => URL.createObjectURL(file)

const getQueueState = () => {
  apiGetAuditQueueState().then(({ data }) => {
    if (data.success) queueState.value = data.message
  })
}

const selectVideo = () => videoFileRef.value?.click()
const handleDrop = (event) => {
  const file = [...(event.dataTransfer?.files || [])].find((item) => item.type.startsWith('video/'))
  if (file) uploadVideoFile(file)
}

const clearUp = () => {
  if (currentVideoIndex.value === null) return
  uploadStore.removeUpload(currentVideoIndex.value)
}

const pushVideo = (data) => {
  videoSnackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
  if (data.success) {
    uploadStore.removeCompleted()
    getQueueState()
  }
}

const uploadVideo = () => {
  const file = videoFileRef.value?.files?.[0]
  if (file) uploadVideoFile(file)
  if (videoFileRef.value) videoFileRef.value.value = ''
}

const uploadVideoFile = (file) => {
  uploadStore.startUpload(file)
}

const triggerResumeSelect = () => resumeFileRef.value?.click()
const handleResumeSelect = (event) => {
  const file = event.target?.files?.[0]
  if (file) {
    uploadStore.resumeUpload(file)
    resumableUploads.value = []
  }
  if (resumeFileRef.value) resumeFileRef.value.value = ''
}
const dismissResumable = () => { resumableUploads.value = [] }

const uploadImage = (file) => new Promise(async (resolve, reject) => {
  try {
    await apiUploadFile(file, {
      next: (event) => { progress.value = event.total.percent },
      error: reject,
      complete: (_result, response) => {
        if (!response?.success) reject(new Error(response?.message || '图片上传失败'))
        else resolve(response.data)
      }
    })
  } catch (error) { reject(error) }
})

const publishText = async () => {
  submitting.value = true
  try {
    const { data } = await apiPublishTextWork({ ...textWork, labelNames: textLabels.value.join(',') })
    const successMessage = textWork.openState
      ? '文章已提交审核，审核结果会通过站内通知告知；当前设为仅自己可见，不会出现在公开推荐流。'
      : '文章已提交审核，通过后会出现在推荐作品流；审核结果会通过站内通知告知。'
    showMessage(data.success ? successMessage : data.message, data.success ? 'success' : 'error')
    if (data.success) {
      Object.assign(textWork, { title: '', description: '', content: '' })
      textLabels.value = []
    }
  } catch (error) { showMessage(error.message) } finally { submitting.value = false }
}

const publishImage = async () => {
  if (!imageAllUploaded.value) return
  submitting.value = true
  try {
    const imageFileIds = imageUploads.value.filter(i => i.status === 1).map(i => i.fileId)
    const { data } = await apiPublishImageWork({ ...imageWork, labelNames: imageLabels.value.join(','), imageFileIds })
    const successMessage = imageWork.openState
      ? '图片作品已提交审核，审核结果会通过站内通知告知；当前设为仅自己可见，不会出现在公开推荐流。'
      : '图片作品已提交审核，通过后会出现在推荐作品流；审核结果会通过站内通知告知。'
    showMessage(data.success ? successMessage : data.message, data.success ? 'success' : 'error')
    if (data.success) {
      imageUploads.value.forEach(i => { if (i.previewUrl) URL.revokeObjectURL(i.previewUrl) })
      imageUploads.value = []
      imageLabels.value = []
    }
  } catch (error) { showMessage(error.message) } finally { submitting.value = false }
}

onMounted(() => {
  apiClassifyGetAll().then(({ data }) => { if (data.success) categories.value = data.data || [] })
  getQueueState()
  // 检查是否有可恢复的分片上传
  resumableUploads.value = uploadStore.checkResumableUploads()
})
</script>

<style scoped>
.video-upload-page { display: grid; gap: 16px; }
.upload-intro { display: grid; grid-template-columns: minmax(0, 1fr) minmax(260px, 1fr); gap: 16px; }
.upload-dropzone { display: grid; place-items: center; align-content: center; gap: 14px; min-height: 300px; background: #fafafa; border: 1px dashed #ddd; text-align: center; }
.upload-icon { color: #ff2442; font-size: 50px; line-height: 1; }
.upload-dropzone .el-tag { margin-top: 4px; }
.notice-card { min-height: 300px; }
.notice-title { margin-bottom: 10px; font-weight: 600; }
.notice-item { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid #eee; }
.notice-item:last-child { border-bottom: 0; }
.notice-image { display: grid; width: 50px; height: 50px; flex: 0 0 50px; place-items: center; border-radius: 12px; color: #ff2442; background: #fff0f2; font-size: 25px; }
.notice-category { color: #999; font-size: 12px; text-transform: uppercase; }
.upload-workspace { margin-top: 16px; }
.workspace-title { font-size: 16px; font-weight: 600; }
.upload-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; margin-bottom: 20px; }
.upload-item { cursor: pointer; border-color: transparent; }
.upload-item.active { border-color: #409eff; }
.upload-item-title { overflow: hidden; margin-bottom: 8px; text-overflow: ellipsis; white-space: nowrap; }
.upload-error { color: #f56c6c; font-size: 12px; }
.upload-actions { margin-top: 4px; text-align: right; }
.resume-alert { margin-bottom: 0; }
.upload-alert { position: fixed; right: 24px; bottom: 24px; z-index: 20; width: min(360px, calc(100vw - 48px)); }
.card-heading { padding: 16px 0; font-size: 18px; }
.publish-form { display: flex; flex-direction: column; gap: 14px; padding: 16px 0; }
.full-width { width: 100%; }
.image-preview-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; }
.image-preview-grid .el-image { width: 100%; aspect-ratio: 1; border-radius: 4px; }
.image-upload-section { display: flex; flex-direction: column; gap: 10px; }
.image-thumbnails { display: flex; flex-wrap: wrap; gap: 10px; }
.image-thumb { position: relative; width: 80px; height: 80px; border-radius: 8px; overflow: hidden; border: 1px solid rgba(255,255,255,0.12); }
.thumb-img { width: 100%; height: 100%; }
.thumb-overlay { position: absolute; inset: 0; display: grid; place-items: center; font-size: 24px; color: #fff; }
.thumb-overlay.success { background: rgba(103, 194, 58, 0.7); }
.thumb-overlay.error { background: rgba(245, 108, 108, 0.7); }
.thumb-remove { position: absolute; top: 2px; right: 2px; cursor: pointer; color: #fff; background: rgba(0,0,0,0.5); border-radius: 50%; font-size: 14px; width: 18px; height: 18px; }
.image-thumb :deep(.el-progress) { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(0,0,0,0.45); }
.my-4 { margin: 16px 0; }
.mt-4 { margin-top: 16px; }
@media (max-width: 760px) { .upload-intro { grid-template-columns: 1fr; } }
</style>
