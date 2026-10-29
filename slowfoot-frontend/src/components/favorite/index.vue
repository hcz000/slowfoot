<template>
  <slot :props="{ onClick: () => { dialog = true } }" />
  <el-dialog v-model="dialog" append-to-body width="340px" title="收藏到">
    <div v-if="loading" class="favorite-state">正在加载收藏夹…</div>
    <el-alert v-else-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
    <div v-else-if="!favoriteItems.length" class="favorite-state">
      <strong>你还没有收藏夹</strong>
      <span>先创建一个收藏夹，再保存这条作品。</span>
    </div>
    <el-radio-group v-else v-model="dialogm1" class="favorite-options">
      <el-radio v-for="item in favoriteItems" :key="item.id" :value="item.id">{{ item.name }}</el-radio>
    </el-radio-group>
    <el-divider />
    <div class="dialog-actions">
      <el-button link @click="dialog = false">取消</el-button>
      <FavoriteEdit :close-event="getFavorites">
        <template #default="{ props }">
          <el-button link v-bind="props">{{ favoriteItems.length ? '新建收藏夹' : '创建收藏夹' }}</el-button>
        </template>
      </FavoriteEdit>
      <el-button type="primary" :loading="saving" :disabled="loading || Boolean(loadError) || !favoriteItems.length || !dialogm1" @click="save">收藏</el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiFavoriteVideo, apiGetFavorites } from '../../apis/user/favorites'
import FavoriteEdit from './edit.vue'

const props = defineProps({
  videoId: { type: [Number, String], default: null },
  callback: { type: Function, default: () => {} }
})
const dialog = ref(false)
const dialogm1 = ref(null)
const favoriteItems = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')

const getFavorites = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await apiGetFavorites()
    if (!data?.success) throw new Error(data?.message || '收藏夹加载失败')
    favoriteItems.value = Array.isArray(data.data) ? data.data : []
    if (!favoriteItems.value.some((item) => item.id === dialogm1.value)) {
      dialogm1.value = favoriteItems.value[0]?.id ?? null
    }
  } catch (error) {
    loadError.value = error?.message || '收藏夹加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(dialog, (value) => { if (value) getFavorites() })
const save = async () => {
  if (saving.value) return
  if (!favoriteItems.value.length) {
    ElMessage.warning('请先创建收藏夹')
    return
  }
  if (!props.videoId || !dialogm1.value) {
    ElMessage.warning('请先选择收藏夹')
    return
  }
  saving.value = true
  try {
    const { data } = await apiFavoriteVideo(dialogm1.value, props.videoId)
    if (!data?.success) {
      ElMessage.error(data?.message || '收藏失败，请稍后重试')
      return
    }
    dialog.value = false
    props.callback(data.message, Boolean(data.data))
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '收藏失败，请检查网络后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.favorite-options { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; width: 100%; }
.dialog-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.favorite-state { display: flex; min-height: 76px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #606a78; font-size: 13px; text-align: center; }
.favorite-state strong { color: #303844; font-size: 14px; }
</style>
