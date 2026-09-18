<template>
  <div class="home-page">
    <section class="welcome-panel">
      <div class="welcome-copy">
        <div class="eyebrow"><span class="eyebrow-line" />今日精选</div>
        <h1>{{ route.meta.isSearch ? `搜索「${route.params.key}」` : '今天，想看点什么？' }}</h1>
        <p>{{ route.meta.isSearch ? '找到你感兴趣的内容，慢慢看。' : '从喜欢的频道开始，发现下一段值得收藏的灵感。' }}</p>
        <div class="welcome-actions">
          <el-button type="primary" size="large" @click="router.push('/pushVideo')">
            <el-icon><Compass /></el-icon>浏览推荐
          </el-button>
          <el-button plain size="large" @click="openSearch">
            <el-icon><Search /></el-icon>搜索内容
          </el-button>
        </div>
      </div>
      <div class="welcome-mark" aria-hidden="true">
        <span class="mark-ring mark-ring-one" />
        <span class="mark-ring mark-ring-two" />
        <span class="mark-play"><VideoCameraFilled /></span>
      </div>
    </section>

    <div class="content-grid">
      <main class="feed-column">
        <div class="section-heading">
          <div>
            <span class="section-kicker">EXPLORE</span>
            <h2>{{ route.meta.isSearch ? '搜索结果' : '热门视频' }}</h2>
          </div>
          <div class="view-meta"><span class="status-dot" />实时更新</div>
        </div>
        <div class="feed-tabs">
          <button class="feed-tab is-active" type="button">{{ currentLabel }}</button>
          <button class="feed-tab" type="button" @click="router.push('/pushVideo')">为你推荐</button>
          <button v-if="userStore.token" class="feed-tab" type="button" @click="router.push('/followVideo')">关注动态</button>
        </div>
        <VideoListVue :video-list="videoList" :loading="isLoading" :no-data-msg="route.meta.isSearch ? '没有找到相关视频' : '暂时还没有视频，去推荐频道看看吧'" />
      </main>

      <aside class="side-column">
        <hot-list />
        <section class="side-note">
          <span class="note-icon"><Star /></span>
          <div>
            <strong>记录你的灵感</strong>
            <p>登录后可以收藏、点赞和关注创作者。</p>
          </div>
          <el-button v-if="!userStore.token" text type="primary" @click="openLogin">登录</el-button>
        </section>
      </aside>
    </div>

    <el-dialog :model-value="dialog" fullscreen :show-close="false" :close-on-press-escape="false" :close-on-click-modal="false">
      <el-card v-if="dialog" shadow="never"><Video :video-info="searchVideoInfo" :close-video="closeVideo" /></el-card>
    </el-dialog>
    <auth ref="authRef" :show-trigger="false" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Compass, Search, Star, VideoCameraFilled } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { apiGetClassifyByUser } from '../../apis/classify'
import { apiGetVideoById, apiSearchVideo, apiVideoByClassfiy } from '../../apis/video'
import { useUserStore } from '../../stores'
import Video from '../../components/video/index.vue'
import VideoListVue from '../../components/video/list.vue'
import hotList from '../../components/hotList.vue'
import auth from '../auth/index.vue'
import router from '../../router/index.js'

const userStore = useUserStore()
const route = useRoute()
const authRef = ref()
const userClassifys = ref([])
const isLoading = ref(true)
const videoList = ref([])
const currentClassify = ref(0)
const searchVideoInfo = ref(null)
const pageInfo = ref({ page: 1, limit: 15 })
const dialog = computed(() => Boolean(searchVideoInfo.value))
const currentLabel = computed(() => {
  if (route.meta.isSearch) return '搜索结果'
  if (route.meta.isClassify) return '频道内容'
  return '全部'
})

const getCurrentClassifyVideo = () => {
  if (route.meta.isSearch || pageInfo.value.page > 1) return
  isLoading.value = true
  apiVideoByClassfiy(currentClassify.value, pageInfo.value.page, pageInfo.value.limit).then(({ data }) => {
    if (!data.success) return
    const items = Array.isArray(data.data) ? data.data : (data.data?.records || [])
    videoList.value = videoList.value.concat(items)
  }).catch(() => { videoList.value = [] }).finally(() => { isLoading.value = false })
}

const getSearchVideo = () => {
  isLoading.value = true
  apiSearchVideo(route.params.key, pageInfo.value.page, pageInfo.value.limit).then(({ data }) => {
    if (data.success) videoList.value = videoList.value.concat(data.data?.records || [])
  }).catch(() => { videoList.value = [] }).finally(() => { isLoading.value = false })
}

const initView = () => {
  pageInfo.value.page = 1
  videoList.value = []
  if (route.meta.isSearch) {
    currentClassify.value = -1
    getSearchVideo()
  } else {
    currentClassify.value = route.meta.isClassify ? route.params.classify : 0
    getCurrentClassifyVideo()
    if (!route.meta.isClassify) apiGetClassifyByUser().then(({ data }) => { if (data.success) userClassifys.value = data.data || [] })
  }
  if (route.query.play) {
    apiGetVideoById(route.query.play).then(({ data }) => { if (data.success) searchVideoInfo.value = data.data })
  } else searchVideoInfo.value = null
}

const closeVideo = () => {
  router.back()
  searchVideoInfo.value = null
}
const openSearch = () => document.querySelector('.desktop-search')?.focus()
const openLogin = () => authRef.value?.open?.()
const listenScroll = () => {
  if (!route.meta.isSearch || isLoading.value) return
  const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
  const windowHeight = document.documentElement.clientHeight || document.body.clientHeight
  const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
  if (scrollHeight - scrollTop - windowHeight < 150) {
    isLoading.value = true
    pageInfo.value.page++
    getSearchVideo()
  }
}

watch(() => route.fullPath, initView)
onMounted(() => {
  initView()
  window.addEventListener('scroll', listenScroll)
})
onUnmounted(() => window.removeEventListener('scroll', listenScroll))
</script>

<style scoped>
.home-page { width: min(1440px, 100%); margin: 0 auto; padding: 34px 42px 64px; box-sizing: border-box; }
.welcome-panel { position: relative; display: flex; justify-content: space-between; min-height: 250px; padding: 34px 42px; overflow: hidden; background: #1b222c; border: 1px solid #293442; border-radius: 14px; box-sizing: border-box; }
.welcome-panel::after { position: absolute; right: -80px; bottom: -140px; width: 410px; height: 250px; content: ''; background: #273847; border-radius: 50%; transform: rotate(-18deg); opacity: .7; }
.welcome-copy { position: relative; z-index: 1; }
.eyebrow { display: flex; align-items: center; gap: 9px; color: #f2bd5c; font-size: 11px; font-weight: 800; letter-spacing: 1.8px; text-transform: uppercase; }
.eyebrow-line { width: 22px; height: 2px; background: #f2bd5c; }
.welcome-panel h1 { max-width: 600px; margin: 18px 0 10px; color: #f5f7fa; font-size: clamp(28px, 3.2vw, 46px); font-weight: 760; letter-spacing: 0; line-height: 1.12; }
.welcome-panel p { margin: 0; color: #95a2b2; font-size: 14px; }
.welcome-actions { display: flex; gap: 12px; margin-top: 27px; }
.welcome-actions .el-button { height: 42px; padding: 0 18px; border-radius: 7px; }
.welcome-actions .el-button + .el-button { margin-left: 0; }
.welcome-actions :deep(.el-icon) { margin-right: 7px; }
.welcome-mark { position: relative; z-index: 1; width: 205px; margin: 4px 30px 0 0; }
.mark-ring { position: absolute; display: block; border: 1px solid #4c6d7b; border-radius: 50%; transform: rotate(-24deg); }
.mark-ring-one { top: 0; left: 8px; width: 168px; height: 168px; }
.mark-ring-two { top: 24px; left: 33px; width: 118px; height: 118px; border-color: #f2bd5c; }
.mark-play { position: absolute; top: 64px; left: 86px; display: grid; place-items: center; width: 52px; height: 52px; color: #151a21; background: #f2bd5c; border-radius: 50%; box-shadow: 0 10px 30px rgba(242, 189, 92, .2); }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 288px; gap: 34px; margin-top: 42px; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; }
.section-kicker { color: #677487; font-size: 10px; font-weight: 800; letter-spacing: 1.6px; }
.section-heading h2 { margin: 6px 0 0; color: #f0f3f7; font-size: 25px; font-weight: 720; }
.view-meta { display: flex; align-items: center; gap: 7px; padding-bottom: 3px; color: #748093; font-size: 11px; }
.status-dot { width: 6px; height: 6px; background: #66d19e; border-radius: 50%; }
.feed-tabs { display: flex; gap: 22px; margin: 26px 0 18px; border-bottom: 1px solid #252d37; }
.feed-tab { position: relative; padding: 0 0 12px; color: #788597; background: transparent; border: 0; cursor: pointer; font: inherit; font-size: 13px; }
.feed-tab:hover, .feed-tab.is-active { color: #f2bd5c; }
.feed-tab.is-active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; content: ''; background: #f2bd5c; }
.side-column { display: flex; flex-direction: column; gap: 18px; }
.side-note { display: flex; align-items: flex-start; gap: 11px; padding: 17px; background: #171d25; border: 1px solid #252e3a; border-radius: 10px; }
.note-icon { display: grid; flex: 0 0 28px; place-items: center; width: 28px; height: 28px; color: #151a21; background: #f2bd5c; border-radius: 7px; }
.side-note strong { display: block; padding-top: 2px; color: #e8edf3; font-size: 12px; }
.side-note p { margin: 5px 0 0; color: #7d8999; font-size: 11px; line-height: 1.55; }
.side-note .el-button { flex: 0 0 auto; margin: 0 0 0 auto; padding: 3px; }
@media (max-width: 980px) { .home-page { padding: 26px 24px 48px; } .content-grid { grid-template-columns: 1fr; } .side-column { display: grid; grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .home-page { padding: 18px 14px 36px; } .welcome-panel { min-height: 260px; padding: 26px 22px; } .welcome-mark { position: absolute; right: -40px; bottom: -20px; opacity: .5; transform: scale(.8); } .welcome-panel h1 { font-size: 30px; } .welcome-actions .el-button { padding: 0 12px; } .side-column { display: flex; } .view-meta { display: none; } }
</style>
