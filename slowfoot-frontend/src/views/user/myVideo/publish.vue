<template>
  <el-card shadow="never">
    <div class="card-heading">发布作品</div>
    <el-tabs v-model="mode">
      <el-tab-pane label="文字" name="text">
        <form class="publish-form" @submit.prevent="publishText">
          <el-input v-model="textWork.title" placeholder="标题" required />
          <el-input v-model="textWork.description" type="textarea" placeholder="简介" :rows="2" />
          <el-input v-model="textWork.content" type="textarea" placeholder="正文" :rows="8" required />
          <el-select v-model="textWork.typeId" placeholder="分类" class="full-width" required>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="textLabels" multiple filterable allow-create default-first-option collapse-tags
            placeholder="标签" class="full-width" />
          <el-switch v-model="textWork.openState" active-text="仅自己可见" />
          <el-button native-type="submit" type="primary" :loading="submitting">发布文字作品</el-button>
        </form>
      </el-tab-pane>
      <el-tab-pane label="图片" name="image">
        <form class="publish-form" @submit.prevent="publishImage">
          <el-input v-model="imageWork.title" placeholder="标题" required />
          <el-input v-model="imageWork.description" type="textarea" placeholder="简介" :rows="2" />
          <el-select v-model="imageWork.typeId" placeholder="分类" class="full-width" required>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="imageLabels" multiple filterable allow-create default-first-option collapse-tags
            placeholder="标签" class="full-width" />
          <el-switch v-model="imageWork.openState" active-text="仅自己可见" />
          <input ref="imageInput" type="file" accept="image/*" multiple @change="selectImages" />
          <div v-if="imageFiles.length" class="image-preview-grid">
            <el-image v-for="file in imageFiles" :key="file.name + file.lastModified" :src="previewUrl(file)" fit="cover" />
          </div>
          <el-progress v-if="uploading" class="my-4" :percentage="progress" status="success" />
          <el-button class="mt-4" native-type="submit" type="primary" :loading="submitting || uploading"
            :disabled="!imageFiles.length">发布图片作品</el-button>
        </form>
      </el-tab-pane>
    </el-tabs>
    <el-alert v-if="snackbar.show" :title="snackbar.text" :type="snackbar.color || 'error'" closable
      @close="snackbar.show = false" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { apiClassifyGetAll } from '../../../apis/classify'
import { apiUploadFile } from '../../../apis/file'
import { apiPublishImageWork, apiPublishTextWork } from '../../../apis/work'

const mode = ref('text')
const categories = ref([])
const textLabels = ref([])
const imageLabels = ref([])
const imageFiles = ref([])
const imageInput = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const progress = ref(0)
const snackbar = ref({ show: false, text: '', color: 'error' })
const textWork = reactive({ title: '', description: '', content: '', typeId: null, openState: false })
const imageWork = reactive({ title: '', description: '', typeId: null, openState: false })

const showMessage = (text, color = 'error') => { snackbar.value = { show: true, text, color } }
const selectImages = (event) => { imageFiles.value = Array.from(event.target.files || []) }
const previewUrl = (file) => URL.createObjectURL(file)

const uploadImage = (file) => new Promise(async (resolve, reject) => {
  try {
    await apiUploadFile(file, {
      next: (event) => { progress.value = event.total.percent },
      error: reject,
      complete: (_result, response) => {
        if (!response?.success) reject(new Error(response?.message || '图片上传失败'))
        else resolve(response.data)
      }
    })
  } catch (error) { reject(error) }
})

const publishText = async () => {
  submitting.value = true
  try {
    const { data } = await apiPublishTextWork({ ...textWork, labelNames: textLabels.value.join(',') })
    showMessage(data.message, data.success ? 'success' : 'error')
    if (data.success) {
      Object.assign(textWork, { title: '', description: '', content: '' })
      textLabels.value = []
    }
  } catch (error) { showMessage(error.message) } finally { submitting.value = false }
}

const publishImage = async () => {
  submitting.value = true; uploading.value = true; progress.value = 0
  try {
    const imageFileIds = []
    for (const file of imageFiles.value) imageFileIds.push(await uploadImage(file))
    const { data } = await apiPublishImageWork({ ...imageWork, labelNames: imageLabels.value.join(','), imageFileIds })
    showMessage(data.message, data.success ? 'success' : 'error')
    if (data.success) { imageFiles.value = []; imageLabels.value = []; if (imageInput.value) imageInput.value.value = '' }
  } catch (error) { showMessage(error.message) } finally { uploading.value = false; submitting.value = false }
}

onMounted(() => apiClassifyGetAll().then(({ data }) => { if (data.success) categories.value = data.data || [] }))
</script>

<style scoped>
.card-heading { padding: 16px 0; font-size: 18px; }
.publish-form { display: flex; flex-direction: column; gap: 14px; padding: 16px 0; }
.full-width { width: 100%; }
.image-preview-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; }
.image-preview-grid .el-image { width: 100%; aspect-ratio: 1; border-radius: 4px; }
.my-4 { margin: 16px 0; }
.mt-4 { margin-top: 16px; }
</style>
