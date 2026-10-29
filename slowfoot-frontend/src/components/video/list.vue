<template>
  <div class="video-list">
    <div v-if="loading" class="video-grid skeleton-grid" aria-label="正在加载">
      <div v-for="item in 8" :key="item" class="skeleton-card">
        <div class="skeleton-cover" />
        <div class="skeleton-line skeleton-line-long" />
        <div class="skeleton-line skeleton-line-short" />
      </div>
    </div>
    <div v-else-if="videoList?.length" class="video-grid">
      <VideoCard v-for="(video, index) in videoList" :key="video.id || index" :video-info="video" @click="playVideo(video)" />
    </div>
    <div v-else class="empty-state">
      <span class="empty-icon"><i class="mdi mdi-compass-outline" /></span>
      <strong>{{ noDataMsg }}</strong>
      <span>新内容会在这里出现，先去别的频道逛逛吧。</span>
      <el-button type="primary" round @click="router.push('/pushVideo')">去逛推荐</el-button>
    </div>

    <el-dialog v-model="videoDialog" class="work-dialog" fullscreen :show-close="false">
      <el-card v-if="currentVideo && isVideo(currentVideo)" class="video-dialog-card" shadow="never"><Video :video-info="currentVideo" :close-video="() => playVideo(null)" /></el-card>
      <WorkViewer v-else-if="currentVideo" :work="currentVideo" @close="playVideo(null)" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import VideoCard from './card.vue'
import Video from './index.vue'
import WorkViewer from './WorkViewer.vue'

defineProps({
  videoList: { type: Array, default: () => [] },
  noDataMsg: { type: String, default: '没有找到相关作品' },
  loading: { type: Boolean, default: false }
})
const router = useRouter()
const currentVideo = ref(null)
const videoDialog = ref(false)
const isVideo = (work) => !work?.workType || work.workType === 'VIDEO'
const playVideo = (video) => {
  currentVideo.value = video
  videoDialog.value = Boolean(video)
}
</script>

<style scoped>
.video-list { min-width: 0; }
.video-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(185px, 1fr)); gap: 22px 16px; }
.skeleton-card { min-width: 0; overflow: hidden; background: #fff; border-radius: 13px; }
.skeleton-cover, .skeleton-line { background: linear-gradient(90deg, #f0f0f0 25%, #f7f7f7 50%, #f0f0f0 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-cover { aspect-ratio: 3 / 4; }
.skeleton-line { height: 10px; margin: 13px 12px 0; border-radius: 5px; }
.skeleton-line-short { width: 48%; margin-bottom: 14px; }
.empty-state { display: flex; min-height: 340px; flex-direction: column; align-items: center; justify-content: center; padding: 30px; color: #333; background: #fff; border: 1px dashed #e4e4e4; border-radius: 16px; box-sizing: border-box; text-align: center; }
.empty-icon { display: grid; width: 62px; height: 62px; margin-bottom: 16px; place-items: center; color: #ff2442; background: #fff0f2; border-radius: 50%; font-size: 30px; }
.empty-state strong { font-size: 16px; }
.empty-state > span:not(.empty-icon) { margin-top: 8px; color: #999; font-size: 12px; }
.empty-state .el-button { margin-top: 22px; }
:global(.work-dialog .el-dialog__body) { height: 100%; padding: 0; overflow: hidden; }
:global(.work-dialog .video-dialog-card) { height: 100%; background: #111; border: 0; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
@media (max-width: 600px) { .video-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 9px; } }
</style>
