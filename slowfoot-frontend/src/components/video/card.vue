<template>
  <article v-if="videoInfo" class="video-card" @click="$emit('click')">
    <div class="cover-wrap">
      <el-image :src="coverUrl" class="video-cover" fit="cover">
        <template #error><div class="cover-fallback"><VideoCamera /></div></template>
      </el-image>
      <div class="cover-shade" />
      <div class="cover-footer">
        <span><View /> {{ formatCount(videoInfo.viewCount) }}</span>
        <span v-if="videoInfo.duration" class="duration">{{ videoInfo.duration }}</span>
      </div>
      <span class="play-chip"><VideoPlay /></span>
    </div>
    <div class="video-card-body">
      <h3 class="video-title">{{ videoInfo.title || '未命名视频' }}</h3>
      <div class="video-meta">
        <button v-if="videoInfo.user" type="button" @click.stop="goUser">{{ videoInfo.user.nickName || '创作者' }}</button>
        <span v-else>SlowFoot 用户</span>
        <span v-if="videoInfo.likeCount"><Star /> {{ formatCount(videoInfo.likeCount) }}</span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { Star, VideoCamera, VideoPlay, View } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { apiFileGet } from '../../apis/file'
import { useUserStore } from '../../stores'

defineEmits(['click'])
const props = defineProps({ videoInfo: { type: Object, default: null } })
const router = useRouter()
const userStore = useUserStore()
const coverUrl = computed(() => props.videoInfo?.coverFileId ? apiFileGet(props.videoInfo.coverFileId) : '')
const formatCount = (value) => {
  const count = Number(value || 0)
  return count > 9999 ? `${(count / 10000).toFixed(1)}w` : count
}
const goUser = () => {
  if (userStore.token && props.videoInfo?.user) router.push(`/user?lookId=${props.videoInfo.user.id}`)
}
</script>

<style scoped>
.video-card { min-width: 0; overflow: hidden; background: #171d25; border: 1px solid #252e3a; border-radius: 10px; cursor: pointer; transition: border-color .2s ease, transform .2s ease, box-shadow .2s ease; }
.video-card:hover { border-color: #536477; box-shadow: 0 12px 26px rgba(0, 0, 0, .22); transform: translateY(-3px); }
.cover-wrap { position: relative; aspect-ratio: 16 / 10; overflow: hidden; background: #222a33; }
.video-cover { width: 100%; height: 100%; transition: transform .3s ease; }
.video-card:hover .video-cover { transform: scale(1.04); }
.cover-shade { position: absolute; inset: 38% 0 0; background: linear-gradient(transparent, rgba(5, 8, 11, .8)); pointer-events: none; }
.cover-fallback { display: grid; place-items: center; width: 100%; height: 100%; color: #647285; }
.cover-footer { position: absolute; right: 10px; bottom: 9px; left: 10px; display: flex; align-items: center; justify-content: space-between; color: #e8edf3; font-size: 11px; }
.cover-footer span { display: flex; align-items: center; gap: 4px; }
.duration { padding: 3px 6px; color: #f5f7fa; background: rgba(10, 13, 17, .7); border-radius: 4px; }
.play-chip { position: absolute; top: 10px; right: 10px; display: grid; place-items: center; width: 26px; height: 26px; color: #101318; background: #f2bd5c; border-radius: 50%; opacity: 0; transition: opacity .2s ease; }
.video-card:hover .play-chip { opacity: 1; }
.video-card-body { padding: 12px 13px 13px; }
.video-title { display: -webkit-box; min-height: 38px; margin: 0; overflow: hidden; color: #ebeff4; font-size: 13px; font-weight: 650; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.video-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 10px; color: #778597; font-size: 11px; }
.video-meta button { max-width: 65%; padding: 0; overflow: hidden; color: #9ba8b8; background: transparent; border: 0; cursor: pointer; text-overflow: ellipsis; white-space: nowrap; }
.video-meta button:hover { color: #f2bd5c; }
.video-meta span { display: flex; align-items: center; gap: 3px; }
.video-meta :deep(svg) { width: 12px; height: 12px; }
</style>
