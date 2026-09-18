<template>
  <aside v-show="open" class="app-navigation">
    <div class="navigation-user">
      <el-avatar :size="42" :src="userStore.token && userStore.info.avatar ? apiFileGet(userStore.info.avatar) : '/logo.png'" />
      <div class="navigation-user-info">
        <strong>{{ userStore.token ? userStore.info.nickName : '未登录' }}</strong>
        <span>{{ userStore.token ? (userStore.info.description || '欢迎回来') : '登录后解锁更多功能' }}</span>
      </div>
      <span class="online-dot" />
    </div>

    <div class="nav-label">发现</div>
    <el-menu class="navigation-menu" :default-active="route.path">
      <el-menu-item index="/" @click="navigate('/')">
        <el-icon><House /></el-icon><span>热门视频</span>
      </el-menu-item>
      <el-menu-item index="/pushVideo" @click="navigate('/pushVideo')">
        <el-icon><Compass /></el-icon><span>推荐视频</span>
      </el-menu-item>
      <el-menu-item v-if="userStore.token" index="/followVideo" @click="navigate('/followVideo')">
        <el-icon><Star /></el-icon><span>关注动态</span>
      </el-menu-item>
    </el-menu>

    <div class="nav-label category-label">频道</div>
    <el-menu class="navigation-menu" :default-active="route.path">
      <el-menu-item v-for="item in allClassifyList" :key="item.id" :index="`/video/${item.id}`" @click="navigate(`/video/${item.id}`)">
        <el-icon><Folder /></el-icon><span>{{ item.name }}</span>
      </el-menu-item>
    </el-menu>

    <div class="navigation-footer">
      <div class="footer-line"><span class="footer-signal" />内容持续更新</div>
      <el-button v-if="userStore.token" text class="footer-button" @click="navigate('/user')">
        <el-icon><User /></el-icon>个人空间
      </el-button>
    </div>
  </aside>
</template>

<script setup>
import { ref } from 'vue'
import { Compass, Folder, Star, House, User } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { apiClassifyGetAll } from '../../apis/classify'
import { apiFileGet } from '../../apis/file'
import { useUserStore } from '../../stores'

defineProps({ open: { type: Boolean, default: true } })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const allClassifyList = ref([])
const navigate = (path) => router.push(path)

apiClassifyGetAll().then(({ data }) => {
  if (data.success) allClassifyList.value = data.data || []
})
</script>

<style scoped>
.app-navigation { position: sticky; top: 72px; display: flex; flex: 0 0 244px; flex-direction: column; width: 244px; min-height: calc(100vh - 72px); padding: 22px 14px; overflow-y: auto; background: #14181f; border-right: 1px solid #272d37; box-sizing: border-box; }
.navigation-user { position: relative; display: flex; align-items: center; gap: 11px; min-height: 62px; padding: 10px; background: #1a2029; border: 1px solid #252d38; border-radius: 10px; }
.navigation-user-info { min-width: 0; display: flex; flex: 1; flex-direction: column; gap: 4px; }
.navigation-user-info strong, .navigation-user-info span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.navigation-user-info strong { color: #edf1f7; font-size: 13px; }
.navigation-user-info span { color: #7f8b9c; font-size: 11px; }
.online-dot { position: absolute; right: 12px; bottom: 12px; width: 7px; height: 7px; background: #66d19e; border: 2px solid #1a2029; border-radius: 50%; }
.nav-label { padding: 26px 12px 8px; color: #657183; font-size: 10px; font-weight: 800; letter-spacing: 1.4px; text-transform: uppercase; }
.category-label { padding-top: 30px; }
.navigation-menu { background: transparent; }
.navigation-menu :deep(.el-menu-item) { height: 42px; margin: 3px 0; padding: 0 12px !important; color: #8f9aaa; border-radius: 8px; }
.navigation-menu :deep(.el-menu-item:hover) { color: #edf1f7; background: #1d2530; }
.navigation-menu :deep(.el-menu-item.is-active) { color: #f2bd5c; background: #252b35; }
.navigation-menu :deep(.el-menu-item .el-icon) { margin-right: 12px; font-size: 17px; }
.navigation-menu :deep(.el-menu-item span) { font-size: 13px; }
.navigation-footer { margin-top: auto; padding: 26px 12px 0; color: #667386; font-size: 11px; }
.footer-line { display: flex; align-items: center; gap: 7px; }
.footer-signal { width: 6px; height: 6px; background: #66d19e; border-radius: 50%; }
.footer-button { margin-top: 15px; padding: 0; color: #9aa5b5; font-size: 12px; }
.footer-button:hover { color: #f2bd5c; }
@media (max-width: 760px) { .app-navigation { display: none; } }
</style>
