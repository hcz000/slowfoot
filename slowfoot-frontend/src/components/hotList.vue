<template>
  <section :class="['hot-list', { compact }]">
    <div class="hot-heading">
      <div><span class="hot-kicker">TRENDING</span><h3>本周热门</h3></div>
      <span class="fire-icon"><TrendCharts /></span>
    </div>
    <div v-if="rankList.length" class="hot-items">
      <button v-for="(rank, n) in rankList" :key="rank.workId" class="hot-item" type="button" @click="playRank(rank.workId, n)">
        <span :class="['rank-number', { 'top-rank': n < 3 }]">{{ String(n + 1).padStart(2, '0') }}</span>
        <span class="rank-title">{{ rank.title }}</span>
        <small>{{ rank.hotFormat || '热门' }}</small>
      </button>
    </div>
    <div v-else class="hot-empty"><TrendCharts /><span>热门内容正在加载</span></div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { apiVideoHotRank } from '../apis/video'

defineProps({ compact: { type: Boolean, default: false } })
const router = useRouter()
const rankList = ref([])
const playRank = (workId, index) => router.push(`/?play=${workId}&n=${index}`)
apiVideoHotRank().then(({ data }) => {
  if (data.success) rankList.value = (data.data || []).slice(0, 6)
})
</script>

<style scoped>
.hot-list { padding: 19px 17px 13px; background: #fff; border: 1px solid #f0f0f0; border-radius: 13px; }
.hot-list.compact { margin-top: 26px; padding: 0; background: transparent; border: 0; }
.hot-heading { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 13px; border-bottom: 1px solid #f0f0f0; }
.hot-kicker { color: #aaa; font-size: 9px; font-weight: 750; letter-spacing: 1.4px; }
.hot-heading h3 { margin: 5px 0 0; color: #333; font-size: 16px; }
.fire-icon { display: grid; place-items: center; width: 30px; height: 30px; color: #ff2442; background: #fff0f2; border-radius: 9px; }
.hot-items { padding-top: 5px; }
.hot-item { display: grid; grid-template-columns: 25px minmax(0, 1fr) auto; align-items: center; gap: 8px; width: 100%; min-height: 44px; padding: 0; color: #888; background: transparent; border: 0; border-bottom: 1px solid #f4f4f4; cursor: pointer; text-align: left; }
.hot-item:last-child { border-bottom: 0; }
.hot-item:hover .rank-title { color: #ff2442; }
.rank-number { color: #aaa; font-size: 11px; font-variant-numeric: tabular-nums; }
.top-rank { color: #ff2442; font-weight: 700; }
.rank-title { overflow: hidden; color: #555; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.hot-item small { color: #aaa; font-size: 10px; }
.hot-empty { display: flex; min-height: 100px; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #aaa; font-size: 11px; }
.hot-empty svg { width: 25px; height: 25px; color: #ccc; }
</style>
