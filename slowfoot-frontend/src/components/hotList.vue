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
.hot-list { padding: 20px 18px 15px; background: #171d25; border: 1px solid #252e3a; border-radius: 10px; }
.hot-list.compact { margin-top: 26px; padding: 0; background: transparent; border: 0; }
.hot-heading { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 14px; border-bottom: 1px solid #27313c; }
.hot-kicker { color: #687689; font-size: 9px; font-weight: 800; letter-spacing: 1.4px; }
.hot-heading h3 { margin: 5px 0 0; color: #edf1f6; font-size: 17px; }
.fire-icon { display: grid; place-items: center; width: 30px; height: 30px; color: #f2bd5c; background: #282a29; border-radius: 8px; }
.hot-items { padding-top: 5px; }
.hot-item { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: center; gap: 8px; width: 100%; min-height: 45px; padding: 0; color: #a8b2c0; background: transparent; border: 0; border-bottom: 1px solid #202934; cursor: pointer; text-align: left; }
.hot-item:last-child { border-bottom: 0; }
.hot-item:hover .rank-title { color: #f2bd5c; }
.rank-number { color: #697588; font-size: 11px; font-variant-numeric: tabular-nums; }
.top-rank { color: #f2bd5c; }
.rank-title { overflow: hidden; color: #c7ced8; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.hot-item small { color: #687689; font-size: 10px; }
.hot-empty { display: flex; min-height: 100px; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #697789; font-size: 11px; }
.hot-empty svg { width: 25px; height: 25px; color: #596879; }
</style>
