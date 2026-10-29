<template>
  <slot :props="{ onClick: open }" />
  <el-dialog v-model="dialog" append-to-body width="500px" :title="`${title}收藏夹`">
    <el-input v-model="currentData.name" placeholder="收藏夹名称" class="mb-3" />
    <el-input v-model="currentData.description" placeholder="收藏夹描述" />
    <template #footer>
      <el-button v-if="props.editData" type="danger" @click="deleteFavorite">删除</el-button>
      <el-button type="primary" :loading="saving" @click="saveFavorite">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiRemoveFavorite, apiSaveFavorite } from '../../apis/user/favorites'

const props = defineProps({
  editData: { type: Object, default: null },
  closeEvent: { type: Function, default: () => {} }
})
const dialog = ref(false)
const emptyData = () => ({ name: '', description: '' })
const title = computed(() => props.editData ? '编辑' : '添加')
const currentData = ref(props.editData ? { ...props.editData } : emptyData())
const saving = ref(false)

const open = () => {
  if (!props.editData) currentData.value = emptyData()
  dialog.value = true
}

const saveFavorite = async () => {
  if (saving.value) return
  if (!currentData.value.name?.trim()) {
    ElMessage.warning('请输入收藏夹名称')
    return
  }
  saving.value = true
  try {
    const { data } = await apiSaveFavorite({ ...currentData.value, name: currentData.value.name.trim() })
    if (!data?.success) {
      ElMessage.error(data?.message || '保存收藏夹失败')
      return
    }
    ElMessage.success('收藏夹已保存')
    dialog.value = false
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存收藏夹失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
const deleteFavorite = () => apiRemoveFavorite(currentData.value.id).then(({ data }) => {
  if (data.success) dialog.value = false
})

watch(dialog, (value) => { if (!value) props.closeEvent() })
watch(() => props.editData, (value) => {
  currentData.value = value ? { ...value } : emptyData()
  if (value) dialog.value = true
}, { deep: true })
</script>
