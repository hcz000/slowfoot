<template>
  <main class="work-reader" :class="{ 'reader-image': isImage }">
    <header class="reader-header">
      <div class="reader-heading">
        <span class="reader-type">{{ isImage ? '图片笔记' : '文字笔记' }}</span>
        <h1>{{ work.title || '未命名作品' }}</h1>
        <p v-if="work.description">{{ work.description }}</p>
      </div>
      <button class="reader-close" type="button" aria-label="关闭作品" @click="$emit('close')">
        <el-icon><Close /></el-icon>
      </button>
    </header>
    <WorkPager class="reader-pager" :work="work" :show-meta="false" />
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { Close } from '@element-plus/icons-vue'
import WorkPager from './WorkPager.vue'

defineEmits(['close'])
const props = defineProps({ work: { type: Object, required: true } })
const isImage = computed(() => props.work?.workType === 'IMAGE')
</script>

<style scoped>
.work-reader { display: flex; width: 100%; height: 100%; min-height: 0; flex-direction: column; color: #333; background: #fff; }
.reader-header { display: flex; flex: 0 0 auto; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 20px clamp(20px, 4vw, 48px) 17px; background: rgba(255, 255, 255, .96); border-bottom: 1px solid #f0f0f0; }
.reader-heading { min-width: 0; }
.reader-type { color: #ff2442; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.reader-heading h1 { margin: 7px 0 4px; color: #222; font-size: clamp(18px, 2.4vw, 25px); line-height: 1.4; }
.reader-heading p { display: -webkit-box; margin: 0; overflow: hidden; color: #888; font-size: 13px; line-height: 1.6; white-space: pre-wrap; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.reader-close { display: grid; width: 36px; height: 36px; flex: 0 0 36px; padding: 0; place-items: center; color: #555; background: #f6f6f6; border: 0; border-radius: 50%; cursor: pointer; font-size: 19px; }
.reader-close:hover { color: #ff2442; background: #fff0f2; }
.reader-pager { min-height: 0; flex: 1; }
.reader-image .reader-pager { background: #151515; }
@media (max-width: 640px) {
  .reader-header { padding: 14px 16px; }
  .reader-heading h1 { margin-top: 4px; font-size: 17px; }
  .reader-heading p { font-size: 12px; }
  .reader-close { width: 32px; height: 32px; flex-basis: 32px; }
}
</style>
