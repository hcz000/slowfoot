<template>
  <el-card>
    <el-tabs v-model="tab" class="work-tabs">
      <el-tab-pane v-for="(item, index) in items" :key="item" :label="item" :name="String(index)">
        <UploadVideo v-if="index === 0" />
        <videoList v-else />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import UploadVideo from './upVideo.vue'
import videoList from './videoList.vue'

const tab = ref('0')
const items = ref(['上传稿件', '稿件管理'])
</script>
