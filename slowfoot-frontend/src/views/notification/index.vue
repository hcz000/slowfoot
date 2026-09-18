<template>
  <el-container class="notification-page">
    <el-card shadow="never" class="notification-card">
      <div class="notification-heading">
        <span>通知</span>
        <span class="heading-spacer" />
        <el-button link :disabled="!unreadCount" @click="markAllRead">全部已读</el-button>
      </div>
      <el-divider />
      <div v-if="notifications.length">
        <div v-for="item in notifications" :key="item.id" class="notification-item"
          :class="{ unread: !item.readState }" @click="markRead(item)">
          <i class="mdi mdi-bell-outline notification-icon" />
          <div class="notification-content">
            <strong>{{ item.content || '你有一条新通知' }}</strong>
            <small>{{ formatDate(item.gmtCreated) }}</small>
          </div>
          <el-tag v-if="!item.readState" type="primary" size="small">未读</el-tag>
        </div>
      </div>
      <div v-else class="empty-state">暂无通知</div>
      <el-pagination v-if="pages > 1" v-model:current-page="page" layout="prev, pager, next" :page-count="pages"
        background @current-change="loadNotifications" />
    </el-card>
  </el-container>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores'
import {
  apiGetNotifications,
  apiGetUnreadNotificationCount,
  apiMarkAllNotificationsRead,
  apiMarkNotificationRead
} from '../../apis/notification'

const router = useRouter()
const userStore = useUserStore()
const notifications = ref([])
const unreadCount = ref(0)
const page = ref(1)
const pages = ref(0)
const formatDate = (value) => value ? new Date(value).toLocaleString() : ''

const loadUnreadCount = () => apiGetUnreadNotificationCount().then(({ data }) => {
  if (data.success) unreadCount.value = data.data || 0
})

const loadNotifications = () => apiGetNotifications(page.value).then(({ data }) => {
  if (!data.success) return
  notifications.value = data.data?.records || []
  pages.value = data.data?.pages || 0
})

const markRead = (item) => {
  if (item.readState) return
  apiMarkNotificationRead(item.id).then(({ data }) => {
    if (!data.success) return
    item.readState = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  })
}

const markAllRead = () => apiMarkAllNotificationsRead().then(({ data }) => {
  if (!data.success) return
  notifications.value.forEach((item) => { item.readState = true })
  unreadCount.value = 0
})

onMounted(() => {
  if (!userStore.token) {
    router.replace('/')
    return
  }
  loadNotifications()
  loadUnreadCount()
})
</script>

<style scoped>
.notification-page { padding: 16px; }
.notification-card { width: min(900px, 100%); margin: 0 auto; background: #36393f; border: 0; }
.notification-heading { display: flex; align-items: center; padding: 4px 0; font-size: 18px; }
.heading-spacer { flex: 1; }
.notification-item { display: flex; align-items: center; gap: 12px; padding: 14px 16px; cursor: pointer; }
.notification-item:hover, .notification-item.unread { background: rgba(33, 150, 243, .08); }
.notification-icon { color: #409eff; font-size: 20px; }
.notification-content { flex: 1; min-width: 0; }
.notification-content strong, .notification-content small { display: block; }
.notification-content small { margin-top: 4px; color: #a3a6ad; }
.empty-state { padding: 48px 16px; color: #a3a6ad; text-align: center; }
</style>
