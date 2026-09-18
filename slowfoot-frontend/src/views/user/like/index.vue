<template>
  <el-card>
    <div class="section-title">关注 / 粉丝</div>
    <el-tabs v-model="currentType" @tab-change="getLike">
      <el-tab-pane label="关注" name="follows" />
      <el-tab-pane label="粉丝" name="fans" />
    </el-tabs>

    <div v-if="currentItems.length" class="people-list">
      <div v-for="item in currentItems" :key="item.id" class="person-row" @click="openUser(item)">
        <el-avatar :src="item.avatar ? apiFileGet(item.avatar) : '/logo.png'" :class="item.sex ? 'male-avatar' : 'female-avatar'">
          <i :class="['mdi', item.sex ? 'mdi-human-male' : 'mdi-human-female']" />
        </el-avatar>
        <div class="person-details">
          <strong>{{ item.nickName }}</strong>
          <span>{{ item.description || '这个人很懒，没有任何描述' }}</span>
        </div>
        <el-button type="info" link @click.stop="unLikeOrLike(item.id)">
          {{ currentType === 'fans' ? (item.each ? '取消互关' : '互相关注') : '取消关注' }}
        </el-button>
      </div>
    </div>
    <div v-else class="empty-state">好像没有什么内容呢</div>

    <el-pagination
      v-if="pageInfo.pages > 1"
      v-model:current-page="pageInfo.page"
      :page-count="pageInfo.pages"
      class="page-control"
      @current-change="getLike"
    />
    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'info'" closable class="page-alert" @close="snackbar.show = false" />
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { apiFileGet } from '../../../apis/file'
import { apiFollows, apiGetLike } from '../../../apis/user/like'
import router from '../../../router'
import { useUserStore } from '../../../stores'

const currentType = ref('fans')
const currentItems = ref([])
const snackbar = ref({ show: false, text: '', color: 'info' })
const pageInfo = ref({ page: 1, pages: 0 })
const userStore = useUserStore()

const getLike = () => {
  currentItems.value = []
  apiGetLike(currentType.value, userStore.lookId, pageInfo.value.page).then(({ data }) => {
    if (!data.success) return
    currentItems.value = data.data.records || []
    pageInfo.value.pages = data.data.pages || 0
  })
}

const openUser = (item) => router.push(`/user?lookId=${item.id}`)

const unLikeOrLike = (id) => {
  apiFollows(id).then(({ data }) => {
    snackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
    if (data.success) getLike()
  })
}

watch(() => userStore.lookId, getLike, { immediate: true })
</script>

<style scoped>
.section-title { font-size: 18px; font-weight: 600; }
.people-list { display: grid; gap: 1px; }
.person-row { display: flex; align-items: center; gap: 14px; min-height: 72px; padding: 10px 4px; cursor: pointer; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.person-row:hover { background: rgba(255, 255, 255, 0.04); }
.person-details { display: flex; flex: 1; flex-direction: column; gap: 4px; min-width: 0; }
.person-details span { overflow: hidden; color: #aeb4c0; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.male-avatar { background: #409eff; }
.female-avatar { background: #e78bba; }
.empty-state { display: grid; place-items: center; min-height: 240px; color: #aeb4c0; }
.page-control { justify-content: center; margin-top: 18px; }
.page-alert { position: fixed; right: 24px; bottom: 24px; z-index: 20; width: min(360px, calc(100vw - 48px)); }
</style>
