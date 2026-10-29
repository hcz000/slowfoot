<template>
  <main class="channel-page">
    <VideoList
      :video-list="videoList"
      :loading="loading"
      no-data-msg="这个频道暂时没有作品"
    />
  </main>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { apiVideoByClassfiy } from '../../apis/video'
import VideoList from '../../components/video/list.vue'

const route = useRoute()
const videoList = ref([])
const loading = ref(true)
let requestSequence = 0

const loadChannel = async () => {
  const sequence = ++requestSequence
  const categoryId = Number(route.params.classify)
  loading.value = true
  videoList.value = []

  try {
    const { data } = await apiVideoByClassfiy(categoryId, 1, 30)
    if (sequence !== requestSequence) return
    if (data.success) {
      videoList.value = Array.isArray(data.data) ? data.data : data.data?.records || []
    }
  } catch {
    if (sequence === requestSequence) videoList.value = []
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

onMounted(loadChannel)
watch(() => route.params.classify, loadChannel)
</script>

<style scoped>
.channel-page {
  width: min(1680px, 100%);
  min-height: calc(100vh - 72px);
  margin: 0 auto;
  padding: 28px 30px 52px;
  box-sizing: border-box;
}

@media (max-width: 760px) {
  .channel-page {
    min-height: calc(100vh - 64px);
    padding: 16px 14px 36px;
  }
}
</style>
