<template>
  <aside v-show="open" class="app-navigation">
    <div class="user-card">
      <el-avatar v-if="userStore.token && userStore.info.avatar" :size="42" :src="apiFileGet(userStore.info.avatar)" />
      <DefaultAvatar v-else :size="42" />
      <div class="user-copy">
        <strong>{{ userStore.token ? (userStore.info.nickName || 'SlowFoot 用户') : '还未登录' }}</strong>
        <span>{{ userStore.token ? (userStore.info.description || '欢迎回来') : '登录后解锁更多功能' }}</span>
      </div>
      <i class="online-dot" />
    </div>

    <div class="nav-label">发现</div>
    <nav class="navigation-menu">
      <button :class="['nav-item', { active: route.path === '/' }]" type="button" @click="navigate('/')"><el-icon><House /></el-icon><span>热门作品</span></button>
      <button :class="['nav-item', { active: route.path === '/pushVideo' }]" type="button" @click="navigate('/pushVideo')"><el-icon><Compass /></el-icon><span>推荐作品</span></button>
      <button v-if="userStore.token" :class="['nav-item', { active: route.path === '/followVideo' }]" type="button" @click="navigate('/followVideo')"><el-icon><Star /></el-icon><span>关注动态</span></button>
    </nav>

    <div v-if="userStore.token" class="nav-label">我的内容</div>
    <nav v-if="userStore.token" class="navigation-menu">
      <button :class="['nav-item', { active: route.path === '/user/publish' }]" type="button" @click="navigate('/user/publish')"><el-icon><Upload /></el-icon><span>发布作品</span></button>
      <button :class="['nav-item', { active: route.path.startsWith('/user/favorites') }]" type="button" @click="navigate('/user/favorites')"><el-icon><Star /></el-icon><span>我的收藏</span></button>
    </nav>

    <div class="nav-label category-label">频道</div>
    <nav class="navigation-menu">
      <button v-for="item in allClassifyList" :key="item.id" :class="['nav-item', { active: route.path === `/video/${item.id}` }]" type="button" @click="navigate(`/video/${item.id}`)"><el-icon><Folder /></el-icon><span>{{ item.name }}</span></button>
    </nav>

    <div class="navigation-footer">
      <div class="footer-line"><i class="footer-signal" />内容持续更新</div>
      <button v-if="userStore.token" class="footer-button" type="button" @click="navigate('/user')"><el-icon><User /></el-icon>个人空间</button>
    </div>
  </aside>
</template>

<script setup>
import { ref } from 'vue'
import { Compass, Folder, House, Star, Upload, User } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { apiClassifyGetAll } from '../../apis/classify'
import { apiFileGet } from '../../apis/file'
import { useUserStore } from '../../stores'
import DefaultAvatar from '../../components/DefaultAvatar.vue'

defineProps({ open: { type: Boolean, default: true } })
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const allClassifyList = ref([])
const navigate = (path) => router.push(path)

apiClassifyGetAll().then(({ data }) => { if (data.success) allClassifyList.value = data.data || [] }).catch(() => { allClassifyList.value = [] })
</script>

<style scoped>
.app-navigation { position: sticky; top: 64px; display: flex; flex: 0 0 216px; flex-direction: column; width: 216px; height: calc(100vh - 64px); min-height: calc(100vh - 64px); padding: 18px 13px; overflow-y: auto; background: #fff; border-right: 1px solid #ededed; }
.user-card { position: relative; display: flex; align-items: center; gap: 10px; min-height: 62px; padding: 9px 10px; background: #fafafa; border-radius: 12px; }
.user-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 5px; }
.user-copy strong, .user-copy span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-copy strong { color: #333; font-size: 13px; }
.user-copy span { color: #999; font-size: 10px; }
.online-dot { position: absolute; right: 11px; bottom: 11px; width: 7px; height: 7px; background: #35bd7b; border: 2px solid #fafafa; border-radius: 50%; }
.nav-label { padding: 25px 12px 8px; color: #aaa; font-size: 10px; font-weight: 650; letter-spacing: 1.2px; }
.category-label { padding-top: 26px; }
.navigation-menu { display: grid; gap: 3px; }
.nav-item { display: flex; align-items: center; width: 100%; height: 41px; padding: 0 13px; color: #666; background: transparent; border: 0; border-radius: 10px; cursor: pointer; font: inherit; text-align: left; transition: color .15s ease, background .15s ease; }
.nav-item:hover { color: #333; background: #f6f6f6; }
.nav-item.active { color: #ff2442; background: #fff0f2; font-weight: 650; }
.nav-item .el-icon { margin-right: 12px; font-size: 17px; }
.nav-item span { font-size: 13px; }
.navigation-footer { margin-top: auto; padding: 25px 12px 0; color: #aaa; font-size: 10px; }
.footer-line { display: flex; align-items: center; gap: 7px; }
.footer-signal { width: 6px; height: 6px; background: #35bd7b; border-radius: 50%; }
.footer-button { display: flex; align-items: center; gap: 7px; margin-top: 16px; padding: 0; color: #777; background: transparent; border: 0; cursor: pointer; font: inherit; font-size: 12px; }
.footer-button:hover { color: #ff2442; }
@media (max-width: 760px) { .app-navigation { display: none !important; } }
</style>
