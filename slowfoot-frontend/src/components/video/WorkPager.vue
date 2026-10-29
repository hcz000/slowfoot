<template>
  <section
    ref="root"
    class="work-pager"
    :class="isImage ? 'pager-image' : 'pager-text'"
    :tabindex="pageCount > 1 ? 0 : -1"
    :aria-label="isImage ? '图片作品，左右滑动切换图片' : '文字作品，左右滑动切换页面'"
    @keydown.left.prevent="move(-1)"
    @keydown.right.prevent="move(1)"
  >
    <div v-if="isImage" ref="track" class="pager-track" @scroll.passive="onScroll">
      <figure v-for="(image, index) in images" :key="image.id || image.fileId || index" class="pager-page image-page">
        <img :src="getAssetUrl(image)" :alt="`${work.title || '作品图片'} ${index + 1}`" @dragstart.prevent />
      </figure>
      <div v-if="!images.length" class="pager-page image-empty">暂无图片</div>
    </div>

    <div v-else ref="track" class="pager-track" @scroll.passive="onScroll">
      <article v-for="(content, index) in textPages" :key="index" class="pager-page text-page">
        <header v-if="index === 0 && showMeta" class="text-meta">
          <span class="text-kind">文字笔记</span>
          <h2>{{ work.title || '未命名文章' }}</h2>
          <p v-if="work.description" class="text-description">{{ work.description }}</p>
          <span class="text-author">{{ work.user?.nickName || 'SlowFoot 创作者' }}</span>
        </header>
        <p class="text-content">{{ content }}</p>
      </article>
    </div>

    <!-- 使用与正式页面完全相同的排版测量，按实际剩余高度分页，而不是固定字数。 -->
    <div v-if="!isImage" class="measure-layer" aria-hidden="true">
      <article ref="measurePage" class="pager-page text-page">
        <header v-if="measureFirstPage && showMeta" class="text-meta">
          <span class="text-kind">文字笔记</span>
          <h2>{{ work.title || '未命名文章' }}</h2>
          <p v-if="work.description" class="text-description">{{ work.description }}</p>
          <span class="text-author">{{ work.user?.nickName || 'SlowFoot 创作者' }}</span>
        </header>
        <p class="text-content">{{ measureText }}</p>
      </article>
    </div>

    <span v-if="pageCount > 1" class="pager-count">{{ page + 1 }} / {{ pageCount }}</span>
    <button v-if="page > 0" class="pager-arrow pager-previous" type="button" aria-label="上一页" @click="move(-1)">
      <el-icon><ArrowLeft /></el-icon>
    </button>
    <button v-if="page < pageCount - 1" class="pager-arrow pager-next" type="button" aria-label="下一页" @click="move(1)">
      <el-icon><ArrowRight /></el-icon>
    </button>
    <div v-if="pageCount > 1 && pageCount <= 8" class="pager-dots" aria-label="页面导航">
      <button
        v-for="index in pageCount"
        :key="index"
        :class="{ active: page === index - 1 }"
        type="button"
        :aria-label="`第 ${index} 页`"
        :aria-current="page === index - 1 ? 'page' : undefined"
        @click="goTo(index - 1)"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { apiFileGet } from '../../apis/file'

const props = defineProps({
  work: { type: Object, required: true },
  showMeta: { type: Boolean, default: true }
})

const root = ref(null)
const track = ref(null)
const measurePage = ref(null)
const measureText = ref('')
const measureFirstPage = ref(true)
const textPages = ref([''])
const page = ref(0)
const isImage = computed(() => props.work?.workType === 'IMAGE')
const sourceText = computed(() => String(props.work?.content || props.work?.description || '').trim() || '这篇文章还没有正文。')
const images = computed(() => {
  const assets = (props.work?.assets || []).filter((asset) => asset.assetType === 'IMAGE')
  if (assets.length) return assets
  const fileId = props.work?.coverFileId || props.work?.primaryFileId
  return fileId ? [{ fileId }] : []
})
const pageCount = computed(() => isImage.value ? Math.max(images.value.length, 1) : textPages.value.length)
const getAssetUrl = (asset) => asset?.url || (asset?.fileId ? apiFileGet(asset.fileId) : '')

let paginationSequence = 0
let resizeObserver
let resizeTimer

const resetScroll = async () => {
  page.value = 0
  await nextTick()
  track.value?.scrollTo({ left: 0 })
}

const paginateText = async () => {
  const sequence = ++paginationSequence
  if (isImage.value) return

  await nextTick()
  const measurement = measurePage.value
  if (!measurement?.clientHeight || !root.value?.clientWidth) return

  const characters = Array.from(sourceText.value)
  const pages = []
  let start = 0
  while (start < characters.length) {
    measureFirstPage.value = pages.length === 0
    let low = 1
    let high = characters.length - start
    let fitted = 1

    // 二分查找当前页能容纳的字符数，标题和简介也会占用第一页高度。
    while (low <= high) {
      const middle = Math.floor((low + high) / 2)
      measureText.value = characters.slice(start, start + middle).join('')
      await nextTick()
      if (sequence !== paginationSequence) return
      if (measurePage.value.scrollHeight <= measurePage.value.clientHeight + 1) {
        fitted = middle
        low = middle + 1
      } else {
        high = middle - 1
      }
    }

    pages.push(characters.slice(start, start + fitted).join(''))
    start += fitted
  }

  if (sequence !== paginationSequence) return
  textPages.value = pages.length ? pages : ['']
  await resetScroll()
}

const onScroll = (event) => {
  const element = event.currentTarget
  if (!element.clientWidth) return
  page.value = Math.max(0, Math.min(pageCount.value - 1, Math.round(element.scrollLeft / element.clientWidth)))
}
const move = (direction) => {
  track.value?.scrollBy({ left: direction * track.value.clientWidth, behavior: 'smooth' })
}
const goTo = (index) => {
  track.value?.scrollTo({ left: index * track.value.clientWidth, behavior: 'smooth' })
}

watch([() => props.work?.id, sourceText, () => props.showMeta, isImage], paginateText, { immediate: true, flush: 'post' })
watch(() => props.work?.id, resetScroll)

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      clearTimeout(resizeTimer)
      resizeTimer = setTimeout(paginateText, 120)
    })
    if (root.value) resizeObserver.observe(root.value)
  }
  document.fonts?.ready?.then(paginateText)
})

onUnmounted(() => {
  paginationSequence += 1
  clearTimeout(resizeTimer)
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.work-pager { position: relative; width: 100%; height: 100%; min-height: 0; overflow: hidden; outline: none; }
.pager-track { display: flex; width: 100%; height: 100%; overflow-x: auto; overflow-y: hidden; overscroll-behavior-x: contain; scroll-snap-type: x mandatory; scrollbar-width: none; }
.pager-track::-webkit-scrollbar { display: none; }
.pager-page { width: 100%; height: 100%; min-width: 100%; flex: 0 0 100%; margin: 0; overflow: hidden; scroll-snap-align: start; scroll-snap-stop: always; }
.pager-image { background: #111; }
.image-page { display: grid; place-items: center; }
.image-page img { display: block; width: 100%; height: 100%; object-fit: contain; user-select: none; }
.image-empty { display: grid; place-items: center; color: #999; background: #f5f5f5; }
.pager-text { color: #333; background: #fff; }
.text-page { padding: clamp(30px, 5vw, 58px) clamp(25px, 5vw, 54px) 68px; background: #fff; }
.text-meta { flex: 0 0 auto; margin-bottom: 23px; }
.text-kind { display: inline-flex; padding: 4px 9px; color: #ff2442; background: #fff0f2; border-radius: 5px; font-size: 11px; font-weight: 650; letter-spacing: 1px; }
.text-meta h2 { display: -webkit-box; margin: 15px 0 9px; overflow: hidden; color: #222; font-size: clamp(21px, 2.4vw, 29px); line-height: 1.4; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.text-description { display: -webkit-box; margin: 0 0 13px; overflow: hidden; color: #777; font-size: 13px; line-height: 1.65; white-space: pre-wrap; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.text-author { color: #999; font-size: 12px; }
.text-content { margin: 0; color: #3c3c3c; font-size: clamp(15px, 1.5vw, 18px); line-height: 1.9; letter-spacing: .02em; white-space: pre-wrap; overflow-wrap: anywhere; }
.measure-layer { position: absolute; inset: 0; z-index: -1; overflow: hidden; visibility: hidden; pointer-events: none; }
.pager-count { position: absolute; top: 17px; right: 17px; z-index: 3; min-width: 42px; padding: 5px 9px; color: #777; background: rgba(255, 255, 255, .86); border: 1px solid rgba(0, 0, 0, .06); border-radius: 999px; font-size: 11px; text-align: center; backdrop-filter: blur(8px); }
.pager-image .pager-count { color: #fff; background: rgba(0, 0, 0, .48); border-color: rgba(255, 255, 255, .14); }
.pager-arrow { position: absolute; top: 50%; z-index: 4; display: grid; width: 36px; height: 36px; padding: 0; place-items: center; color: #333; background: rgba(255, 255, 255, .9); border: 1px solid rgba(0, 0, 0, .07); border-radius: 50%; box-shadow: 0 4px 16px rgba(0, 0, 0, .1); cursor: pointer; opacity: 0; transform: translateY(-50%); transition: opacity .18s ease, background .18s ease; backdrop-filter: blur(8px); }
.work-pager:hover .pager-arrow, .work-pager:focus-within .pager-arrow { opacity: 1; }
.pager-arrow:hover { color: #ff2442; background: #fff; }
.pager-previous { left: 13px; }
.pager-next { right: 13px; }
.pager-dots { position: absolute; bottom: 19px; left: 50%; z-index: 4; display: flex; align-items: center; gap: 6px; padding: 8px 10px; background: rgba(255, 255, 255, .78); border-radius: 999px; transform: translateX(-50%); backdrop-filter: blur(8px); }
.pager-dots button { width: 6px; height: 6px; padding: 0; background: #c7c7c7; border: 0; border-radius: 999px; cursor: pointer; transition: width .2s ease, background .2s ease; }
.pager-dots button.active { width: 18px; background: #ff2442; }
.pager-image .pager-dots { background: rgba(0, 0, 0, .36); }
.pager-image .pager-dots button { background: rgba(255, 255, 255, .5); }
.pager-image .pager-dots button.active { background: #fff; }
@media (hover: none), (max-width: 760px) {
  .pager-arrow { width: 32px; height: 32px; opacity: .86; }
  .pager-previous { left: 9px; }
  .pager-next { right: 9px; }
  .text-page { padding: 65px 25px 64px; }
  .text-meta h2 { font-size: 22px; }
  .text-content { font-size: 16px; }
}
</style>
