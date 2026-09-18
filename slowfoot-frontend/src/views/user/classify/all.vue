<template>
  <div class="classify-panel">
    <div class="classify-heading">
      <strong>全部分类</strong>
      <span>所有视频分类</span>
    </div>
    <el-divider />
    <div class="classify-tags">
      <el-tag v-for="(item, index) in classifyData" :key="index" size="large" class="classify-tag" @click="closeEvent(item, true)">
        <i :class="['mdi', item.icon || 'mdi-file-document-alert-outline']" />
        {{ item.name }}
        <i class="mdi mdi-plus-circle-outline" />
      </el-tag>
    </div>
  </div>
</template>

<script setup>
defineProps({
  classifyData: { type: Array, default: () => [] },
  closeEvent: { type: Function, default: () => {} }
})
</script>

<style scoped>
.classify-heading { display: flex; align-items: baseline; gap: 12px; }
.classify-heading span { color: var(--text-secondary, #aeb4c0); font-size: 13px; }
.classify-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.classify-tag { cursor: pointer; }
.classify-tag .mdi { margin: 0 4px; }
</style>
