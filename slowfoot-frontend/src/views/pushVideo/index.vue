<template>
  <RecommendationFeed
    :video-list="videoList"
    :loading="loading"
    :loading-more="loadingMore"
    :error="loadError"
    @index-change="onIndexChange"
    @retry="loadVideos(true)"
  />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { apiVideoByPush } from '../../apis/video'
import RecommendationFeed from '../../components/video/recommendationFeed.vue'

const videoList = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const loadError = ref(false)
const hasMore = ref(true)
let requestPending = false

const loadVideos = async (retry = false) => {
  if (requestPending || (!hasMore.value && !retry)) return
  if (retry) {
    hasMore.value = true
    loadError.value = false
  }
  requestPending = true
  if (videoList.value.length) loadingMore.value = true
  else loading.value = true

  let shouldFillInitialFeed = false
  try {
    const { data } = await apiVideoByPush()
    if (!data.success) {
      loadError.value = true
      return
    }
    const items = Array.isArray(data.data) ? data.data : data.data?.records || []
    const knownIds = new Set(videoList.value.map((item) => item.id))
    const freshItems = items.filter((item) => item && !knownIds.has(item.id))
    videoList.value = videoList.value.concat(freshItems)
    if (!freshItems.length) hasMore.value = false
    shouldFillInitialFeed = hasMore.value && videoList.value.length > 0 && videoList.value.length < 4
  } catch {
    loadError.value = true
  } finally {
    requestPending = false
    loading.value = false
    loadingMore.value = false
  }

  if (shouldFillInitialFeed) loadVideos()
}

const onIndexChange = (index) => {
  if (hasMore.value && videoList.value.length - index <= 3) loadVideos()
}

onMounted(() => loadVideos())
</script>
