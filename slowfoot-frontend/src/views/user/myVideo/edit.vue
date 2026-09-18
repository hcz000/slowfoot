<template>
  <div class="video-edit-form">
    <el-form label-position="top">
      <el-form-item label="视频标题">
        <el-input v-model="currentVideo.title" clearable />
      </el-form-item>
      <el-form-item label="视频描述">
        <el-input v-model="currentVideo.description" type="textarea" :rows="3" clearable />
      </el-form-item>
      <el-form-item v-if="!currentVideo.id" label="视频封面">
        <div class="cover-picker" @click="avatarFileRef?.click()">
          <el-image :key="currentVideo.coverFileId" :src="coverImg" fit="cover" />
          <div class="cover-picker-overlay">
            <el-progress v-if="uploading > -1" type="circle" :percentage="uploading" :width="46" />
            <i v-else class="mdi mdi-upload" />
          </div>
        </div>
        <input ref="avatarFileRef" hidden type="file" accept="image/*" @change="uploadAvatar" />
      </el-form-item>
      <el-form-item label="视频分类">
        <el-select v-model="currentVideo.typeId" filterable placeholder="请选择视频分类" no-data-text="无视频分类">
          <el-option v-for="item in allClassifyList" :key="item.id" :label="item.name" :value="item.id">
            <span class="option-with-icon">
              <i :class="['mdi', item.icon || 'mdi-file-document-alert-outline']" />
              {{ item.name }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="视频标签">
        <el-select v-model="currentVideo.labelNames" filterable allow-create default-first-option multiple placeholder="输入标签后回车">
          <el-option v-for="label in currentVideo.labelNames || []" :key="label" :label="label" :value="label" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-divider />
    <div class="form-actions">
      <el-button type="warning" plain @click="clearUp">取消</el-button>
      <el-button type="success" plain @click="pushVideo">发布视频</el-button>
    </div>
    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'info'" closable class="form-alert" @close="snackbar.show = false" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, toRef } from 'vue'
import { apiClassifyGetAll } from '../../../apis/classify'
import { apiFileGet, apiUploadFile } from '../../../apis/file'
import { apiVideoPush } from '../../../apis/user/videoManger'

const props = defineProps({
  currentVideo: { type: Object, default: () => ({}) },
  clear: { type: Function, default: () => {} },
  save: { type: Function, default: () => {} }
})
const currentVideo = toRef(props, 'currentVideo')
const avatarFileRef = ref(null)
const uploading = ref(-1)
const allClassifyList = ref([])
const snackbar = ref({ show: false, text: '', color: 'info' })

const clearUp = () => props.clear()

const uploadAvatar = () => {
  const file = avatarFileRef.value?.files?.[0]
  if (!file) return
  apiUploadFile(file, {
    next: (event) => { uploading.value = event.total.percent },
    error: () => {
      uploading.value = -1
      snackbar.value = { text: '上传失败', show: true, color: 'error' }
    },
    complete: (_event, result) => {
      uploading.value = -1
      if (!result.success) {
        snackbar.value = { text: result.message, show: true, color: 'error' }
        return
      }
      currentVideo.value.coverFileId = result.data
      snackbar.value = { text: '上传完成', show: true, color: 'success' }
      avatarFileRef.value.value = ''
    }
  })
}

const coverImg = computed(() => {
  if (avatarFileRef.value?.files?.[0]) return URL.createObjectURL(avatarFileRef.value.files[0])
  return currentVideo.value.coverFileId ? apiFileGet(currentVideo.value.coverFileId) : '/logo.png'
})

onMounted(() => {
  apiClassifyGetAll().then(({ data }) => {
    allClassifyList.value = data.success ? data.data : []
  })
})

const pushVideo = () => {
  apiVideoPush(currentVideo.value).then(({ data }) => props.save(data))
}
</script>

<style scoped>
.video-edit-form { display: grid; gap: 4px; }
.el-select { width: 100%; }
.cover-picker { position: relative; width: 150px; height: 150px; overflow: hidden; border-radius: 8px; cursor: pointer; background: #2f3136; }
.cover-picker > .el-image { width: 100%; height: 100%; }
.cover-picker-overlay { position: absolute; inset: 0; display: grid; place-items: center; color: white; background: rgba(0, 0, 0, 0.45); opacity: 0; transition: opacity 0.15s ease; }
.cover-picker:hover .cover-picker-overlay { opacity: 1; }
.option-with-icon { display: flex; align-items: center; gap: 8px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; }
.form-alert { position: fixed; right: 24px; bottom: 24px; z-index: 20; width: min(360px, calc(100vw - 48px)); }
</style>
