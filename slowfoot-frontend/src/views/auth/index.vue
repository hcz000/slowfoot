<template>
  <el-button v-if="showTrigger && !userStore.token" class="auth-button" text @click="open">
    <el-icon><User /></el-icon>登录
  </el-button>
  <el-button v-else-if="showTrigger" class="auth-button" text @click="logout">退出登录</el-button>

  <el-dialog
    v-model="dialog"
    class="auth-dialog"
    :width="mode === 0 ? 'min(92vw, 440px)' : 'min(94vw, 760px)'"
    align-center
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    title="欢迎回来"
  >
    <div class="auth-card">
      <div class="auth-intro">
        <div><h2>登录 SlowFoot</h2><p>继续发现值得收藏的内容</p></div>
      </div>
      <component :is="components[mode]" :show-message="showMessage" v-bind="mode === 0 ? { loginSuccess } : {}" />
      <div class="auth-actions">
        <el-button v-if="mode !== 2" link @click="mode = 2">忘记密码？</el-button>
        <div class="auth-action-row">
          <el-button @click="dialog = false">取消</el-button>
          <el-button type="primary" @click="mode = mode === 0 ? 1 : 0">{{ mode === 0 ? '注册账号' : '返回登录' }}</el-button>
        </div>
      </div>
    </div>
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

const props = defineProps({ showTrigger: { type: Boolean, default: true } })
const userStore = useUserStore()
const dialog = ref(false)
const mode = ref(0)
const components = [login, register, forget]
const open = () => { mode.value = 0; dialog.value = true }
defineExpose({ open })

const clearSession = () => {
  sessionStorage.removeItem('token')
  userStore.$patch({ token: null, info: {} })
}
const getUserInfo = async () => {
  if (!userStore.token) return
  try {
    const { data } = await apiGetUserInfo()
    if (!data.success) { clearSession(); return }
    userStore.$patch({ info: data.data })
    apiInitFollowFeed().catch(() => {})
  } catch { clearSession() }
}
const showMessage = (message, type = 'error') => ElMessage({ message: message || '请求失败', type })
const loginSuccess = ({ token }) => {
  if (!token) { showMessage('登录响应中没有 token'); return }
  sessionStorage.setItem('token', token)
  userStore.$patch({ token })
  dialog.value = false
  getUserInfo()
}
const logout = () => { clearSession(); router.push('/') }

onMounted(getUserInfo)
watch(dialog, (visible) => { if (!visible) mode.value = 0 })
</script>

<style scoped>
.auth-button { color: #555; }
.auth-button:hover { color: #ff2442; background: #fff0f2; }
.auth-card { width: 100%; padding: 2px 2px 0; }
.auth-intro { display: flex; align-items: center; margin-bottom: 18px; }
.auth-intro h2 { margin: 0; color: #333; font-size: 20px; }
.auth-intro p { margin: 4px 0 0; color: #999; font-size: 12px; }
.auth-actions { padding-top: 16px; text-align: center; }
.auth-action-row { display: flex; gap: 10px; margin-top: 10px; }
.auth-action-row .el-button { flex: 1; }
</style>
