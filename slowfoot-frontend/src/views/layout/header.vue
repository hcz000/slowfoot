<template>
  <header class="app-header">
    <div class="header-left">
      <el-button text class="menu-button" aria-label="切换导航" @click="clickEvent">
        <el-icon :size="21"><Menu /></el-icon>
      </el-button>
      <button class="brand" type="button" aria-label="返回首页" @click="router.push('/')">
        <span class="brand-mark">S</span>
        <span class="brand-name">SlowFoot</span>
      </button>
    </div>

    <el-input class="desktop-search" readonly placeholder="搜索作品、创作者或主题" :prefix-icon="Search" @click="openSearch" />

    <div class="header-right">
      <el-button text class="search-trigger" @click="openSearch"><el-icon><Search /></el-icon>搜索</el-button>
      <el-button v-if="userStore.token" class="publish-button" type="primary" round @click="router.push('/user/publish')"><el-icon><Plus /></el-icon><span>发布</span></el-button>
      <el-button v-if="userStore.token" class="notification-button" text circle aria-label="通知" @click="router.push('/notifications')">
        <el-icon :size="20"><Bell /></el-icon>
        <span v-if="unreadCount" class="notification-dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
      </el-button>
      <auth />
    </div>

    <el-dialog v-model="showSearch" class="search-dialog" width="min(92vw, 520px)" title="搜索内容" align-center append-to-body destroy-on-close>
      <el-input ref="searchInputRef" v-model="searchKey" autofocus clearable placeholder="输入关键词开始探索" :prefix-icon="Search" @keyup.enter="search" @clear="search" />
      <div v-if="searchHistory.length" class="history-block">
        <div class="history-heading"><span>最近搜索</span><el-button text @click="clearSearchHistory">清除</el-button></div>
        <div class="history-tags">
          <el-tag v-for="item in searchHistory" :key="item" effect="plain" @click="searchKey = item; search()">{{ item }}</el-tag>
        </div>
      </div>
      <hot-list compact />
    </el-dialog>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Bell, Menu, Plus, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { apiClearUserSearchHistory, apiGetUserSearchHistory } from '../../apis/user/user'
import { apiGetUnreadNotificationCount } from '../../apis/notification'
import { connectNotificationEvents } from '../../apis/notificationEvents'
import hotList from '../../components/hotList.vue'
import auth from '../auth/index.vue'
import { useNotificationStore, useUserStore } from '../../stores'

defineProps({ clickEvent: { type: Function, default: () => {} } })
const router = useRouter()
const userStore = useUserStore()
const searchInputRef = ref(null)
const showSearch = ref(false)
const searchKey = ref('')
const searchHistory = ref([])
const notificationStore = useNotificationStore()
const unreadCount = computed(() => notificationStore.unreadCount)
let unreadCountRequest = 0

const openSearch = () => { showSearch.value = true }
const handleOpenSearch = () => openSearch()
const loadSearchHistory = async () => {
  if (!userStore.token) { searchHistory.value = []; return }
  try { const { data } = await apiGetUserSearchHistory(); if (data.success) searchHistory.value = data.data || [] } catch { searchHistory.value = [] }
}
const clearSearchHistory = async () => {
  try { const { data } = await apiClearUserSearchHistory(); if (data.success) searchHistory.value = [] } catch { /* token may have expired */ }
}
const loadUnreadCount = async () => {
  if (!userStore.token) { notificationStore.setUnreadCount(0); return }
  const requestId = ++unreadCountRequest
  try {
    const { data } = await apiGetUnreadNotificationCount()
    if (requestId === unreadCountRequest) notificationStore.setUnreadCount(data.success ? data.data : 0)
  } catch { /* keep the last known count during a temporary network failure */ }
}
const search = () => {
  const keyword = searchKey.value.trim()
  showSearch.value = false
  router.push(keyword ? `/video/search/${encodeURIComponent(keyword)}` : '/')
  searchKey.value = ''
}

watch(() => userStore.token, (token, _previousToken, onCleanup) => {
  loadSearchHistory()

  if (!token) {
    unreadCountRequest += 1
    notificationStore.reset()
    return
  }

  loadUnreadCount()
  let hasConnected = false
  const stream = connectNotificationEvents({
    token,
    onEvent: (event) => {
      unreadCountRequest += 1
      notificationStore.receiveEvent(event)
    },
    onConnected: () => {
      if (hasConnected) notificationStore.markReconnected()
      hasConnected = true
      loadUnreadCount()
    },
    onUnauthorized: () => {
      sessionStorage.removeItem('token')
      userStore.$patch({ token: null, info: {} })
    }
  })

  onCleanup(stream.stop)
}, { immediate: true })

onMounted(() => window.addEventListener('slowfoot:open-search', handleOpenSearch))
onUnmounted(() => window.removeEventListener('slowfoot:open-search', handleOpenSearch))
</script>

<style scoped>
.app-header { position: sticky; top: 0; z-index: 100; display: flex; align-items: center; justify-content: space-between; height: 64px; padding: 0 25px; background: rgba(255, 255, 255, .94); border-bottom: 1px solid #ededed; backdrop-filter: blur(18px); }
.header-left, .header-right { display: flex; align-items: center; }
.header-left { gap: 12px; }
.header-right { gap: 9px; }
.menu-button { width: 36px; height: 36px; color: #777; }
.menu-button:hover { color: #ff2442; background: #fff0f2; }
.brand { display: flex; align-items: center; gap: 9px; padding: 0; color: #222; background: transparent; border: 0; cursor: pointer; }
.brand-mark { display: grid; width: 30px; height: 30px; place-items: center; color: #fff; background: #ff2442; border-radius: 9px 9px 9px 3px; font-size: 17px; font-weight: 850; font-style: italic; }
.brand-name { font-size: 20px; font-weight: 800; letter-spacing: -.6px; }
.desktop-search { width: min(430px, 39vw); margin: 0 6vw; cursor: pointer; }
.desktop-search :deep(.el-input__wrapper) { height: 38px; background: #f5f5f5; border-radius: 999px; box-shadow: none; }
.desktop-search :deep(.el-input__wrapper:hover) { background: #f0f0f0; box-shadow: none; }
.search-trigger { display: none; color: #666; }
.search-trigger:hover { color: #ff2442; }
.publish-button { height: 34px; padding: 0 16px; border: 0; }
.header-right :deep(.el-button.is-circle) { position: relative; }
.notification-button { color: #666; }
.notification-dot { position: absolute; top: -3px; right: -5px; min-width: 16px; padding: 0 4px; color: #fff; background: #ff2442; border: 2px solid #fff; border-radius: 10px; box-sizing: content-box; font-size: 9px; line-height: 14px; }
.history-block { margin-top: 22px; }
.history-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; color: #777; font-size: 13px; }
.history-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.history-tags .el-tag { cursor: pointer; }
@media (max-width: 760px) { .app-header { height: 60px; padding: 0 12px; } .desktop-search { display: none; } .search-trigger { display: inline-flex; } .brand-mark { width: 27px; height: 27px; font-size: 15px; } .brand-name { font-size: 17px; } .menu-button { width: 30px; margin-right: -4px; } .publish-button { width: 33px; height: 33px; padding: 0; } .publish-button span { display: none; } }
</style>
