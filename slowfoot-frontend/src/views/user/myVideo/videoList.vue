<template>
  <el-card shadow="never">
    <div v-if="videoList.length" class="video-list">
      <div v-for="item in videoList" :key="item.id" class="video-row">
        <div class="video-cover-wrap" @click="playVideo(item)">
          <el-image class="video-cover" :src="apiFileGet(item.coverFileId)" fit="cover" />
          <el-tag v-if="item.auditStatus === 2" type="warning" class="audit-tag">{{ item.auditMsg }}</el-tag>
          <el-tag v-else-if="item.auditStatus > 0" type="warning" class="audit-tag">审核中</el-tag>
        </div>
        <div class="video-details">
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
          <div class="video-labels">
            <el-tag v-for="label in getLabels(item)" :key="label" size="small">{{ label }}</el-tag>
          </div>
        </div>
        <div class="video-actions">
          <el-button type="primary" plain @click="edit(item)">编辑</el-button>
          <el-button type="danger" plain @click="openDelete(item)">删除</el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">暂无视频</div>

    <el-pagination v-if="pageInfo.pages > 1" v-model:current-page="pageInfo.page" :page-count="pageInfo.pages" class="page-control" />

    <el-dialog v-model="dialog" title="删除视频" width="380px">
      <p>确定要删除该视频吗？</p>
      <p class="dialog-video-title">视频名: {{ videoInfo.title }}</p>
      <template #footer>
        <el-button @click="dialog = false">不确定</el-button>
        <el-button type="danger" @click="removeVideo">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="videoDialog" fullscreen>
      <Video v-if="currentVideo" :video-info="currentVideo" :close-video="() => playVideo(null)" />
    </el-dialog>

    <el-dialog v-model="editDialog" title="信息编辑" width="500px">
      <VideoEdit :current-video="videoInfo" class="edit-video-form" :clear="clear" :save="saveEdit" />
    </el-dialog>

    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'info'" closable class="page-alert" @close="snackbar.show = false" />
  </el-card>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { apiFileGet } from '../../../apis/file'
import { apiRemoveVideo } from '../../../apis/user/videoManger'
import { apiGetVideoByUser } from '../../../apis/video'
import Video from '../../../components/video/index.vue'
import VideoEdit from './edit.vue'

const pageInfo = ref({ page: 1, pages: 0 })
const videoList = ref([])
const dialog = ref(false)
const editDialog = ref(false)
const videoInfo = ref({})
const snackbar = ref({ show: false, text: '', color: 'info' })
const currentVideo = ref(null)
const videoDialog = ref(false)

const getVideo = () => {
  apiGetVideoByUser(pageInfo.value.page).then(({ data }) => {
    if (!data.success) return
    videoList.value = data.data.records || []
    pageInfo.value.pages = data.data.pages || 0
  })
}

const edit = (info) => {
  videoInfo.value = { ...info, labelNames: (info.labelNames || '').split(',').filter(Boolean) }
  editDialog.value = true
}

const openDelete = (info) => {
  videoInfo.value = info
  dialog.value = true
}

const removeVideo = () => {
  apiRemoveVideo(videoInfo.value.id).then(({ data }) => {
    snackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
    dialog.value = false
    if (data.success) {
      videoInfo.value = {}
      getVideo()
    }
  })
}

const saveEdit = (data) => {
  snackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
  if (data.success) {
    clear()
    getVideo()
  }
}

const clear = () => {
  videoInfo.value = {}
  editDialog.value = false
}

const getLabels = (item) => (item?.labelNames || '').split(',').map((label) => label.trim()).filter(Boolean)
const playVideo = (video) => {
  currentVideo.value = video
  videoDialog.value = Boolean(video)
}

onMounted(getVideo)
watch(() => pageInfo.value.page, getVideo)
</script>

<style scoped>
.video-list { display: grid; gap: 14px; }
.video-row { display: flex; align-items: center; gap: 16px; min-height: 110px; padding: 8px 0; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.video-cover-wrap { position: relative; width: 200px; height: 100px; flex: 0 0 200px; cursor: pointer; }
.video-cover { width: 100%; height: 100%; border-radius: 4px; }
.audit-tag { position: absolute; top: 6px; left: 6px; max-width: calc(100% - 12px); }
.video-details { display: grid; flex: 1; gap: 8px; min-width: 0; }
.video-details > span { overflow: hidden; line-height: 25px; text-overflow: ellipsis; white-space: nowrap; }
.video-labels { display: flex; flex-wrap: wrap; gap: 6px; }
.video-actions { display: flex; flex: 0 0 auto; gap: 6px; }
.empty-state { display: grid; place-items: center; min-height: 240px; color: #aeb4c0; }
.page-control { justify-content: center; margin-top: 18px; }
.dialog-video-title { color: #aeb4c0; }
.edit-video-form { margin: 4px; }
.page-alert { position: fixed; right: 24px; bottom: 24px; z-index: 20; width: min(360px, calc(100vw - 48px)); }
@media (max-width: 760px) {
  .video-row { align-items: flex-start; flex-wrap: wrap; }
  .video-details { flex-basis: calc(100% - 216px); }
  .video-actions { margin-left: 216px; }
}
</style>
