<template>
  <main v-if="route.query.play" class="focused-work-page">
    <div v-if="selectedLoading" class="focused-state">
      <i class="mdi mdi-loading mdi-spin" />
      <span>正在加载作品</span>
    </div>
    <div v-else-if="selectedError" class="focused-state focused-error">
      <strong>作品暂时无法打开</strong>
      <span>{{ selectedError }}</span>
      <button type="button" @click="closeVideo">返回</button>
    </div>
    <WorkViewer v-else-if="selectedVideo && isReaderWork" class="focused-reader" :work="selectedVideo" @close="closeVideo" />
    <section v-else-if="selectedVideo" class="focused-video-view">
      <header class="focused-video-header">
        <button type="button" aria-label="返回" @click="closeVideo"><i class="mdi mdi-arrow-left" /></button>
        <span>视频作品</span>
      </header>
      <div class="focused-video-content">
        <video controls playsinline preload="metadata" :poster="selectedVideo.coverFileId ? apiFileGet(selectedVideo.coverFileId) : undefined">
          <source :src="selectedVideo.primaryFileId ? apiFileGet(selectedVideo.primaryFileId) : ''" :type="selectedVideo.format || 'video/mp4'" />
        </video>
        <h1>{{ selectedVideo.title || '未命名作品' }}</h1>
        <p v-if="selectedVideo.description">{{ selectedVideo.description }}</p>
      </div>
    </section>
  </main>

  <div v-else class="home-page">
    <section class="discover-heading">
      <div>
        <span class="eyebrow"><i /> DISCOVER SOMETHING NEW</span>
        <h1>{{ route.meta.isSearch ? `搜索「${route.params.key}」` : '发现值得收藏的新鲜事' }}</h1>
        <p>{{ route.meta.isSearch ? '找到同好，也找到更多灵感。' : '视频、图片和文字，每一种分享都值得被看见。' }}</p>
      </div>
      <el-button class="recommend-button" type="primary" round size="large" @click="router.push('/pushVideo')">
        <el-icon><Compass /></el-icon>为你推荐
      </el-button>
    </section>

    <div class="content-grid">
      <main class="feed-column">
        <div class="section-heading">
          <h2>{{ pageTitle }}</h2>
          <span class="live-label"><i />持续更新</span>
        </div>
        <div class="feed-tabs">
          <button class="feed-tab active" type="button">{{ currentLabel }}</button>
          <button class="feed-tab" type="button" @click="router.push('/pushVideo')">推荐</button>
          <button v-if="userStore.token" class="feed-tab" type="button" @click="router.push('/followVideo')">关注</button>
          <button class="feed-tab search-tab" type="button" aria-label="搜索" @click="openSearch"><el-icon><Search /></el-icon></button>
        </div>
        <VideoListVue :video-list="videoList" :loading="isLoading" :no-data-msg="emptyMessage" />
      </main>

      <aside class="side-column">
        <hot-list />
        <section v-if="!userStore.token" class="login-card">
          <span class="login-icon"><Star /></span>
          <div><strong>记录你的灵感</strong><p>登录后可以收藏、点赞和关注创作者。</p></div>
          <el-button text type="primary" @click="openLogin">登录</el-button>
        </section>
        <section v-else class="login-card signed-card">
          <span class="login-icon"><CircleCheck /></span>
          <div><strong>欢迎回来</strong><p>去关注动态看看创作者的新作品。</p></div>
        </section>
      </aside>
    </div>

    <auth ref="authRef" :show-trigger="false" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { CircleCheck, Compass, Search, Star } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import router from '../../router'
import { apiFileGet } from '../../apis/file'
import { apiGetVideoById, apiSearchVideo, apiVideoByClassfiy } from '../../apis/video'
import { useUserStore } from '../../stores'
import WorkViewer from '../../components/video/WorkViewer.vue'
import VideoListVue from '../../components/video/list.vue'
import hotList from '../../components/hotList.vue'
import auth from '../auth/index.vue'

const route = useRoute()
const userStore = useUserStore()
const authRef = ref(null)
const videoList = ref([])
const isLoading = ref(true)
const selectedVideo = ref(null)
const selectedLoading = ref(false)
const selectedError = ref('')
const pageInfo = ref({ page: 1, limit: 15 })
let selectedRequestSequence = 0
const isReaderWork = computed(() => ['IMAGE', 'TEXT'].includes(selectedVideo.value?.workType))
const isSearch = computed(() => Boolean(route.meta.isSearch))
const isCategory = computed(() => Boolean(route.meta.isClassify))
const pageTitle = computed(() => isSearch.value ? '搜索结果' : isCategory.value ? '频道内容' : '热门作品')
const currentLabel = computed(() => isSearch.value ? '搜索结果' : isCategory.value ? '频道内容' : '精选')
const emptyMessage = computed(() => isSearch.value ? '没有找到相关作品' : isCategory.value ? '这个频道暂时还没有作品' : '暂时还没有热门作品')

const loadVideos = async () => {
  isLoading.value = true
  try {
    let response
    if (isSearch.value) response = await apiSearchVideo(route.params.key, pageInfo.value.page, pageInfo.value.limit)
    else response = await apiVideoByClassfiy(isCategory.value ? Number(route.params.classify) : 0, pageInfo.value.page, pageInfo.value.limit)
    const data = response.data
    if (data.success) {
      const items = Array.isArray(data.data) ? data.data : data.data?.records || []
      videoList.value = pageInfo.value.page === 1 ? items : videoList.value.concat(items)
    } else if (pageInfo.value.page === 1) videoList.value = []
  } catch { if (pageInfo.value.page === 1) videoList.value = [] }
  finally { isLoading.value = false }
}
const loadSelectedVideo = async () => {
  const sequence = ++selectedRequestSequence
  const workId = route.query.play
  selectedVideo.value = null
  selectedError.value = ''
  if (!workId) { selectedLoading.value = false; return }

  selectedLoading.value = true
  try {
    const { data } = await apiGetVideoById(workId)
    if (sequence !== selectedRequestSequence) return
    if (!data?.success || !data.data) throw new Error(data?.message || '请稍后重试。')
    selectedVideo.value = data.data
  } catch (error) {
    if (sequence === selectedRequestSequence) selectedError.value = error?.response?.data?.message || error?.message || '请稍后重试。'
  } finally {
    if (sequence === selectedRequestSequence) selectedLoading.value = false
  }
}
const initView = () => {
  pageInfo.value = { page: 1, limit: 15 }
  videoList.value = []
  if (route.query.play) {
    isLoading.value = false
    loadSelectedVideo()
    return
  }
  loadVideos()
  loadSelectedVideo()
}
const openSearch = () => window.dispatchEvent(new Event('slowfoot:open-search'))
const openLogin = () => authRef.value?.open?.()
const closeVideo = () => {
  selectedVideo.value = null
  if (route.query.play) router.back()
}
const listenScroll = () => {
  if (!isSearch.value || isLoading.value || videoList.value.length === 0) return
  const nearBottom = document.documentElement.scrollHeight - window.scrollY - window.innerHeight < 180
  if (nearBottom) { pageInfo.value.page += 1; loadVideos() }
}

watch(() => route.fullPath, initView)
onMounted(() => {
  initView()
  window.addEventListener('scroll', listenScroll)
  if (route.query.login === 'required') setTimeout(openLogin, 0)
})
onUnmounted(() => window.removeEventListener('scroll', listenScroll))
</script>

<style scoped>
.focused-work-page { width: 100%; height: 100%; min-height: 100dvh; color: #333; background: #fff; }
.focused-reader { width: 100%; height: 100%; min-height: 100dvh; }
.focused-state { display: flex; min-height: 100dvh; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: #888; font-size: 14px; }
.focused-state > i { color: #ff2442; font-size: 30px; }
.focused-error strong { color: #333; font-size: 18px; }
.focused-error button { margin-top: 8px; padding: 9px 20px; color: #fff; background: #ff2442; border: 0; border-radius: 999px; cursor: pointer; }
.focused-video-view { display: flex; width: 100%; height: 100%; min-height: 100dvh; flex-direction: column; color: #fff; background: #111; }
.focused-video-header { display: flex; height: 58px; flex: 0 0 58px; align-items: center; gap: 14px; padding: 0 24px; color: #ccc; border-bottom: 1px solid #292929; box-sizing: border-box; font-size: 13px; }
.focused-video-header button { display: grid; width: 34px; height: 34px; place-items: center; color: #fff; background: #262626; border: 0; border-radius: 50%; cursor: pointer; font-size: 17px; }
.focused-video-content { width: min(1120px, calc(100% - 40px)); margin: auto; padding: 28px 0 48px; }
.focused-video-content video { display: block; width: 100%; max-height: calc(100dvh - 230px); aspect-ratio: 16 / 9; background: #000; border-radius: 12px; object-fit: contain; }
.focused-video-content h1 { margin: 20px 0 8px; font-size: clamp(20px, 3vw, 28px); }
.focused-video-content p { margin: 0; color: #aaa; font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
.home-page { width: min(1500px, 100%); margin: 0 auto; padding: 30px 32px 64px; }
.discover-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 5px 0 29px; }
.eyebrow { display: flex; align-items: center; gap: 8px; color: #999; font-size: 10px; font-weight: 700; letter-spacing: 1.7px; }
.eyebrow i { width: 7px; height: 7px; background: #ff2442; border-radius: 50%; }
.discover-heading h1 { margin: 12px 0 8px; color: #222; font-size: clamp(26px, 3vw, 38px); font-weight: 750; letter-spacing: -.8px; line-height: 1.25; }
.discover-heading p { margin: 0; color: #888; font-size: 13px; }
.recommend-button { flex: 0 0 auto; height: 42px; padding: 0 21px; border: 0; font-weight: 650; }
.recommend-button :deep(.el-icon) { margin-right: 7px; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 276px; gap: 30px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; }
.section-heading h2 { margin: 0; color: #333; font-size: 19px; font-weight: 700; }
.live-label { display: flex; align-items: center; gap: 6px; color: #aaa; font-size: 11px; }
.live-label i { width: 6px; height: 6px; background: #35bd7b; border-radius: 50%; }
.feed-tabs { display: flex; align-items: center; gap: 26px; margin: 18px 0 21px; border-bottom: 1px solid #ececec; }
.feed-tab { position: relative; padding: 0 1px 12px; color: #999; background: transparent; border: 0; cursor: pointer; font: inherit; font-size: 13px; }
.feed-tab:hover, .feed-tab.active { color: #333; }
.feed-tab.active { font-weight: 650; }
.feed-tab.active::after { position: absolute; bottom: -1px; left: 50%; width: 20px; height: 3px; content: ''; background: #ff2442; border-radius: 3px; transform: translateX(-50%); }
.search-tab { margin-left: auto; padding-right: 2px; font-size: 16px; }
.side-column { display: flex; flex-direction: column; gap: 15px; padding-top: 1px; }
.login-card { display: flex; align-items: flex-start; gap: 11px; padding: 17px; background: #fff; border: 1px solid #f0f0f0; border-radius: 13px; }
.login-icon { display: grid; flex: 0 0 32px; place-items: center; width: 32px; height: 32px; color: #ff2442; background: #fff0f2; border-radius: 10px; }
.login-card strong { display: block; padding-top: 2px; color: #333; font-size: 13px; }
.login-card p { margin: 5px 0 0; color: #999; font-size: 11px; line-height: 1.5; }
.login-card .el-button { margin-left: auto; padding: 3px; }
.signed-card .login-icon { color: #26965e; background: #ebf8f1; }
@media (max-width: 1080px) { .home-page { padding: 25px 23px 52px; } .content-grid { grid-template-columns: minmax(0, 1fr) 240px; gap: 22px; } }
@media (max-width: 900px) { .content-grid { grid-template-columns: 1fr; } .side-column { display: grid; grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .home-page { padding: 19px 12px 42px; } .discover-heading { align-items: flex-start; padding: 3px 3px 22px; } .discover-heading h1 { margin-top: 10px; font-size: 25px; } .discover-heading p { max-width: 250px; font-size: 12px; line-height: 1.6; } .recommend-button { height: 36px; padding: 0 13px; font-size: 12px; } .content-grid { gap: 25px; } .section-heading { padding: 0 3px; } .feed-tabs { margin: 15px 3px 16px; } .side-column { display: flex; } .focused-video-header { padding: 0 14px; } .focused-video-content { width: calc(100% - 24px); padding: 18px 0 30px; } .focused-video-content video { max-height: 62dvh; border-radius: 8px; } }
</style>
