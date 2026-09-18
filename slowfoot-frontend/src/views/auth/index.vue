<template>
  <el-button v-if="showTrigger && !userStore.token" text class="auth-button" @click="open">
    <el-icon><User /></el-icon>登录
  </el-button>
  <el-button v-else-if="showTrigger" text class="auth-button" @click="outlogo">退出登录</el-button>

  <el-dialog v-model="dialog" :close-on-click-modal="false" width="430px" title="欢迎回来">
    <el-card class="auth-card" shadow="never">
      <component :is="components[isLogin]" :show-message="showMessage" v-bind="isLogin === 0 ? { loginSuccess } : {}" />
      <div class="auth-actions">
        <el-button v-if="isLogin !== 2" link @click="isLogin = 2">忘记密码？</el-button>
        <div class="auth-action-row">
          <el-button class="action-button" @click="dialog = false">取消</el-button>
          <el-button class="action-button" type="primary" @click="isLogin = isLogin === 0 ? 1 : 0">
            {{ isLogin === 0 ? '注册账号' : '登录账号' }}
          </el-button>
        </div>
      </div>
    </el-card>
  </el-dialog>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { apiGetUserInfo } from '../../apis/user/user'
import { apiInitFollowFeed } from '../../apis/video'
import router from '../../router'
import { useUserStore } from '../../stores'
import forget from './forget.vue'
import login from './login.vue'
import register from './register.vue'

const userStore = useUserStore()
const { showTrigger } = defineProps({ showTrigger: { type: Boolean, default: true } })
const dialog = ref(false)
const isLogin = ref(0)
const components = [login, register, forget]
const open = () => { dialog.value = true }
defineExpose({ open })

const getUserInfo = () => {
  if (!userStore.token) return
  apiGetUserInfo().then(({ data }) => {
    if (!data.success) {
      userStore.$patch({ info: {}, token: null })
      return
    }
    apiInitFollowFeed()
    userStore.$patch({ info: data.data })
  })
}
const showMessage = (text, type = 'error') => ElMessage({ message: text, type })
const loginSuccess = (data) => {
  if (!data?.token) return
  userStore.$patch({ token: data.token })
  sessionStorage.setItem('token', data.token)
  getUserInfo()
  dialog.value = false
}
const outlogo = () => {
  sessionStorage.removeItem('token')
  userStore.$patch({ token: null, info: {} })
  router.push('/')
}

onMounted(getUserInfo)
watch(dialog, (value) => { if (!value) isLogin.value = 0 })
</script>

<style scoped>
.auth-button { color: #d5dce5; }
.auth-button:hover { color: #f2bd5c; }
.auth-card { border: 0; background: transparent; }
.auth-actions { padding-top: 12px; text-align: center; }
.auth-action-row { display: flex; gap: 12px; margin-top: 12px; }
.action-button { flex: 1; }
</style>
