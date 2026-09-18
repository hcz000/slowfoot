<template>
  <div class="video-list">
    <div v-if="loading" class="video-grid skeleton-grid" aria-label="正在加载">
      <div v-for="item in 6" :key="item" class="skeleton-card">
        <div class="skeleton-cover" />
        <div class="skeleton-line skeleton-line-long" />
        <div class="skeleton-line skeleton-line-short" />
      </div>
    </div>
    <div v-else-if="videoList?.length" class="video-grid">
      <VideoCard v-for="(video, index) in videoList" :key="video.id || index" :video-info="video" @click="playVideo(video)" />
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon"><VideoCamera /></div>
      <strong>{{ noDataMsg }}</strong>
      <span>新内容会在这里出现，先去别的频道逛逛吧。</span>
      <el-button type="primary" plain size="small" @click="router.push('/pushVideo')">看看推荐</el-button>
    </div>

    <el-dialog v-model="videoDialog" fullscreen :show-close="false">
      <el-card v-if="currentVideo" shadow="never"><Video :video-info="currentVideo" :close-video="() => playVideo(null)" /></el-card>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { VideoCamera } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import VideoCard from './card.vue'
import Video from './index.vue'

defineProps({
  videoList: { type: Array, default: () => [] },
  noDataMsg: { type: String, default: '没有找到相关视频' },
  loading: { type: Boolean, default: false }
})
const router = useRouter()
const currentVideo = ref(null)
const videoDialog = ref(false)
const playVideo = (video) => {
  currentVideo.value = video
  videoDialog.value = Boolean(video)
}
</script>

<style scoped>
.video-list { min-width: 0; }
.video-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 20px; }
.skeleton-card { min-width: 0; overflow: hidden; background: #171d25; border: 1px solid #222b36; border-radius: 10px; }
.skeleton-cover, .skeleton-line { background: linear-gradient(90deg, #1d2530 25%, #27313d 50%, #1d2530 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-cover { aspect-ratio: 16 / 10; }
.skeleton-line { height: 10px; margin: 14px 14px 0; border-radius: 4px; }
.skeleton-line-short { width: 48%; margin-bottom: 16px; }
.empty-state { display: flex; min-height: 320px; flex-direction: column; align-items: center; justify-content: center; padding: 30px; color: #f0f3f7; background: #171d25; border: 1px solid #252e3a; border-radius: 10px; box-sizing: border-box; text-align: center; }
.empty-icon { display: grid; place-items: center; width: 56px; height: 56px; margin-bottom: 18px; color: #f2bd5c; background: #282b2b; border-radius: 16px; }
.empty-state strong { font-size: 15px; }
.empty-state span { margin-top: 8px; color: #778597; font-size: 12px; }
.empty-state .el-button { margin-top: 21px; border-radius: 6px; }
@keyframes shimmer { from { background-position: 200% 0; } to { background-position: -200% 0; } }
</style>
