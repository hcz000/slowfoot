<template>
  <main class="notification-page">
    <section class="notification-card">
      <header class="notification-heading">
        <div>
          <span class="eyebrow">消息中心</span>
          <h1>通知</h1>
          <p v-if="unreadCount">你有 {{ unreadCount }} 条未读通知</p>
          <p v-else>查看点赞、评论、关注和审核结果</p>
        </div>
        <button class="mark-all-button" type="button" :disabled="!unreadCount || markingAll" @click="markAllRead">
          {{ markingAll ? '处理中…' : '全部已读' }}
        </button>
      </header>

      <div v-if="loading" class="notification-state">
        <i class="mdi mdi-loading mdi-spin" />
        <span>正在加载通知</span>
      </div>
      <div v-else-if="loadError" class="notification-state error-state">
        <i class="mdi mdi-cloud-alert-outline" />
        <strong>通知暂时加载失败</strong>
        <span>{{ loadError }}</span>
        <button type="button" @click="loadNotifications">重新加载</button>
      </div>
      <template v-else>
        <div v-if="actionError" class="action-error" role="status">{{ actionError }}</div>
        <div v-if="notifications.length" class="notification-list">
          <article
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.readState }"
            @click="openNotification(item)"
          >
            <div class="notification-icon" :class="`icon-${String(item.type || 'default').toLowerCase()}`">
              <i :class="iconFor(item.type)" />
            </div>
            <div class="notification-content">
              <div class="notification-title">
                <strong>{{ item.actorName || typeName(item.type) }}</strong>
                <span v-if="!item.readState" class="unread-marker">未读</span>
              </div>
              <p>{{ item.content || fallbackContent(item.type) }}</p>
              <small>{{ formatDate(item.gmtCreated) }}</small>
            </div>
            <button v-if="item.workId" class="open-work-button" type="button" @click.stop="openWork(item)">
              查看作品 <i class="mdi mdi-arrow-right" />
            </button>
          </article>
        </div>
        <div v-else-if="unreadCount" class="notification-state error-state">
          <i class="mdi mdi-bell-alert-outline" />
          <strong>有 {{ unreadCount }} 条未读通知，但列表暂时为空</strong>
          <span>通知列表和未读数暂时没有同步，请重新加载。</span>
          <button type="button" @click="loadNotifications">重新加载</button>
        </div>
        <div v-else class="notification-state empty-state">
          <i class="mdi mdi-bell-sleep-outline" />
          <strong>暂时没有通知</strong>
          <span>有人点赞、评论、关注你，或作品审核完成后会显示在这里。</span>
        </div>
      </template>

      <footer v-if="!loading && !loadError && pages > 1" class="notification-pagination">
        <button type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ pages }} 页</span>
        <button type="button" :disabled="page >= pages" @click="changePage(page + 1)">下一页</button>
      </footer>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore, useUserStore } from '../../stores'
import {
  apiGetNotifications,
  apiGetUnreadNotificationCount,
  apiMarkAllNotificationsRead,
  apiMarkNotificationRead
} from '../../apis/notification'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const notifications = ref([])
const unreadCount = computed(() => notificationStore.unreadCount)
const page = ref(1)
const pages = ref(0)
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const markingAll = ref(false)
const pendingReads = ref(new Set())

const formatDate = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString()
}

const typeName = (type) => ({
  LIKE: '作品获赞', FAVORITE: '作品被收藏', FOLLOW: '新的关注',
  COMMENT: '收到评论', REPLY: '收到回复', COMMENT_AUDIT: '评论审核结果', WORK_AUDIT: '作品审核结果'
})[type] || '系统通知'

const fallbackContent = (type) => ({
  LIKE: '有人赞了你的作品。', FAVORITE: '有人收藏了你的作品。', FOLLOW: '有人关注了你。',
  COMMENT: '有人评论了你的作品。', REPLY: '有人回复了你。',
  COMMENT_AUDIT: '你的评论审核状态已更新。', WORK_AUDIT: '你的作品审核状态已更新。'
})[type] || '你有一条新通知。'

const iconFor = (type) => ({
  LIKE: 'mdi mdi-heart-outline', FAVORITE: 'mdi mdi-bookmark-outline', FOLLOW: 'mdi mdi-account-plus-outline',
  COMMENT: 'mdi mdi-comment-text-outline', REPLY: 'mdi mdi-reply-outline',
  COMMENT_AUDIT: 'mdi mdi-comment-check-outline', WORK_AUDIT: 'mdi mdi-file-check-outline'
})[type] || 'mdi mdi-bell-outline'

const mergeRecentNotifications = (records) => {
  const byId = new Map()
  ;[...notificationStore.recentNotifications, ...records].forEach((item) => {
    if (item?.id !== undefined && item?.id !== null) byId.set(String(item.id), item)
  })
  return [...byId.values()]
    .sort((a, b) => new Date(b.gmtCreated || 0) - new Date(a.gmtCreated || 0))
    .slice(0, 15)
}

const extractPage = (response) => {
  const payload = response?.data
  const data = payload?.data
  const result = data?.records ?? data?.data?.records ?? (Array.isArray(data) ? data : null)
  if (!Array.isArray(result)) throw new Error('通知接口返回格式异常，请重新加载。')
  const totalPages = data?.pages ?? data?.data?.pages
    ?? (payload?.meta?.size ? Math.ceil(payload.meta.total / payload.meta.size) : 0)
  return { records: result, pages: Number(totalPages) || 0 }
}

const syncUnreadCount = async () => {
  try {
    const { data } = await apiGetUnreadNotificationCount()
    if (data?.success) notificationStore.setUnreadCount(data.data)
  } catch { /* keep the live count until the server is reachable */ }
}

const loadNotifications = async () => {
  const requestedPage = page.value
  loading.value = true
  loadError.value = ''
  actionError.value = ''
  try {
    const response = await apiGetNotifications(requestedPage)
    if (!response.data?.success) throw new Error(response.data?.message || '服务器没有返回通知列表')
    if (requestedPage !== page.value) return
    const result = extractPage(response)
    notifications.value = requestedPage === 1 ? mergeRecentNotifications(result.records) : result.records
    pages.value = result.pages
  } catch (error) {
    if (requestedPage === page.value) {
      notifications.value = []
      loadError.value = error?.response?.data?.message || error?.message || '请检查网络后重试。'
    }
  } finally {
    if (requestedPage === page.value) loading.value = false
  }
}

const changePage = (nextPage) => {
  if (nextPage < 1 || nextPage > pages.value || nextPage === page.value) return
  page.value = nextPage
  loadNotifications()
}

const markRead = async (item) => {
  if (item.readState || pendingReads.value.has(String(item.id))) return
  const id = String(item.id)
  pendingReads.value = new Set(pendingReads.value).add(id)
  try {
    const { data } = await apiMarkNotificationRead(item.id)
    if (!data?.success) throw new Error(data?.message || '标记已读失败')
    item.readState = true
    notificationStore.decrementUnreadCount()
    const recent = notificationStore.recentNotifications.find((entry) => String(entry.id) === id)
    if (recent) recent.readState = true
  } catch (error) {
    actionError.value = error?.response?.data?.message || error?.message || '标记已读失败，请重试。'
  } finally {
    const pending = new Set(pendingReads.value)
    pending.delete(id)
    pendingReads.value = pending
  }
}

const markAllRead = async () => {
  if (!unreadCount.value || markingAll.value) return
  markingAll.value = true
  try {
    const { data } = await apiMarkAllNotificationsRead()
    if (!data?.success) throw new Error(data?.message || '标记全部已读失败')
    notifications.value.forEach((item) => { item.readState = true })
    notificationStore.recentNotifications.forEach((item) => { item.readState = true })
    notificationStore.setUnreadCount(0)
    await syncUnreadCount()
  } catch (error) {
    actionError.value = error?.response?.data?.message || error?.message || '标记全部已读失败，请重试。'
  } finally {
    markingAll.value = false
  }
}

const openWork = async (item) => {
  await markRead(item)
  if (item.workId) router.push({ path: '/', query: { play: item.workId } })
}

const openNotification = (item) => {
  if (item.type === 'WORK_AUDIT') {
    markRead(item)
    router.push('/user/publish')
    return
  }
  if (item.workId) return openWork(item)
  markRead(item)
  if (item.actorId) router.push(`/user?lookId=${item.actorId}`)
}

watch(() => notificationStore.eventSequence, () => {
  if (page.value !== 1 || !notificationStore.recentNotifications.length) return
  notifications.value = mergeRecentNotifications(notifications.value)
})

watch(() => notificationStore.connectionSequence, () => {
  if (page.value !== 1) page.value = 1
  loadNotifications()
  syncUnreadCount()
})

onMounted(() => {
  if (!userStore.token) {
    router.replace('/')
    return
  }
  loadNotifications()
  syncUnreadCount()
})
</script>

<style scoped>
.notification-page { min-height: calc(100vh - 64px); padding: clamp(18px, 4vw, 42px); color: #333; background: #f7f7f7; box-sizing: border-box; }
.notification-card { width: min(900px, 100%); min-height: 460px; margin: 0 auto; overflow: hidden; background: #fff; border: 1px solid #ededed; border-radius: 16px; }
.notification-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 25px 28px 20px; border-bottom: 1px solid #f0f0f0; }
.notification-heading h1 { margin: 5px 0 4px; color: #333; font-size: 23px; }
.notification-heading p { margin: 0; color: #999; font-size: 12px; }
.eyebrow { color: #ff2442; font-size: 10px; font-weight: 750; letter-spacing: 1.4px; }
.mark-all-button { padding: 9px 14px; color: #ff2442; background: #fff0f2; border: 0; border-radius: 999px; cursor: pointer; font: inherit; font-size: 12px; }
.mark-all-button:hover:not(:disabled) { color: #fff; background: #ff2442; }
.mark-all-button:disabled { color: #aaa; cursor: default; opacity: .7; }
.notification-list { display: grid; }
.notification-item { display: flex; align-items: center; gap: 15px; min-height: 84px; padding: 15px 25px; border-bottom: 1px solid #f4f4f4; cursor: pointer; transition: background .15s ease; }
.notification-item:hover { background: #fafafa; }
.notification-item.unread { background: #fff8f9; }
.notification-icon { display: grid; width: 42px; height: 42px; flex: 0 0 42px; place-items: center; color: #777; background: #f5f5f5; border-radius: 12px; font-size: 20px; }
.notification-icon.icon-like, .notification-icon.icon-comment { color: #ff5470; background: #fff0f2; }
.notification-content { min-width: 0; flex: 1; }
.notification-title { display: flex; align-items: center; gap: 8px; }
.notification-title strong { overflow: hidden; color: #333; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.notification-content p { margin: 5px 0 4px; overflow: hidden; color: #777; font-size: 12px; line-height: 1.5; text-overflow: ellipsis; }
.notification-content small { color: #aaa; font-size: 10px; }
.unread-marker { padding: 3px 6px; color: #ff2442; background: #ffe3e8; border-radius: 4px; font-size: 9px; white-space: nowrap; }
.open-work-button { flex: 0 0 auto; padding: 8px 11px; color: #666; background: #fff; border: 1px solid #e5e5e5; border-radius: 999px; cursor: pointer; font: inherit; font-size: 11px; }
.open-work-button:hover { color: #ff2442; border-color: #ffadba; }
.notification-state { display: flex; min-height: 330px; flex-direction: column; align-items: center; justify-content: center; gap: 10px; padding: 24px; color: #999; text-align: center; }
.notification-state > i { color: #bbb; font-size: 36px; }
.notification-state strong { color: #555; font-size: 15px; }
.notification-state span { max-width: 440px; font-size: 12px; line-height: 1.6; }
.error-state > i { color: #e58b6c; }
.error-state button { margin-top: 6px; padding: 8px 12px; color: #ff2442; background: #fff0f2; border: 0; border-radius: 999px; cursor: pointer; }
.action-error { padding: 10px 25px; color: #d46c52; background: #fff3ef; border-bottom: 1px solid #f7ddd5; font-size: 12px; }
.notification-pagination { display: flex; align-items: center; justify-content: center; gap: 16px; padding: 16px; color: #999; font-size: 11px; }
.notification-pagination button { padding: 7px 11px; color: #666; background: #f6f6f6; border: 0; border-radius: 7px; cursor: pointer; }
.notification-pagination button:disabled { color: #bbb; cursor: default; }
@media (max-width: 640px) {
  .notification-page { min-height: calc(100dvh - 64px); padding: 12px; }
  .notification-card { min-height: calc(100dvh - 90px); }
  .notification-heading { padding: 19px 16px; }
  .notification-item { gap: 11px; padding: 13px 14px; }
  .action-error { padding: 10px 14px; }
  .notification-icon { width: 36px; height: 36px; flex-basis: 36px; font-size: 17px; }
  .open-work-button { padding: 7px 8px; font-size: 10px; }
}
</style>
