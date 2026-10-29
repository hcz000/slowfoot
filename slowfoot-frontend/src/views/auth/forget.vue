<template>
  <el-card class="auth-flow-card" shadow="never">
    <div class="flow-heading"><i class="mdi mdi-account" /> 找回账号</div>
    <el-card shadow="never" class="flow-panel">
      <el-steps :active="step - 1" finish-status="success" simple>
        <el-step v-for="item in stepData" :key="item.title" :title="item.title" />
      </el-steps>
      <el-divider />

      <form v-if="step === 1" :disabled="isLoading" class="flow-form" @submit.prevent="getEmailCode">
        <el-input v-model="registerInfo.email" placeholder="邮箱" type="email" required />
        <div class="captcha-row">
          <el-image :src="captchaImg" fit="contain" @click="getCaptchaImg" />
          <el-input v-model="registerInfo.captchaCode" placeholder="图形验证码" required />
        </div>
        <el-button native-type="submit" type="primary" :loading="isLoading">获取邮箱验证码</el-button>
        <el-input v-model="registerInfo.code" maxlength="6" placeholder="邮箱验证码" />
      </form>

      <form v-else-if="step === 2" class="flow-form" @submit.prevent="setPassword">
        <el-input v-model="registerInfo.newPassword" placeholder="请输入新密码" type="password" show-password required />
        <el-input v-model="registerInfo.confirmPassword" placeholder="请确认新密码" type="password" show-password required />
        <span class="form-hint">请为您的账号设置一个新密码</span>
      </form>

      <div v-else class="success-panel">
        <i class="mdi mdi-check-circle" />
        <h3>找回成功</h3>
        <span>请注意保存好密码</span>
      </div>

      <div class="flow-actions">
        <el-button v-if="step === 2" link :loading="isLoading" @click="step--">上一步</el-button>
        <span />
        <el-button v-if="step < 3" type="primary" :loading="isLoading" @click="stepData[step - 1].next()">下一步</el-button>
      </div>
    </el-card>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { apiCheckCode, apiForgetPassword, apiGetCode } from '../../apis/user/auth'
import buildUtils from '../../utils/buildUtil'

const { showMessage } = defineProps({
  showMessage: { type: Function, default: () => {} },
})
const step = ref(1)
const isLoading = ref(false)
const captchaImg = ref('')
const registerInfo = reactive({ email: '', code: '', captchaCode: '', newPassword: '', confirmPassword: '', uuid: '' })
const stepData = [
  { title: '邮箱验证', next: () => vertifyCode() },
  { title: '设置新密码', next: () => setPassword() },
  { title: '找回成功' }
]

const getCaptchaImg = () => {
  registerInfo.uuid = buildUtils.guid()
  captchaImg.value = apiGetCode(1, registerInfo.uuid)
}

const getEmailCode = async () => {
  if (isLoading.value) return
  isLoading.value = true
  try {
    const { data } = await apiGetCode(0, {
      email: registerInfo.email,
      code: registerInfo.captchaCode,
      uuid: registerInfo.uuid
    })
    showMessage(data.message, data.success ? 'success' : 'error')
    if (!data.success) getCaptchaImg()
  } catch (error) {
    showMessage(error.response?.data?.message || error.message)
  } finally {
    isLoading.value = false
  }
}

const vertifyCode = () => {
  if (isLoading.value) return
  isLoading.value = true
  apiCheckCode({ ...registerInfo }).then(({ data }) => {
    if (!data.success) { showMessage('邮箱验证码无效', 'error'); getCaptchaImg(); return }
    step.value++
  }).finally(() => { isLoading.value = false })
}

const setPassword = async () => {
  if (isLoading.value) return
  if (registerInfo.newPassword !== registerInfo.confirmPassword) {
    showMessage('两次输入的密码不一致', 'error')
    return
  }
  isLoading.value = true
  try {
    const { data } = await apiForgetPassword({
      email: registerInfo.email,
      code: registerInfo.code,
      newPassword: registerInfo.newPassword
    })
    if (!data.success) { showMessage(data.message, 'error'); return }
    step.value++
  } catch (error) {
    showMessage(error.response?.data?.message || '密码修改失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

getCaptchaImg()
</script>

<style scoped>
.auth-flow-card { max-width: 650px; margin: 0 auto; background: transparent; border: 0; }
.flow-heading { padding: 12px 0; font-size: 18px; font-weight: 700; }
.flow-panel { margin: 16px; background: #fafafa; border: 1px solid #eee; }
.flow-form { display: flex; flex-direction: column; gap: 14px; }
.captcha-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; align-items: center; }
.captcha-row .el-image { height: 42px; cursor: pointer; }
.form-hint, .success-panel span { color: #a3a6ad; }
.flow-actions { display: flex; justify-content: space-between; margin-top: 20px; }
.success-panel { padding: 32px 0; text-align: center; color: greenyellow; }
.success-panel i { font-size: 60px; }
.success-panel h3 { margin: 8px 0; }
</style>
