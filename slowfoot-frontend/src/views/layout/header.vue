<template>
  <header class="app-header">
    <div class="header-left">
      <el-button text class="menu-button" aria-label="打开或关闭导航" @click="clickEvent(1)">
        <el-icon :size="21"><Menu /></el-icon>
      </el-button>
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark"><VideoCameraFilled /></span>
        <span class="brand-name">SlowFoot</span>
      </button>
    </div>

    <el-input class="desktop-search" placeholder="搜索视频、创作者或主题" :prefix-icon="Search" readonly @focus="openSearch" />

    <div class="header-right">
      <el-button class="search-trigger" text @click="openSearch">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
      <el-badge v-if="userStore.token && unreadCount" :value="unreadCount" type="danger">
        <el-button text circle aria-label="通知" @click="router.push('/notifications')">
          <el-icon :size="20"><Bell /></el-icon>
        </el-button>
      </el-badge>
      <el-button v-else-if="userStore.token" text circle aria-label="通知" @click="router.push('/notifications')">
        <el-icon :size="20"><Bell /></el-icon>
      </el-button>
      <auth />
    </div>

    <el-dialog v-model="showSearch" width="520px" class="search-dialog" title="搜索">
      <el-input ref="searchInputRef" v-model="searchKey" autofocus clearable placeholder="输入关键词开始探索" :prefix-icon="Search" @keyup.enter="search" @clear="search" />
      <div v-if="serarchHistory.length" class="history-block">
        <div class="history-heading">
          <span>最近搜索</span>
          <el-button text circle aria-label="清除搜索历史" @click="clearSearchHistory">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div class="history-tags">
          <el-tag v-for="item in serarchHistory" :key="item" effect="plain" @click="searchKey = item; search()">
            {{ item }}
          </el-tag>
        </div>
      </div>
      <hot-list compact />
    </el-dialog>
  </header>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { Bell, Delete, Menu, Search, VideoCameraFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { apiClearUserSearchHistory, apiGetUserSearchHistory } from '../../apis/user/user'
import { apiGetUnreadNotificationCount } from '../../apis/notification'
import hotList from '../../components/hotList.vue'
import auth from '../auth/index.vue'
import { useUserStore } from '../../stores'

defineProps({ clickEvent: { type: Function, default: () => {} } })

const router = useRouter()
const userStore = useUserStore()
const searchInputRef = ref()
const showSearch = ref(false)
const searchKey = ref('')
const serarchHistory = ref([])
const unreadCount = ref(0)

const openSearch = () => { showSearch.value = true }
const loadSearchHistory = () => {
  if (!userStore.token) {
    serarchHistory.value = []
    return
  }
  apiGetUserSearchHistory().then(({ data }) => {
    if (data.success) serarchHistory.value = data.data || []
  })
}
const clearSearchHistory = () => {
  apiClearUserSearchHistory().then(({ data }) => {
    if (data.success) serarchHistory.value = []
  })
}
const loadUnreadCount = () => {
  if (!userStore.token) {
    unreadCount.value = 0
    return
  }
  apiGetUnreadNotificationCount().then(({ data }) => {
    if (data.success) unreadCount.value = data.data || 0
  })
}
const search = () => {
  showSearch.value = false
  router.push(searchKey.value ? `/video/search/${encodeURIComponent(searchKey.value)}` : '/')
  loadSearchHistory()
  searchKey.value = ''
}

onMounted(() => {
  loadSearchHistory()
  loadUnreadCount()
})
watch(() => userStore.token, () => {
  loadSearchHistory()
  loadUnreadCount()
})
</script>

<style scoped>
.app-header { position: sticky; top: 0; z-index: 10; display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; width: 100%; height: 72px; padding: 0 30px; background: rgba(16, 19, 24, .94); border-bottom: 1px solid #272d37; backdrop-filter: blur(18px); }
.header-left, .header-right { display: flex; align-items: center; }
.header-left { gap: 14px; }
.header-right { gap: 12px; }
.menu-button { width: 38px; height: 38px; color: #9ca6b5; }
.menu-button:hover { color: #f4f7fb; background: #1e2530; }
.brand { display: flex; align-items: center; gap: 10px; padding: 0; color: #f4f7fb; background: transparent; border: 0; cursor: pointer; }
.brand-mark { display: grid; place-items: center; width: 31px; height: 31px; color: #101318; background: #f2bd5c; border-radius: 9px; }
.brand-name { font-size: 19px; font-weight: 750; letter-spacing: 0; }
.desktop-search { width: min(480px, 42vw); margin-left: 8vw; }
.search-trigger { color: #aeb8c7; }
.search-trigger:hover { color: #f4f7fb; }
.history-block { margin-top: 24px; }
.history-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; color: #aeb8c7; font-size: 13px; font-weight: 650; }
.history-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.history-tags .el-tag { cursor: pointer; }
@media (max-width: 760px) {
  .app-header { height: 64px; padding: 0 14px; }
  .desktop-search, .search-trigger { display: none; }
  .app-header :deep(.el-button) { padding: 8px; }
  .brand-name { font-size: 17px; }
}
</style>
