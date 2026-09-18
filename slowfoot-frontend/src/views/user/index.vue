<template>
  <div class="profile-page">
    <el-card class="profile-card" shadow="never">
      <div class="profile-cover">
        <el-image class="profile-banner" fit="cover" src="/logo.png" />
        <div class="profile-overlay">
          <el-avatar :src="avatarImg" :size="50" />
          <div class="profile-summary">
            <strong>{{ userInfo.nickName }}</strong>
            <span>{{ userInfo.description || '这个人很懒，没有任何描述' }}</span>
          </div>
        </div>
      </div>

      <div class="profile-navigation">
        <el-button v-for="item in navigationItems" :key="item.path" link :class="{ active: isActive(item.path) }" @click="goTo(item.path)">
          {{ item.label }}
        </el-button>
        <span class="navigation-spacer" />
        <el-button v-if="isSelf" type="primary" plain @click="editDialog = true">编辑信息</el-button>
      </div>
    </el-card>

    <router-view class="mt-2" />

    <el-dialog v-model="editDialog" title="编辑用户信息" width="400px">
      <form class="profile-form" @submit.prevent="saveInfo">
        <div class="avatar-picker" @click="avatarFileRef?.click()">
          <el-avatar :src="avatarImg" :size="80" />
          <div class="avatar-picker-overlay">
            <el-progress v-if="uploading > -1" type="circle" :percentage="uploading" :width="42" />
            <i v-else class="mdi mdi-upload" />
          </div>
        </div>
        <input ref="avatarFileRef" hidden type="file" accept="image/*" @change="uploadAvatar" />
        <el-form-item label="昵称">
          <el-input v-model="userInfo.nickName" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="userInfo.description" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="性别">
          <el-switch v-model="userInfo.sex" active-text="男生" inactive-text="女生" />
        </el-form-item>
      </form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="saveInfo">保存</el-button>
      </template>
    </el-dialog>

    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'info'" closable class="profile-alert" @close="snackbar.show = false" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { apiFileGet, apiUploadFile } from '../../apis/file'
import { apiChangeUserInfo, apiGetUserInfo } from '../../apis/user/user'
import router from '../../router'
import { useUserStore } from '../../stores'

const userStore = useUserStore()
const route = useRoute()
const userInfo = ref({})
const editDialog = ref(false)
const avatarFileRef = ref(null)
const uploading = ref(-1)
const snackbar = ref({ show: false, text: '', color: 'info' })

const isSelf = computed(() => Boolean(userInfo.value?.id && userStore.info?.id === userInfo.value.id))
const avatarImg = computed(() => {
  if (avatarFileRef.value?.files?.[0]) {
    return URL.createObjectURL(avatarFileRef.value.files[0])
  }
  return userInfo.value?.avatar ? apiFileGet(userInfo.value.avatar) : '/logo.png'
})

const navigationItems = computed(() => [
  { label: '主页', path: '/user/home' },
  ...(isSelf.value ? [
    { label: '创作中心', path: '/user/video' },
    { label: '收藏夹', path: '/user/favorites' },
    { label: '历史记录', path: '/user/history' },
    { label: '订阅分类', path: '/user/classify' }
  ] : []),
  { label: '关注/粉丝', path: '/user/like' },
  ...(isSelf.value ? [{ label: '发布作品', path: '/user/publish' }] : [])
])

const isActive = (path) => route.path === path || (path === '/user/home' && route.path === '/user')
const goTo = (path) => {
  const lookId = !isSelf.value && userInfo.value?.id ? { lookId: userInfo.value.id } : undefined
  router.push({ path, query: lookId })
}

const uploadAvatar = () => {
  const file = avatarFileRef.value?.files?.[0]
  if (!file) return
  apiUploadFile(file, {
    next: (event) => { uploading.value = event.total.percent },
    error: () => {
      uploading.value = -1
      snackbar.value = { text: '上传失败', show: true, color: 'error' }
    },
    complete: (_event, result) => {
      uploading.value = -1
      if (!result.success) {
        snackbar.value = { text: result.message, show: true, color: 'error' }
        return
      }
      userInfo.value.avatar = result.data
      snackbar.value = { text: '上传完成', show: true, color: 'success' }
    }
  })
}

const getUserInfo = () => {
  const lookId = route.query?.lookId || userStore.info?.id
  userStore.$patch({ lookId })
  if (!lookId) return
  apiGetUserInfo(lookId).then(({ data }) => {
    if (data.success) userInfo.value = data.data
  })
}

const saveInfo = () => {
  apiChangeUserInfo(userInfo.value).then(({ data }) => {
    snackbar.value = { text: data.message, show: true, color: data.success ? 'success' : 'error' }
    if (!data.success) return
    editDialog.value = false
    apiGetUserInfo(userInfo.value.id).then(({ data: response }) => {
      if (response.success) userStore.$patch({ info: response.data })
    })
  })
}

watch(() => route.query, getUserInfo, { immediate: true })

onMounted(() => {
  if (!userStore.token) router.push({ path: '/' })
})
</script>

<style scoped>
.profile-card { overflow: hidden; }
.profile-cover { position: relative; height: 220px; }
.profile-banner { width: 100%; height: 100%; }
.profile-overlay { position: absolute; right: 0; bottom: 0; left: 0; display: flex; align-items: center; gap: 12px; padding: 18px; color: white; background: rgba(1, 1, 1, 0.5); }
.profile-summary { display: flex; flex-direction: column; gap: 4px; }
.profile-summary span { color: rgba(255, 255, 255, 0.76); font-size: 13px; }
.profile-navigation { display: flex; align-items: center; gap: 4px; padding-top: 10px; flex-wrap: wrap; }
.profile-navigation .active { color: #7bbfea; }
.navigation-spacer { flex: 1; }
.profile-form { display: grid; gap: 14px; }
.avatar-picker { position: relative; width: 80px; height: 80px; margin: 0 auto 8px; cursor: pointer; }
.avatar-picker-overlay { position: absolute; inset: 0; display: grid; place-items: center; border-radius: 50%; color: white; background: rgba(0, 0, 0, 0.45); opacity: 0; transition: opacity 0.15s ease; }
.avatar-picker:hover .avatar-picker-overlay { opacity: 1; }
.profile-alert { position: fixed; right: 24px; bottom: 24px; z-index: 20; width: min(360px, calc(100vw - 48px)); }
</style>
