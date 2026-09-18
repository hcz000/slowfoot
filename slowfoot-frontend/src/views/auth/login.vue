<template>
  <div class="login-panel">
    <h2>登录账号</h2>
    <div class="login-form">
      <label>邮箱</label>
      <el-input v-model="loginInfo.email" placeholder="请输入邮箱" type="email" />
      <label>密码</label>
      <el-input v-model="loginInfo.password" placeholder="请输入密码" type="password" show-password
        @keyup.enter="loginVertify" />
      <el-button class="login-button" type="primary" size="large" :loading="loading" @click="loginVertify">
        登录账号
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { apiAuth } from '../../apis/user/auth'

const { showMessage, loginSuccess } = defineProps({
  showMessage: { type: Function, default: () => {} },
  loginSuccess: { type: Function, default: () => {} }
})
const loading = ref(false)
const loginInfo = reactive({ email: '', password: '' })

const loginVertify = () => {
  loading.value = true
  apiAuth(1, loginInfo).then(({ data }) => {
    showMessage(data.message, data.success ? 'success' : 'error')
    if (data.success) loginSuccess({ info: {}, token: data.data?.token })
  }).finally(() => { loading.value = false })
}
</script>

<style scoped>
.login-panel h2 { margin: 0; padding: 20px 0; font-size: 22px; }
.login-form { display: flex; flex-direction: column; gap: 10px; }
.login-form label { font-weight: 700; }
.login-button { width: 100%; margin-top: 8px; }
</style>
