<template>
  <el-container class="favorites-layout">
    <el-aside width="210px" class="favorites-sidebar">
      <div class="sidebar-title">我的收藏夹</div>
      <FavoriteEdit :edit-data="null" :close-event="getUserFavorites">
        <template #default="{ props }">
          <el-button v-bind="props" type="primary" plain class="add-favorite-button">
            <i class="mdi mdi-plus" />
            添加收藏夹
          </el-button>
        </template>
      </FavoriteEdit>
      <el-divider />
      <div v-if="favoriteItems.length" class="favorite-menu">
        <div
          v-for="(item, index) in favoriteItems"
          :key="item.id || index"
          :class="['favorite-item', { active: currentFavoriteIndex === index }]"
          @click="currentFavoriteIndex = index"
        >
          <span>{{ item.name }}</span>
          <el-button link class="edit-favorite-button" @click.stop="showMenu(index)">
            <i class="mdi mdi-pen" />
          </el-button>
        </div>
      </div>
      <div v-else class="empty-sidebar">您没有收藏夹</div>
      <FavoriteEdit :edit-data="editFavorite" :close-event="handleFavoriteClose" />
    </el-aside>
    <el-main class="favorites-content">
      <VideoList :video-list="videoList" />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { apiGetFavorites } from '../../../apis/user/favorites'
import { apiGetVideoByFavoriteId } from '../../../apis/video'
import FavoriteEdit from '../../../components/favorite/edit.vue'
import VideoList from '../../../components/video/list.vue'

const favoriteItems = ref([])
const videoList = ref([])
const currentFavoriteIndex = ref(0)
const currentFavorite = computed(() => favoriteItems.value[currentFavoriteIndex.value] || null)
const editFavorite = ref(null)

const getUserFavorites = () => {
  apiGetFavorites().then(({ data }) => {
    favoriteItems.value = data.success ? data.data : []
    if (!favoriteItems.value.length) {
      currentFavoriteIndex.value = 0
      videoList.value = []
      return
    }
    if (currentFavoriteIndex.value >= favoriteItems.value.length) currentFavoriteIndex.value = 0
  })
}

const getFavoriteVideo = () => {
  if (!currentFavorite.value) {
    videoList.value = []
    return
  }
  apiGetVideoByFavoriteId(currentFavorite.value.id).then(({ data }) => {
    videoList.value = data.success ? data.data : []
  })
}

const showMenu = (index) => { editFavorite.value = favoriteItems.value[index] }
const handleFavoriteClose = () => {
  editFavorite.value = null
  getUserFavorites()
}

getUserFavorites()
watch(currentFavorite, getFavoriteVideo)
onMounted(getFavoriteVideo)
</script>

<style scoped>
.favorites-layout { min-height: 360px; }
.favorites-sidebar { padding: 4px 12px 16px 0; border-right: 1px solid rgba(255, 255, 255, 0.08); }
.sidebar-title { margin-bottom: 14px; font-size: 16px; font-weight: 600; }
.add-favorite-button { width: 100%; }
.favorite-menu { display: grid; gap: 4px; }
.favorite-item { display: flex; align-items: center; justify-content: space-between; min-height: 38px; padding: 0 6px 0 12px; border-radius: 4px; cursor: pointer; }
.favorite-item:hover, .favorite-item.active { color: #7bbfea; background: rgba(123, 191, 234, 0.14); }
.edit-favorite-button { opacity: 0.7; }
.empty-sidebar { color: #aeb4c0; font-size: 13px; }
.favorites-content { padding: 0 0 0 16px; }
@media (max-width: 700px) {
  .favorites-layout { display: block; }
  .favorites-sidebar { width: 100% !important; border-right: 0; }
  .favorites-content { padding: 16px 0 0; }
}
</style>
