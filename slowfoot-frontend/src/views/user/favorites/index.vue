<template>
  <main class="favorites-page">
    <header class="favorites-heading">
      <div class="heading-copy">
        <span class="eyebrow">个人收藏</span>
        <h1>我的收藏夹</h1>
        <p>把喜欢的视频、图片和文章收进收藏夹，之后可以随时回来查看。</p>
      </div>
      <FavoriteEdit :edit-data="null" :close-event="getUserFavorites">
        <template #default="{ props }">
          <el-button v-bind="props" type="primary" class="create-button">
            <el-icon><Plus /></el-icon>
            新建收藏夹
          </el-button>
        </template>
      </FavoriteEdit>
    </header>

    <section v-if="loadingFavorites" class="collection-loading" aria-live="polite">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在加载收藏夹</span>
    </section>

    <section v-else-if="!favoriteItems.length" class="collections-empty">
      <div class="empty-icon"><el-icon><Collection /></el-icon></div>
      <h2>还没有收藏夹</h2>
      <p>创建一个收藏夹，整理你喜欢的作品。</p>
      <FavoriteEdit :edit-data="null" :close-event="getUserFavorites">
        <template #default="{ props }">
          <el-button v-bind="props" type="primary" plain>
            <el-icon><Plus /></el-icon>
            创建第一个收藏夹
          </el-button>
        </template>
      </FavoriteEdit>
    </section>

    <template v-else>
      <section class="collection-section">
        <div class="section-heading">
          <div>
            <h2>收藏夹</h2>
            <span>{{ favoriteItems.length }} 个收藏夹</span>
          </div>
          <span class="section-hint">选择一个收藏夹查看内容</span>
        </div>

        <div class="collection-grid">
          <article
            v-for="(item, index) in favoriteItems"
            :key="item.id || index"
            :class="['collection-card', { active: currentFavoriteIndex === index }]"
          >
            <button class="collection-select" type="button" @click="currentFavoriteIndex = index">
              <span class="collection-icon"><el-icon><FolderOpened /></el-icon></span>
              <span class="collection-copy">
                <strong>{{ item.name || '未命名收藏夹' }}</strong>
                <small>{{ item.description || '收集喜欢的作品' }}</small>
              </span>
              <span v-if="currentFavoriteIndex === index" class="current-marker">正在查看</span>
            </button>
            <el-button text class="edit-button" aria-label="编辑收藏夹" @click.stop="showMenu(item)">
              <el-icon><EditPen /></el-icon>
            </el-button>
          </article>
        </div>
      </section>

      <FavoriteEdit :edit-data="editFavorite" :close-event="handleFavoriteClose" />

      <section class="saved-section">
        <div class="saved-heading">
          <div>
            <span class="eyebrow">当前收藏夹</span>
            <h2>{{ currentFavorite?.name || '收藏内容' }}</h2>
          </div>
          <span class="video-count">{{ videoList.length }} 个作品</span>
        </div>
        <VideoList
          :video-list="videoList"
          :loading="loadingVideos"
          :no-data-msg="currentFavorite ? '这个收藏夹还没有作品' : '还没有收藏夹'"
        />
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Collection, EditPen, FolderOpened, Loading, Plus } from '@element-plus/icons-vue'
import { apiGetFavorites } from '../../../apis/user/favorites'
import { apiGetVideoByFavoriteId } from '../../../apis/video'
import FavoriteEdit from '../../../components/favorite/edit.vue'
import VideoList from '../../../components/video/list.vue'

const favoriteItems = ref([])
const videoList = ref([])
const currentFavoriteIndex = ref(0)
const editFavorite = ref(null)
const loadingFavorites = ref(true)
const loadingVideos = ref(false)
const currentFavorite = computed(() => favoriteItems.value[currentFavoriteIndex.value] || null)

const getUserFavorites = async () => {
  loadingFavorites.value = true
  try {
    const { data } = await apiGetFavorites()
    if (!data?.success) throw new Error(data?.message || '收藏夹加载失败')
    favoriteItems.value = Array.isArray(data.data) ? data.data : []
    if (currentFavoriteIndex.value >= favoriteItems.value.length) currentFavoriteIndex.value = 0
  } catch (error) {
    favoriteItems.value = []
    videoList.value = []
    ElMessage.error(error?.response?.data?.message || error?.message || '收藏夹加载失败，请稍后重试')
  } finally {
    loadingFavorites.value = false
  }
}

const getFavoriteVideo = async () => {
  if (!currentFavorite.value) {
    videoList.value = []
    return
  }
  loadingVideos.value = true
  try {
    const { data } = await apiGetVideoByFavoriteId(currentFavorite.value.id)
    if (!data?.success) throw new Error(data?.message || '收藏内容加载失败')
    videoList.value = Array.isArray(data.data) ? data.data : []
  } catch (error) {
    videoList.value = []
    ElMessage.error(error?.response?.data?.message || error?.message || '收藏内容加载失败，请稍后重试')
  } finally {
    loadingVideos.value = false
  }
}

const showMenu = (item) => { editFavorite.value = item }
const handleFavoriteClose = () => {
  editFavorite.value = null
  getUserFavorites()
}

getUserFavorites()
watch(currentFavorite, getFavoriteVideo)
</script>

<style scoped>
.favorites-page { display: grid; gap: 26px; min-width: 0; padding: 6px 0 30px; color: #333; }
.favorites-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding: 4px 2px 18px; border-bottom: 1px solid #e9e9e9; }
.heading-copy { min-width: 0; }
.eyebrow { color: #ff2442; font-size: 10px; font-weight: 750; letter-spacing: 1.4px; }
.favorites-heading h1 { margin: 6px 0 5px; color: #333; font-size: 23px; font-weight: 750; }
.favorites-heading p { margin: 0; color: #999; font-size: 12px; line-height: 1.6; }
.create-button { min-height: 38px; flex: 0 0 auto; padding: 0 17px; border: 0; border-radius: 999px; color: #fff; background: #ff2442; font-weight: 650; }
.create-button:hover { color: #fff; background: #ff4b64; }
.collection-section, .saved-section { min-width: 0; }
.section-heading, .saved-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; margin-bottom: 13px; }
.section-heading h2, .saved-heading h2 { margin: 0; color: #333; font-size: 16px; font-weight: 700; }
.section-heading > div { display: flex; align-items: baseline; gap: 10px; }
.section-heading span, .section-hint { color: #999; font-size: 11px; }
.collection-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(245px, 100%), 1fr)); gap: 10px; }
.collection-card { position: relative; min-width: 0; overflow: hidden; background: #fff; border: 1px solid #ececec; border-radius: 12px; transition: background .15s ease, border-color .15s ease, transform .15s ease; }
.collection-card:hover { background: #fafafa; border-color: #ddd; transform: translateY(-1px); }
.collection-card.active { background: #fff7f8; border-color: #ffb3bf; box-shadow: inset 3px 0 #ff2442; }
.collection-select { display: flex; align-items: center; width: 100%; min-height: 82px; gap: 11px; padding: 13px 43px 13px 14px; color: inherit; background: transparent; border: 0; cursor: pointer; text-align: left; }
.collection-icon { display: grid; width: 42px; height: 42px; flex: 0 0 42px; place-items: center; color: #ff2442; background: #fff0f2; border-radius: 11px; font-size: 20px; }
.collection-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 5px; }
.collection-copy strong, .collection-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.collection-copy strong { color: #333; font-size: 13px; font-weight: 700; }
.collection-copy small { color: #999; font-size: 11px; }
.current-marker { position: absolute; right: 12px; bottom: 9px; color: #ff2442; font-size: 9px; }
.edit-button { position: absolute; top: 9px; right: 8px; width: 28px; height: 28px; color: #999; }
.edit-button:hover { color: #ff2442; background: #fff0f2; }
.saved-section { margin-top: 1px; }
.saved-heading { padding: 15px 0 13px; border-top: 1px solid #e9e9e9; }
.saved-heading > div { display: flex; flex-direction: column; gap: 5px; }
.saved-heading h2 { font-size: 19px; }
.video-count { padding: 6px 9px; color: #888; background: #f5f5f5; border-radius: 999px; font-size: 11px; white-space: nowrap; }
.collections-empty { display: flex; min-height: 290px; flex-direction: column; align-items: center; justify-content: center; padding: 25px; background: #fff; border: 1px dashed #ddd; border-radius: 14px; text-align: center; }
.empty-icon { display: grid; width: 62px; height: 62px; margin-bottom: 14px; place-items: center; color: #ff2442; background: #fff0f2; border-radius: 18px; font-size: 28px; }
.collections-empty h2 { margin: 0; color: #333; font-size: 17px; }
.collections-empty p { margin: 8px 0 17px; color: #999; font-size: 12px; }
.collection-loading { display: flex; min-height: 240px; align-items: center; justify-content: center; gap: 10px; color: #999; font-size: 13px; }
.collection-loading .el-icon { color: #ff2442; font-size: 19px; }
@media (max-width: 620px) {
  .favorites-page { gap: 21px; }
  .favorites-heading { align-items: flex-start; flex-direction: column; }
  .create-button { align-self: flex-start; }
  .section-hint { display: none; }
  .collection-grid { grid-template-columns: 1fr; }
  .saved-heading { align-items: flex-start; }
}
</style>
