<template>
  <article v-if="videoInfo" class="video-card" @click="$emit('click')">
    <div v-if="isText" class="cover-wrap text-cover">
      <span class="text-cover-label">TEXT NOTES</span>
      <span class="quote-mark">“</span>
      <p>{{ videoInfo.content || videoInfo.description || '打开阅读作品正文' }}</p>
      <span class="read-more">阅读全文 <i class="mdi mdi-arrow-right" /></span>
    </div>
    <div v-else class="cover-wrap">
      <el-image :src="coverUrl" class="video-cover" fit="cover">
        <template #error><div class="cover-fallback"><Picture /><span>{{ isImage ? '图片笔记' : '暂无封面' }}</span></div></template>
      </el-image>
      <div class="cover-shade" />
      <span v-if="isVideo" class="play-chip"><VideoPlay /></span>
      <div class="cover-footer">
        <span><View /> {{ formatCount(videoInfo.viewCount) }}</span>
        <span v-if="isVideo && videoInfo.duration" class="duration">{{ videoInfo.duration }}</span>
      </div>
    </div>

    <span class="work-type">{{ typeName }}</span>
    <span v-if="isImage && imageCount > 1" class="image-count"><CopyDocument /> {{ imageCount }}</span>

    <div class="video-card-body">
      <h3 class="video-title">{{ videoInfo.title || `未命名${typeName}` }}</h3>
      <div class="video-meta">
        <button v-if="videoInfo.user" class="creator" type="button" @click.stop="goUser">
          <el-avatar v-if="videoInfo.user.avatar" :size="22" :src="apiFileGet(videoInfo.user.avatar)" />
          <span v-else class="mini-avatar">{{ authorInitial }}</span>
          <span class="creator-name">{{ videoInfo.user.nickName || '创作者' }}</span>
        </button>
        <span v-else class="creator"><span class="mini-avatar">S</span><span>SlowFoot 用户</span></span>
        <span class="like-count"><Star /> {{ formatCount(videoInfo.likeCount) }}</span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument, Picture, Star, VideoPlay, View } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { apiFileGet } from '../../apis/file'
import { useUserStore } from '../../stores'

defineEmits(['click'])
const props = defineProps({ videoInfo: { type: Object, default: null } })
const router = useRouter()
const userStore = useUserStore()
const isVideo = computed(() => !props.videoInfo?.workType || props.videoInfo.workType === 'VIDEO')
const isImage = computed(() => props.videoInfo?.workType === 'IMAGE')
const isText = computed(() => props.videoInfo?.workType === 'TEXT')
const typeName = computed(() => isText.value ? '文字' : isImage.value ? '图片' : '视频')
const imageCount = computed(() => {
  const count = (props.videoInfo?.assets || []).filter((asset) => asset.assetType === 'IMAGE').length
  return count || (props.videoInfo?.primaryFileId || props.videoInfo?.coverFileId ? 1 : 0)
})
const authorInitial = computed(() => Array.from(props.videoInfo?.user?.nickName || 'S')[0]?.toUpperCase())
const coverUrl = computed(() => {
  const work = props.videoInfo
  if (work?.coverFileId) return apiFileGet(work.coverFileId)
  if (isImage.value && work?.primaryFileId) return apiFileGet(work.primaryFileId)
  const image = work?.assets?.find((asset) => asset.assetType === 'IMAGE')
  return image?.url || (image?.fileId ? apiFileGet(image.fileId) : '')
})
const formatCount = (value) => {
  const count = Number(value || 0)
  return count >= 10000 ? `${(count / 10000).toFixed(1)}万` : count.toLocaleString()
}
const goUser = () => {
  if (userStore.token && props.videoInfo?.user) router.push(`/user?lookId=${props.videoInfo.user.id}`)
}
</script>

<style scoped>
.video-card { position: relative; min-width: 0; overflow: hidden; background: #fff; border-radius: 13px; cursor: pointer; transition: transform .22s ease, box-shadow .22s ease; }
.video-card:hover { box-shadow: 0 12px 30px rgba(0, 0, 0, .09); transform: translateY(-4px); }
.cover-wrap { position: relative; aspect-ratio: 3 / 4; overflow: hidden; background: #eee; }
.video-cover { width: 100%; height: 100%; transition: transform .35s ease; }
.video-card:hover .video-cover { transform: scale(1.035); }
.cover-shade { position: absolute; inset: 65% 0 0; background: linear-gradient(transparent, rgba(0, 0, 0, .58)); pointer-events: none; }
.cover-fallback { display: flex; width: 100%; height: 100%; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: #aaa; background: #f0f0f0; font-size: 12px; }
.cover-fallback svg { width: 28px; height: 28px; }
.work-type { position: absolute; top: 10px; left: 10px; z-index: 2; padding: 4px 8px; color: #fff; background: rgba(0, 0, 0, .46); border: 1px solid rgba(255, 255, 255, .2); border-radius: 6px; font-size: 10px; backdrop-filter: blur(7px); }
.image-count { position: absolute; top: 10px; right: 10px; z-index: 2; display: flex; align-items: center; gap: 4px; padding: 4px 7px; color: #fff; background: rgba(0, 0, 0, .46); border-radius: 6px; font-size: 10px; backdrop-filter: blur(7px); }
.image-count svg { width: 12px; height: 12px; }
.text-cover { display: flex; box-sizing: border-box; flex-direction: column; padding: 25px 22px; color: #3d3734; background: linear-gradient(145deg, #fff7f3, #f7e9e5); }
.text-cover::after { position: absolute; right: -36px; bottom: -46px; width: 150px; height: 150px; content: ''; border: 1px solid rgba(183, 119, 105, .15); border-radius: 50%; box-shadow: 0 0 0 24px rgba(183, 119, 105, .04), 0 0 0 48px rgba(183, 119, 105, .025); }
.text-cover-label { margin-top: 24px; color: #b17c70; font-size: 9px; font-weight: 750; letter-spacing: 2px; }
.quote-mark { height: 50px; margin-top: 20px; color: #d8aaa0; font-family: Georgia, serif; font-size: 64px; line-height: 1; }
.text-cover p { display: -webkit-box; margin: 7px 0 0; overflow: hidden; font-size: 15px; line-height: 1.85; white-space: pre-wrap; -webkit-box-orient: vertical; -webkit-line-clamp: 7; }
.read-more { position: relative; z-index: 1; display: flex; align-items: center; gap: 5px; margin-top: auto; padding-top: 16px; color: #a66c60; font-size: 11px; }
.cover-footer { position: absolute; right: 11px; bottom: 10px; left: 11px; display: flex; align-items: center; justify-content: space-between; color: #fff; font-size: 11px; }
.cover-footer span { display: flex; align-items: center; gap: 4px; }
.duration { padding: 3px 6px; background: rgba(0, 0, 0, .4); border-radius: 5px; }
.play-chip { position: absolute; top: 50%; left: 50%; display: grid; width: 46px; height: 46px; place-items: center; color: #fff; background: rgba(0, 0, 0, .32); border: 1px solid rgba(255, 255, 255, .45); border-radius: 50%; opacity: .9; transform: translate(-50%, -50%); backdrop-filter: blur(5px); }
.play-chip svg { width: 22px; height: 22px; margin-left: 2px; }
.video-card-body { padding: 11px 12px 12px; }
.video-title { display: -webkit-box; min-height: 42px; margin: 0; overflow: hidden; color: #333; font-size: 14px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.video-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 11px; color: #999; font-size: 11px; }
.creator { display: flex; min-width: 0; align-items: center; gap: 6px; padding: 0; overflow: hidden; color: #888; background: transparent; border: 0; cursor: pointer; }
.creator-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.creator:hover { color: #ff2442; }
.mini-avatar { display: grid; width: 22px; height: 22px; flex: 0 0 22px; place-items: center; color: #fff; background: linear-gradient(135deg, #ff8b9d, #ff2442); border-radius: 50%; font-size: 10px; }
.like-count { display: flex; flex: 0 0 auto; align-items: center; gap: 4px; }
.video-meta :deep(svg) { width: 13px; height: 13px; }
@media (max-width: 600px) {
  .video-card { border-radius: 10px; }
  .video-card-body { padding: 9px 9px 10px; }
  .video-title { font-size: 13px; }
  .text-cover { padding: 20px 16px; }
  .text-cover p { font-size: 13px; -webkit-line-clamp: 6; }
}
</style>
