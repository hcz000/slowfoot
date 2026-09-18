<template>
  <el-card shadow="never">
    <el-skeleton v-if="isload" :rows="5" animated />
    <template v-else>
      <div v-for="item in items" :key="item.time">
        <div>{{ item.time }}</div>
        <VideoList :video-list="item.data" />
      </div>
    </template>
  </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue';
import { apiGetHistoryVideo } from '../../../apis/video';
import VideoList from '../../../components/video/list.vue';

const items = ref([])
const isload = ref(true)
const getHistoryVideos = () => {
  isload.value = true
  apiGetHistoryVideo().then(({ data }) => {
    if (!data.success) {
      items.value = []
      return
    }
    items.value = Object.entries(data.data || {}).map(([time, data]) => ({ time, data }))
  }).catch(() => {
    items.value = []
  }).finally(() => {
    isload.value = false
  })
}
onMounted(getHistoryVideos)

</script>
