<template>
  <div v-if="currentVideo" class="video-view">
    <aside v-if="drawer" class="similar-drawer">
      <el-card shadow="never" class="similar-list">
        <VideoCard v-for="(videoItem, index) in similarList" :key="index" :overlay="currentIndex === index"
          class="similar-card" :video-info="videoItem" @click="currentIndex = index" />
      </el-card>
    </aside>
    <main class="video-main">
      <el-card shadow="never" class="player-card">
        <video ref="video" class="video-js vjs-default-skin" controls :poster="currentVideo.playCover">
          <source :src="currentVideo.playUrl" :type="currentVideo.format || 'video/mp4'" />
        </video>
        <el-button v-if="!hideClose" class="close-button" circle @click="closeVideo">
          <i class="mdi mdi-close" />
        </el-button>
        <div class="player-actions">
          <el-badge value="+" type="danger" :hidden="isOwnCreator(currentVideo)">
            <el-avatar v-if="currentVideo.user?.avatar" :src="apiFileGet(currentVideo.user.avatar)" @click="likeUser" />
            <DefaultAvatar v-else :size="40" @click="likeUser" />
          </el-badge>
          <el-button circle type="primary" @click="openRgihtD">
            <i class="mdi mdi-more" />
          </el-button>
          <el-badge :value="currentVideo.likeCount" type="danger">
            <el-button circle type="danger" :loading="likePending" @click="starVideo"><i class="mdi mdi-heart" /></el-button>
          </el-badge>
          <FavoriteCom :video-id="currentVideo.id" :callback="favoriteCallBack">
            <template #default="{ props }">
              <el-badge :value="currentVideo.favoriteCount" type="warning">
                <el-button v-bind="props" circle type="warning"><i class="mdi mdi-star" /></el-button>
              </el-badge>
            </template>
          </FavoriteCom>
          <el-badge :value="currentVideo.shareCount" type="success">
            <el-button circle type="success" @click="copyUrl"><i class="mdi mdi-near-me" /></el-button>
          </el-badge>
        </div>
      </el-card>
      <WorkComments :work-id="currentVideo.id" />
      <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'info'" closable
        @close="snackbar.show = false" />
    </main>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { apiFileGet } from '../../apis/file'
import { apiGetUserInfo } from '../../apis/user/user'
import { apiFollows } from '../../apis/user/like'
import { apiAddHistory, apiGetVideoBySimilar, apiInitFollowFeed, apiSetUserVideoModel, apiShareVideo, apiStarVideo } from '../../apis/video'
import FavoriteCom from '../../components/favorite/index.vue'
import VideoCard from '../../components/video/card.vue'
import WorkComments from '../../components/video/comments.vue'
import DefaultAvatar from '../../components/DefaultAvatar.vue'
import strUtils from '../../utils/strUtil'
import { useUserStore } from '../../stores'

const props = defineProps({
  videoInfo: { type: Object, default: null },
  videoList: { type: Array, default: () => [] },
  hideClose: { type: Boolean, default: false },
  nextVideo: { type: Function, default: () => {} },
  closeVideo: { type: Function, default: () => {} }
})

const snackbar = ref({ show: false, text: '', color: 'info' })
const userStore = useUserStore()
const drawer = ref(true)
const currentIndex = ref(0)
const similarList = ref([props.videoInfo])
const video = ref()
const videoPlayer = ref()
const isAddHistory = ref(true)
const playback = ref({ workId: null, watchSessionId: null, reported: false })
const likePending = ref(false)
const instance = getCurrentInstance().proxy
const isVideoWork = (work) => !work?.workType || work.workType === 'VIDEO'

const currentVideo = computed(() => {
  const temp = similarList.value[currentIndex.value]
  if (!temp) return null
  temp.playUrl = apiFileGet(temp.primaryFileId)
  temp.playCover = apiFileGet(temp.coverFileId)
  return temp
})

const shareBtn = () => apiShareVideo(currentVideo.value.id)
const openRgihtD = () => { drawer.value = !drawer.value }
const favoriteCallBack = (message, changed) => {
  if (changed) currentVideo.value.favoriteCount++
  currentVideo.value.favoriteState = true
  snackbar.value = { show: true, text: message, color: 'info' }
}

const handleMouseWheel = (event) => {
  if (event.deltaY > 0 && currentIndex.value < similarList.value.length - 1) currentIndex.value++
  if (event.deltaY < 0 && currentIndex.value > 0) currentIndex.value--
}

const windowKeyEvent = (event) => {
  if (event.which === 38 && currentIndex.value > 0) currentIndex.value--
  if (event.which === 40 && currentIndex.value < similarList.value.length - 1) currentIndex.value++
  if (event.which === 27) props.closeVideo()
  if (event.which === 70) videoPlayer.value?.requestFullscreen()
}

const copyUrl = () => {
  shareBtn()
  snackbar.value = { text: strUtils.copyContent(`${location.host}/#/?play=${currentVideo.value.id}`) ? '作品地址复制成功' : '作品地址复制失败', show: true, color: 'info' }
}

const createWatchSessionId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

const reportWatchProgress = () => {
  if (playback.value.reported || !playback.value.workId || !videoPlayer.value) return
  const watchTimeSeconds = Number(videoPlayer.value.currentTime())
  const durationSeconds = Number(videoPlayer.value.duration())
  if (!Number.isFinite(watchTimeSeconds) || !Number.isFinite(durationSeconds) || durationSeconds <= 0) return
  playback.value.reported = true
  apiSetUserVideoModel(playback.value.workId, Math.min(watchTimeSeconds, durationSeconds), playback.value.watchSessionId)
    .catch(() => { playback.value.reported = false })
}

const beginPlayback = (item) => {
  playback.value = { workId: item.id, watchSessionId: createWatchSessionId(), reported: false }
  isAddHistory.value = true
}

const firstInitVideo = () => {
  if (videoPlayer.value || !currentVideo.value) return
  videoPlayer.value = instance.$video(video.value, { playbackRates: [0.5, 1, 1.5, 2], fill: true, autoplay: true })
  videoPlayer.value.volume(localStorage.getItem('volume') || 1)
  document.addEventListener('wheel', handleMouseWheel)
  window.addEventListener('keydown', windowKeyEvent)
  videoPlayer.value.on('volumechange', () => localStorage.setItem('volume', videoPlayer.value.volume()))
  videoPlayer.value.on('timeupdate', function () {
    if (this.currentTime() >= 3 && isAddHistory.value && playback.value.workId) {
      isAddHistory.value = false
      apiAddHistory(playback.value.workId)
    }
  })
  videoPlayer.value.on('ended', reportWatchProgress)
  beginPlayback(currentVideo.value)
  nextTick(() => videoPlayer.value.play())
  if (!props.videoList.length && props.videoInfo?.labelNames) {
    apiGetVideoBySimilar(props.videoInfo.labelNames, props.videoInfo.id).then(({ data }) => {
      if (data.success) similarList.value = similarList.value.concat((data.data || []).filter(isVideoWork))
    })
  }
}

const isOwnCreator = (item) => userStore.info?.id != null
  && item?.user?.id != null
  && String(userStore.info.id) === String(item.user.id)

const likeUser = async () => {
  if (!userStore.token || !currentVideo.value?.user?.id) {
    snackbar.value = { text: '请先登录后再关注创作者', show: true, color: 'warning' }
    return
  }

  try {
    let currentUserId = userStore.info?.id
    if (currentUserId == null) {
      const { data } = await apiGetUserInfo()
      if (!data?.success || data.data?.id == null) throw new Error(data?.message || '无法确认当前登录用户')
      userStore.$patch({ info: data.data })
      currentUserId = data.data.id
    }
    if (String(currentUserId) === String(currentVideo.value.user.id)) {
      snackbar.value = { text: '这是你自己的作品，无需关注自己', show: true, color: 'info' }
      return
    }

    const { data } = await apiFollows(currentVideo.value.user.id)
    if (data.message === '已关注') apiInitFollowFeed()
    snackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
  } catch (error) {
    snackbar.value = {
      text: error?.response?.data?.message || error?.message || '关注操作失败，请稍后再试',
      show: true,
      color: 'error'
    }
  }
}

const starVideo = async () => {
  if (likePending.value) return
  const liked = !currentVideo.value.likeState
  likePending.value = true
  try {
    const { data } = await apiStarVideo(currentVideo.value.id, liked)
    snackbar.value = { show: true, text: data.message, color: data.success ? 'success' : 'error' }
    if (data.success) {
      currentVideo.value.likeState = liked
      if (data.data) currentVideo.value.likeCount += liked ? 1 : -1
    }
  } finally {
    likePending.value = false
  }
}

const playVideo = (item) => {
  if (!item || !videoPlayer.value) return
  if (playback.value.workId === item.id) return
  reportWatchProgress()
  props.nextVideo(currentIndex.value)
  setTimeout(() => {
    beginPlayback(item)
    videoPlayer.value.src([{ src: item.playUrl, type: item.format, poster: item.playCover }])
    videoPlayer.value.load()
    videoPlayer.value.play()
  }, 10)
}

watch(() => props.videoList, (value) => {
  if (value?.length) similarList.value = value.filter(isVideoWork)
}, { immediate: true, deep: true })
watch(currentVideo, playVideo)

onMounted(() => {
  if (window.innerWidth < 800) drawer.value = false
  firstInitVideo()
})
onUnmounted(() => {
  reportWatchProgress()
  document.removeEventListener('wheel', handleMouseWheel)
  window.removeEventListener('keydown', windowKeyEvent)
  videoPlayer.value?.dispose()
  videoPlayer.value = null
})
</script>

<style scoped>
.video-view { display: flex; min-height: 100%; }
.similar-drawer { flex: 0 0 350px; padding: 16px; background: #252632; }
.similar-list { background: #252632; border: 0; }
.similar-card { margin-bottom: 16px; }
.video-main { position: relative; flex: 1; min-width: 0; }
.player-card { position: relative; height: 100%; background: #252632; border: 0; }
.player-card :deep(.el-card__body) { height: 100%; padding: 0; }
.video-js { width: 100%; min-height: 520px; }
.close-button { position: absolute; top: 16px; left: 16px; z-index: 2; }
.player-actions { position: absolute; right: 24px; bottom: 24px; z-index: 2; display: flex; flex-direction: column; gap: 12px; align-items: center; }
@media (max-width: 800px) { .similar-drawer { display: none; } .player-actions { right: 12px; bottom: 12px; } }
</style>
