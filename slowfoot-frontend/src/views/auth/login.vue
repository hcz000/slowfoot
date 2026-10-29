<template>
  <form class="login-form" @submit.prevent="loginVerify">
    <label for="login-email">邮箱</label>
    <el-input id="login-email" v-model.trim="loginInfo.email" autocomplete="username" placeholder="请输入邮箱" type="email" required />
    <label for="login-password">密码</label>
    <el-input id="login-password" v-model="loginInfo.password" autocomplete="current-password" placeholder="请输入密码" type="password" show-password required />
    <label for="login-captcha">验证码</label>
    <div class="captcha-row">
      <el-input id="login-captcha" v-model="loginInfo.captchaCode" placeholder="请输入验证码" maxlength="5" required />
      <el-image :src="captchaImg" fit="contain" class="captcha-img" @click="refreshCaptcha" />
    </div>
    <el-button class="login-button" native-type="submit" type="primary" size="large" :loading="loading">登录账号</el-button>
  </form>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { apiAuth, apiGetCode } from '../../apis/user/auth'
import buildUtils from '../../utils/buildUtil'

const { showMessage, loginSuccess } = defineProps({
  showMessage: { type: Function, default: () => {} },
  loginSuccess: { type: Function, default: () => {} }
})
const loading = ref(false)
const captchaImg = ref('')
const loginInfo = reactive({ email: '', password: '', captchaCode: '', uuid: '' })

const refreshCaptcha = () => {
  loginInfo.uuid = buildUtils.guid()
  captchaImg.value = apiGetCode(1, loginInfo.uuid)
  loginInfo.captchaCode = ''
}

onMounted(refreshCaptcha)

const loginVerify = async () => {
  if (loading.value) return
  if (!loginInfo.email || !loginInfo.password) { showMessage('请输入邮箱和密码'); return }
  if (!loginInfo.captchaCode) { showMessage('请输入验证码'); return }
  loading.value = true
  try {
    const { data } = await apiAuth(1, loginInfo)
    showMessage(data.message, data.success ? 'success' : 'error')
    if (data.success) loginSuccess({ token: data.data?.token })
    else refreshCaptcha()
  } catch (error) {
    showMessage(error.response?.data?.message || '登录失败，请检查邮箱和密码')
    refreshCaptcha()
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-form { display: flex; flex-direction: column; gap: 9px; }
.login-form label { margin-top: 4px; color: #555; font-size: 13px; font-weight: 650; }
.login-button { width: 100%; margin-top: 10px; }
.captcha-row { display: flex; gap: 8px; align-items: center; }
.captcha-row .el-input { flex: 1; }
.captcha-img { width: 120px; height: 38px; cursor: pointer; border-radius: 6px; border: 1px solid #ddd; }
</style>
