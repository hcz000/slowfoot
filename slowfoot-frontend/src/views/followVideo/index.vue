<template>
  <RecommendationFeed
    :video-list="workList"
    :loading="loading && !workList.length"
    :loading-more="loading && workList.length > 0"
    :error="error"
    @index-change="loadNearEnd"
    @retry="loadWorks"
  />
</template>

<script setup>
import { ref } from 'vue'
import { apiGetFollowVideo } from '../../apis/video.js'
import RecommendationFeed from '../../components/video/recommendationFeed.vue'

const workList = ref([])
const loading = ref(false)
const error = ref(false)
const hasMore = ref(true)

const loadWorks = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  error.value = false
  const lastTime = workList.value.length
    ? new Date(workList.value[workList.value.length - 1].gmtCreated).getTime()
    : Date.now()
  try {
    const { data } = await apiGetFollowVideo(lastTime)
    if (!data?.success) throw new Error(data?.message || '关注作品加载失败')
    const nextWorks = Array.isArray(data.data) ? data.data : []
    if (!nextWorks.length) hasMore.value = false
    const knownIds = new Set(workList.value.map((work) => work.id))
    const newWorks = nextWorks.filter((work) => !knownIds.has(work.id))
    if (nextWorks.length && !newWorks.length) hasMore.value = false
    workList.value = workList.value.concat(newWorks)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

const loadNearEnd = (index) => {
  if (workList.value.length - index <= 3) loadWorks()
}

loadWorks()
</script>
