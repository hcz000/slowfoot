<template>
  <div class="recommendation-feed">
    <div v-if="loading && !videoList.length" class="feed-state">
      <i class="mdi mdi-loading mdi-spin" />
      <strong>正在为你挑选作品</strong>
      <span>马上就好</span>
    </div>

    <div v-else-if="!videoList.length" class="feed-state">
      <i class="mdi mdi-view-grid-outline" />
      <strong>{{ error ? '作品暂时加载失败' : '暂时没有推荐作品' }}</strong>
      <span>{{ error ? '检查网络后可以再试一次。' : '先去热门内容看看，新的作品会继续出现。' }}</span>
      <div class="state-actions">
        <el-button v-if="error" type="primary" @click="$emit('retry')">重新加载</el-button>
        <el-button v-else type="primary" @click="router.push('/')">浏览热门内容</el-button>
      </div>
    </div>

    <div v-else ref="feedScroll" class="feed-scroll" tabindex="0" @scroll.passive="onScroll" @keydown="onKeydown">
      <section v-for="(item, index) in videoList" :key="getWorkId(item) || index" class="feed-slide" :class="{ 'text-slide': isText(item) }">
        <div class="feed-stage" :class="{ 'text-work-stage': isText(item) }">
          <article class="feed-video-card" :class="`work-${String(item.workType || 'video').toLowerCase()}`" @dblclick.prevent="isVideo(item) && likeVideo(item)">
            <video
              v-if="isVideo(item)"
              :ref="(element) => setVideoRef(element, index)"
              class="feed-video"
              :poster="getCoverUrl(item)"
              :preload="Math.abs(index - activeIndex) <= 1 ? 'auto' : 'none'"
              :muted="muted"
              playsinline
              webkit-playsinline
              loop
              @click="togglePlayback(index)"
              @play="onPlay(index)"
              @pause="onPause(index)"
              @timeupdate="onTimeUpdate(item, index, $event)"
              @ended="reportPlayback(item)"
            >
              <source :src="getVideoUrl(item)" :type="item.format || 'video/mp4'" />
            </video>

            <WorkPager v-else class="feed-work-pager" :work="item" :show-meta="isText(item)" />

            <div v-if="!isText(item)" class="video-shade" />
            <div class="feed-topline">
              <span><i />{{ workTypeName(item) }} · 为你推荐</span>
              <span class="feed-position">{{ index + 1 }} <small>/ {{ videoList.length }}</small></span>
            </div>

            <button v-if="isVideo(item)" class="utility-button sound-button" type="button" :aria-label="muted ? '打开声音' : '静音'" @click.stop="toggleMuted">
              <i :class="muted ? 'mdi mdi-volume-mute' : 'mdi mdi-volume-high'" />
              <span>{{ muted ? '打开声音' : '静音' }}</span>
            </button>

            <button v-if="isVideo(item) && activeIndex === index && !isPlaying" class="play-overlay" type="button" aria-label="播放视频" @click.stop="togglePlayback(index)">
              <i class="mdi mdi-play" />
            </button>

            <div v-if="!isText(item)" class="feed-copy">
              <button v-if="item.user" class="creator-name" type="button" @click.stop="openCreator(item)">
                {{ item.user.nickName || 'SlowFoot 创作者' }}
              </button>
              <span v-else class="creator-name">SlowFoot 创作者</span>
              <h1>{{ item.title || '未命名作品' }}</h1>
              <p v-if="item.description && !isText(item)">{{ item.description }}</p>
              <div v-if="getTags(item).length" class="feed-tags">
                <span v-for="tag in getTags(item)" :key="tag"># {{ tag }}</span>
              </div>
            </div>

            <div v-if="isVideo(item)" class="video-progress"><span :style="{ width: index === activeIndex ? `${progressPercent}%` : '0%' }" /></div>
          </article>

          <aside class="feed-actions" aria-label="作品互动">
            <div class="creator-actions">
              <button class="avatar-button" type="button" aria-label="打开创作者主页" @click="openCreator(item)">
                <el-avatar v-if="item.user?.avatar" :src="apiFileGet(item.user.avatar)" :size="48" />
                <DefaultAvatar v-else :size="48" />
              </button>
              <button v-if="!isOwnCreator(item)" class="follow-button" :class="{ following: item.followState }" type="button" :aria-label="item.followState ? '取消关注' : '关注'" :title="item.followState ? '取消关注' : '关注'" :disabled="followPending" @click="followCreator(item)">
                <i :class="item.followState ? 'mdi mdi-check' : 'mdi mdi-plus'" />
              </button>
            </div>

            <button class="feed-action like-action" :class="{ active: item.likeState }" type="button" :aria-label="item.likeState ? '取消点赞' : '点赞'" :title="item.likeState ? '取消点赞' : '点赞'" :disabled="likePending" @click.stop="likeVideo(item)">
              <i :class="item.likeState ? 'mdi mdi-heart' : 'mdi mdi-heart-outline'" />
              <strong>{{ formatCount(item.likeCount) }}</strong>
            </button>

            <button class="feed-action" type="button" aria-label="评论" title="评论" @click="openComments(item)">
              <i class="mdi mdi-comment-text-outline" />
            </button>

            <FavoriteCom :video-id="getWorkId(item)" :callback="(message, changed) => onFavorite(item, message, changed)">
              <template #default="{ props: favoriteProps }">
                <button class="feed-action" type="button" aria-label="收藏" title="收藏" @click.stop="openFavorites(favoriteProps)">
                  <i :class="item.favoriteState ? 'mdi mdi-bookmark' : 'mdi mdi-bookmark-outline'" />
                  <strong>{{ formatCount(item.favoriteCount) }}</strong>
                </button>
              </template>
            </FavoriteCom>

            <button class="feed-action" type="button" aria-label="分享" title="分享" @click="shareVideo(item)">
              <i class="mdi mdi-share-variant-outline" />
              <strong>{{ formatCount(item.shareCount) }}</strong>
            </button>
          </aside>
        </div>
      </section>

      <el-drawer v-model="commentsOpen" class="feed-comments-drawer" :with-header="false" direction="rtl" size="440px" append-to-body destroy-on-close>
        <div class="comments-drawer-heading">
          <div>
            <span>作品评论</span>
            <strong>{{ commentsVideo?.title || '和大家聊聊' }}</strong>
          </div>
          <el-button text @click="commentsOpen = false">关闭</el-button>
        </div>
        <WorkComments v-if="commentsVideo" :key="getWorkId(commentsVideo)" :work-id="getWorkId(commentsVideo)" />
      </el-drawer>
    </div>

    <div v-if="videoList.length && (loadingMore || error)" class="loading-more" aria-live="polite">
      <template v-if="loadingMore"><i class="mdi mdi-loading mdi-spin" /> 加载更多作品</template>
      <button v-else type="button" @click="$emit('retry')"><i class="mdi mdi-refresh" /> 加载失败，点击重试</button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { apiFileGet } from '../../apis/file'
import { apiGetUserInfo } from '../../apis/user/user'
import { apiFollows } from '../../apis/user/like'
import { apiAddHistory, apiInitFollowFeed, apiSetUserVideoModel, apiShareVideo, apiStarVideo } from '../../apis/video'
import FavoriteCom from '../favorite/index.vue'
import WorkComments from './comments.vue'
import WorkPager from './WorkPager.vue'
import DefaultAvatar from '../DefaultAvatar.vue'
import { useUserStore } from '../../stores'
import strUtils from '../../utils/strUtil'

const props = defineProps({
  videoList: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  loadingMore: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})
const emit = defineEmits(['index-change', 'retry'])
const router = useRouter()
const userStore = useUserStore()
const feedScroll = ref(null)
const videoRefs = ref([])
const activeIndex = ref(0)
const muted = ref(true)
const isPlaying = ref(false)
const progressPercent = ref(0)
const likePending = ref(false)
const followPending = ref(false)
const commentsOpen = ref(false)
const commentsVideo = ref(null)
const playback = ref({ workId: null, index: -1, watchSessionId: null, reported: false, historyAdded: false })
const activeVideo = computed(() => props.videoList[activeIndex.value] || null)

// API versions may expose the work primary key as id or workId. Prefer the
// explicit work key so interaction requests never accidentally use a file id.
const getWorkId = (item) => item?.workId ?? item?.work_id ?? item?.work?.id ?? item?.id
const setVideoRef = (element, index) => { videoRefs.value[index] = element || null }
const isVideo = (item) => !item?.workType || item.workType === 'VIDEO'
const isImage = (item) => item?.workType === 'IMAGE'
const isText = (item) => item?.workType === 'TEXT'
const workTypeName = (item) => isVideo(item) ? '视频' : isImage(item) ? '图片' : '文章'
const getVideoUrl = (item) => item?.primaryFileId ? apiFileGet(item.primaryFileId) : ''
const getCoverUrl = (item) => item?.coverFileId ? apiFileGet(item.coverFileId) : ''
const getTags = (item) => (item?.labelNames || '').split(',').map((tag) => tag.trim()).filter(Boolean).slice(0, 4)
const formatCount = (value) => {
  const count = Number(value || 0)
  if (count >= 1000000) return `${(count / 1000000).toFixed(1)}m`
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
  return count.toLocaleString()
}
const createWatchSessionId = () => globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`

const beginPlayback = (item) => {
  playback.value = {
    workId: getWorkId(item) ?? null,
    index: activeIndex.value,
    watchSessionId: item ? createWatchSessionId() : null,
    reported: false,
    historyAdded: false
  }
  progressPercent.value = 0
}

const reportPlayback = (item) => {
  const session = playback.value
  const workId = getWorkId(item)
  if (!item || workId == null || session.reported || session.workId !== workId) return
  const player = videoRefs.value[session.index]
  if (!player) return
  const watched = Number(player.currentTime)
  const duration = Number(player.duration)
  if (!Number.isFinite(watched) || !Number.isFinite(duration) || duration <= 0) return
  session.reported = true
  apiSetUserVideoModel(workId, Math.min(watched, duration), session.watchSessionId)
    .catch(() => { session.reported = false })
}

const onTimeUpdate = (item, index, event) => {
  if (index !== activeIndex.value) return
  const player = event.currentTarget
  const duration = Number(player.duration)
  if (Number.isFinite(duration) && duration > 0) progressPercent.value = Math.min(100, player.currentTime / duration * 100)
  if (!playback.value.historyAdded && player.currentTime >= 3) {
    playback.value.historyAdded = true
    apiAddHistory(getWorkId(item)).catch(() => { playback.value.historyAdded = false })
  }
}

const onPlay = (index) => { if (index === activeIndex.value) isPlaying.value = true }
const onPause = (index) => { if (index === activeIndex.value) isPlaying.value = false }
const togglePlayback = (index) => {
  if (index !== activeIndex.value) return
  const player = videoRefs.value[index]
  if (!player) return
  if (player.paused) player.play().catch(() => { isPlaying.value = false })
  else player.pause()
}
const toggleMuted = () => { muted.value = !muted.value }

const setActiveIndex = (index) => {
  const nextIndex = Math.max(0, Math.min(index, props.videoList.length - 1))
  if (nextIndex === activeIndex.value) return
  reportPlayback(activeVideo.value)
  videoRefs.value[activeIndex.value]?.pause()
  activeIndex.value = nextIndex
  emit('index-change', nextIndex)
}
const onScroll = () => {
  const container = feedScroll.value
  if (!container?.clientHeight) return
  setActiveIndex(Math.round(container.scrollTop / container.clientHeight))
}
const onKeydown = (event) => {
  if (event.key === 'ArrowDown' || event.key === 'PageDown') {
    event.preventDefault()
    feedScroll.value?.scrollBy({ top: feedScroll.value.clientHeight, behavior: 'smooth' })
  }
  if (event.key === 'ArrowUp' || event.key === 'PageUp') {
    event.preventDefault()
    feedScroll.value?.scrollBy({ top: -feedScroll.value.clientHeight, behavior: 'smooth' })
  }
}

const requireLogin = () => {
  if (userStore.token) return true
  ElMessage.info('登录后可以和创作者互动')
  router.push({ path: '/', query: { login: 'required' } })
  return false
}
const isOwnCreator = (item) => userStore.info?.id != null
  && item?.user?.id != null
  && String(userStore.info.id) === String(item.user.id)

const likeVideo = async (item = activeVideo.value) => {
  if (!item || likePending.value || !requireLogin()) return
  const workId = getWorkId(item)
  if (workId == null) {
    ElMessage.error('无法识别这个作品的编号，请刷新推荐列表')
    return
  }
  const liked = !Boolean(item.likeState)
  const previousLiked = Boolean(item.likeState)
  const previousCount = Number(item.likeCount || 0)
  likePending.value = true
  item.likeState = liked
  item.likeCount = Math.max(0, previousCount + (liked ? 1 : -1))
  try {
    const { data } = await apiStarVideo(workId, liked)
    if (!data?.success) throw new Error(data?.message || '点赞失败')
    if (!data.data) item.likeCount = previousCount
    ElMessage.success(liked ? (data.data ? '点赞成功' : '已经点过赞了') : (data.data ? '已取消点赞' : '当前没有点赞'))
  } catch (error) {
    item.likeState = previousLiked
    item.likeCount = previousCount
    const response = error?.response?.data
    if (response?.errorCode === 'WORK_NOT_FOUND') {
      ElMessage.error(`服务器找不到作品 ${workId}，请刷新推荐列表后再点赞`)
    } else {
      ElMessage.error(response?.message || error?.message || '点赞失败，请稍后重试')
    }
  }
  finally { likePending.value = false }
}
const followCreator = async (item) => {
  if (!item?.user?.id || followPending.value || !requireLogin()) return
  followPending.value = true
  try {
    let currentUserId = userStore.info?.id
    if (currentUserId == null) {
      const { data: userResponse } = await apiGetUserInfo()
      if (!userResponse?.success || userResponse.data?.id == null) {
        throw new Error(userResponse?.message || '暂时无法确认当前登录用户，请稍后重试')
      }
      userStore.$patch({ info: userResponse.data })
      currentUserId = userResponse.data.id
    }
    if (String(currentUserId) === String(item.user.id)) {
      ElMessage.info('这是你自己的作品，无需关注自己')
      return
    }

    const { data } = await apiFollows(item.user.id)
    if (!data.success) { ElMessage.error(data.message || '关注操作失败'); return }
    item.followState = !Boolean(item.followState)
    if (item.followState) apiInitFollowFeed().catch(() => {})
    ElMessage.success(item.followState ? '已关注创作者' : '已取消关注')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '关注操作失败，请稍后再试')
  }
  finally { followPending.value = false }
}
const openFavorites = (favoriteProps) => {
  if (!requireLogin()) return
  if (typeof favoriteProps?.onClick !== 'function') {
    ElMessage.error('收藏面板暂时无法打开，请刷新页面后重试')
    return
  }
  favoriteProps.onClick()
}
const onFavorite = (item, message, changed) => {
  if (changed) item.favoriteCount = Number(item.favoriteCount || 0) + 1
  item.favoriteState = true
  ElMessage.success(message || '已收藏到收藏夹')
}
const openComments = (item) => {
  commentsVideo.value = item
  commentsOpen.value = true
}
const shareVideo = async (item) => {
  const workId = getWorkId(item)
  if (workId == null) {
    ElMessage.error('无法识别这个作品的编号，请刷新推荐列表')
    return
  }
  const copied = strUtils.copyContent(`${location.origin}/#/?play=${workId}`)
  try {
    const { data } = await apiShareVideo(workId)
    if (data.success) item.shareCount = Number(item.shareCount || 0) + (data.data ? 1 : 0)
  } catch { /* copying the link still works if share tracking is unavailable */ }
  ElMessage[copied ? 'success' : 'warning'](copied ? '作品链接已复制' : '复制失败，请检查浏览器权限')
}
const openCreator = (item) => {
  if (item?.user?.id && userStore.token) router.push(`/user?lookId=${item.user.id}`)
}

watch(activeVideo, async (item) => {
  beginPlayback(item)
  isPlaying.value = false
  if (!item) return
  if (!isVideo(item)) {
    playback.value.historyAdded = true
    apiAddHistory(getWorkId(item)).catch(() => { playback.value.historyAdded = false })
    return
  }
  await nextTick()
  const player = videoRefs.value[activeIndex.value]
  if (!player) return
  player.muted = muted.value
  player.play().catch(() => { isPlaying.value = false })
}, { flush: 'post' })
watch(muted, (value) => videoRefs.value.forEach((player) => { if (player) player.muted = value }))

onMounted(() => emit('index-change', activeIndex.value))
onUnmounted(() => {
  reportPlayback(activeVideo.value)
  videoRefs.value.forEach((player) => player?.pause())
})
</script>

<style scoped>
.recommendation-feed { position: relative; width: 100%; height: calc(100vh - 64px); min-height: 460px; overflow: hidden; color: #f7f8fa; background: #07090d; }
.feed-scroll { width: 100%; height: 100%; overflow-y: auto; overscroll-behavior-y: contain; scroll-snap-type: y mandatory; scrollbar-width: none; outline: none; }
.feed-scroll::-webkit-scrollbar { display: none; }
.feed-slide { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; min-height: 100%; padding: 20px 28px; scroll-snap-align: start; scroll-snap-stop: always; }
.feed-stage { display: flex; align-items: center; gap: 18px; max-width: 100%; max-height: 100%; }
.feed-video-card { position: relative; width: min(440px, calc((100vh - 116px) * .5625)); height: min(782px, calc(100vh - 116px)); aspect-ratio: 9 / 16; overflow: hidden; background: #000; border: 1px solid rgba(255, 255, 255, .1); border-radius: 17px; box-shadow: 0 28px 70px rgba(0, 0, 0, .42); }
.feed-video { display: block; width: 100%; height: 100%; object-fit: contain; background: #000; }
.video-shade { position: absolute; inset: 0; pointer-events: none; background: linear-gradient(180deg, rgba(0, 0, 0, .4), transparent 20%, transparent 48%, rgba(0, 0, 0, .72)); }
.feed-topline { position: absolute; top: 18px; right: 18px; left: 20px; display: flex; align-items: center; justify-content: space-between; color: rgba(255, 255, 255, .88); font-size: 12px; font-weight: 700; letter-spacing: .4px; pointer-events: none; }
.feed-topline > span:first-child { display: flex; align-items: center; gap: 8px; }
.feed-topline i { width: 7px; height: 7px; background: #ff2442; border-radius: 50%; box-shadow: 0 0 12px rgba(255, 36, 66, .65); }
.feed-position { padding: 6px 9px; color: rgba(255, 255, 255, .85); background: rgba(5, 8, 12, .45); border: 1px solid rgba(255, 255, 255, .14); border-radius: 7px; }
.feed-position small { color: rgba(255, 255, 255, .55); font-size: inherit; }
.utility-button { display: flex; align-items: center; gap: 7px; padding: 8px 11px; color: #f4f6f8; background: rgba(8, 12, 17, .62); border: 1px solid rgba(255, 255, 255, .15); border-radius: 8px; cursor: pointer; font-size: 12px; backdrop-filter: blur(12px); }
.utility-button:hover { background: rgba(30, 39, 50, .9); }
.utility-button i { font-size: 17px; }
.sound-button { position: absolute; top: 16px; right: 16px; z-index: 2; margin-top: 39px; }
.play-overlay { position: absolute; top: 50%; left: 50%; z-index: 2; display: grid; place-items: center; width: 76px; height: 76px; padding: 0; color: #fff; background: rgba(0, 0, 0, .4); border: 1px solid rgba(255, 255, 255, .34); border-radius: 18px; cursor: pointer; transform: translate(-50%, -50%); backdrop-filter: blur(10px); }
.play-overlay i { margin-left: 4px; font-size: 42px; }
.feed-copy { position: absolute; right: 20px; bottom: 24px; left: 22px; z-index: 1; max-width: calc(100% - 42px); }
.creator-name { display: inline-flex; margin-bottom: 8px; padding: 0; color: #fff; background: none; border: 0; cursor: pointer; font-size: 14px; font-weight: 750; text-shadow: 0 1px 8px rgba(0, 0, 0, .8); }
.feed-copy h1 { display: -webkit-box; margin: 0; overflow: hidden; color: #fff; font-size: 19px; font-weight: 760; line-height: 1.38; text-shadow: 0 1px 8px rgba(0, 0, 0, .75); -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.feed-copy p { display: -webkit-box; margin: 7px 0 0; overflow: hidden; color: rgba(255, 255, 255, .83); font-size: 13px; line-height: 1.5; text-shadow: 0 1px 8px rgba(0, 0, 0, .75); -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.feed-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 9px; }
.feed-tags span { padding: 4px 7px; color: rgba(255, 255, 255, .92); background: rgba(15, 20, 27, .64); border: 1px solid rgba(255, 255, 255, .16); border-radius: 5px; font-size: 11px; }
.video-progress { position: absolute; right: 0; bottom: 0; left: 0; z-index: 2; height: 3px; background: rgba(255, 255, 255, .24); }
.video-progress span { display: block; height: 100%; background: #ff2442; transition: width .12s linear; }
.feed-actions { display: flex; width: 82px; flex: 0 0 82px; flex-direction: column; align-items: stretch; gap: 10px; }
.creator-actions { position: relative; display: flex; width: 52px; height: 55px; flex-direction: column; align-items: center; margin-bottom: 3px; }
.avatar-button { display: grid; place-items: center; width: 52px; height: 52px; padding: 0; overflow: hidden; background: #111820; border: 2px solid rgba(255, 255, 255, .72); border-radius: 50%; cursor: pointer; }
.avatar-button :deep(.el-avatar) { display: block; }
.follow-button { position: absolute; right: -3px; bottom: 0; display: grid; place-items: center; width: 21px; height: 21px; padding: 0; color: #fff; background: #ff2442; border: 2px solid #10151c; border-radius: 50%; cursor: pointer; }
.follow-button:hover { background: #ff6076; }
.follow-button.following { color: #fff; background: #334150; }
.follow-button i { font-size: 16px; line-height: 1; }
.feed-action { display: flex; width: 100%; min-height: 50px; flex-direction: column; align-items: center; justify-content: center; gap: 4px; padding: 4px; color: #f4f6f8; background: transparent; border: 0; border-radius: 0; cursor: pointer; transition: color .15s ease, transform .15s ease; }
.feed-action:hover { color: #ff2442; transform: scale(1.06); }
.feed-action:disabled { cursor: wait; opacity: .7; }
.feed-action i { font-size: 25px; line-height: 1; }
.feed-action strong { color: #f4f6f8; font-size: 11px; font-weight: 700; }
.feed-action.like-action.active, .feed-action.like-action.active strong { color: #ff667b; }
.feed-state { display: flex; width: 100%; height: 100%; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 24px; color: #e9edf2; text-align: center; }
.feed-state > i { color: #ff2442; font-size: 36px; }
.feed-state strong { font-size: 18px; }
.feed-state span { color: #9eabba; font-size: 13px; }
.state-actions { display: flex; gap: 10px; margin-top: 8px; }
.loading-more { position: absolute; right: 24px; bottom: 16px; z-index: 3; padding: 8px 12px; color: #c6cfda; background: rgba(17, 24, 33, .92); border: 1px solid #344352; border-radius: 7px; font-size: 12px; }
.loading-more i { margin-right: 5px; }
.loading-more button { padding: 0; color: #ff667d; background: none; border: 0; cursor: pointer; font: inherit; }
.comments-drawer-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 19px 20px; border-bottom: 1px solid #eef0f4; }
.comments-drawer-heading div { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.comments-drawer-heading span { color: #8a94a6; font-size: 11px; }
.comments-drawer-heading strong { overflow: hidden; color: #222c3c; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
:global(.feed-comments-drawer.el-drawer) { width: min(440px, 100vw) !important; background: #fff; }
:global(.feed-comments-drawer .el-drawer__body) { padding: 0; overflow-y: auto; }
.feed-work-pager { position: absolute; inset: 0; }
.work-image .feed-work-pager :deep(.pager-dots) { bottom: 158px; }
.text-slide { background: linear-gradient(168deg, #f8f9fc 0%, #eef1f7 58%, #e9edf5 100%); }
.text-slide .feed-video-card { border-color: rgba(23, 36, 58, .08); box-shadow: 0 16px 40px rgba(23, 36, 58, .1); }
.work-text .feed-topline { color: #666; }
.work-text .feed-topline i { background: #ff2442; box-shadow: none; }
.work-text .feed-position { color: #777; background: rgba(255, 255, 255, .8); border-color: #eee; }
.work-text .feed-position small { color: #aaa; }
.text-work-stage .feed-action, .text-work-stage .feed-action strong { color: #4a5670; }
.text-work-stage .feed-action:hover { color: #ff2442; }
.text-work-stage .feed-action.like-action.active, .text-work-stage .feed-action.like-action.active strong { color: #ff2442; }
.text-work-stage .avatar-button { background: #fff; border-color: #fff; box-shadow: 0 2px 12px rgba(23, 36, 58, .16); }
.text-work-stage .follow-button { border-color: #fff; }
@media (max-width: 760px) {
  .recommendation-feed { height: calc(100vh - 60px); height: calc(100dvh - 60px); min-height: 0; }
  .feed-slide { padding: 0; }
  .feed-stage, .feed-video-card { position: relative; width: 100%; height: 100%; max-width: none; max-height: none; aspect-ratio: auto; }
  .feed-video-card { border: 0; border-radius: 0; box-shadow: none; }
  .feed-actions { position: absolute; right: 10px; bottom: 104px; width: 58px; flex-basis: auto; gap: 8px; }
  .creator-actions { width: 45px; height: 49px; }
  .avatar-button { width: 45px; height: 45px; }
  .follow-button { right: -4px; bottom: 0; width: 20px; height: 20px; }
  .feed-action { min-height: 46px; gap: 3px; padding: 4px 3px; }
  .feed-action i { font-size: 23px; }
  .feed-action strong { font-size: 10px; }
  .feed-copy { right: 76px; bottom: 24px; left: 16px; max-width: none; }
  .feed-copy h1 { font-size: 17px; }
  .feed-copy p { font-size: 12px; }
  .work-image .feed-work-pager :deep(.pager-dots) { bottom: 170px; }
  .text-work-stage .feed-action, .text-work-stage .feed-action strong { color: #444; }
  .text-work-stage .feed-action:hover { color: #ff2442; }
  .text-work-stage .feed-action.like-action.active, .text-work-stage .feed-action.like-action.active strong { color: #ff2442; }
  .text-work-stage .avatar-button { border-color: #fff; box-shadow: 0 2px 10px rgba(0, 0, 0, .12); }
  .text-work-stage .follow-button { border-color: #fff; }
  .text-work-stage .feed-work-pager :deep(.pager-arrow) { top: 38%; }
  .feed-topline { top: 13px; right: 14px; left: 16px; }
  .sound-button { top: 52px; right: 14px; margin-top: 0; }
  .play-overlay { width: 64px; height: 64px; }
  .loading-more { right: 12px; bottom: 12px; }
}
</style>
