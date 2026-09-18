<template>
  <slot :props="{ onClick: () => { dialog = true } }" />
  <el-dialog v-model="dialog" width="300px" title="选择收藏夹">
    <el-radio-group v-model="dialogm1" class="favorite-options">
      <el-radio v-for="item in favoriteItems" :key="item.id" :value="item.id">{{ item.name }}</el-radio>
    </el-radio-group>
    <el-divider />
    <div class="dialog-actions">
      <el-button link @click="dialog = false">取消</el-button>
      <FavoriteEdit :close-event="getFavorites">
        <template #default="{ props }">
          <el-button link v-bind="props">创建收藏夹</el-button>
        </template>
      </FavoriteEdit>
      <el-button type="primary" link @click="save">收藏</el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { apiFavoriteVideo, apiGetFavorites } from '../../apis/user/favorites'
import FavoriteEdit from './edit.vue'

const props = defineProps({
  videoId: { type: Number, default: null },
  callback: { type: Function, default: () => {} }
})
const dialog = ref(false)
const dialogm1 = ref(0)
const favoriteItems = ref([])

const getFavorites = () => apiGetFavorites().then(({ data }) => {
  if (!data.success) { dialog.value = false; return }
  favoriteItems.value = data.data || []
  if (favoriteItems.value.length) dialogm1.value = favoriteItems.value[0].id
})

watch(dialog, (value) => { if (value) getFavorites() })
const save = () => {
  if (!props.videoId || !dialogm1.value) return
  apiFavoriteVideo(dialogm1.value, props.videoId).then(({ data }) => {
    if (!data.success) return
    dialog.value = false
    props.callback(data.message)
  })
}
</script>

<style scoped>
.favorite-options { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; width: 100%; }
.dialog-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
</style>
