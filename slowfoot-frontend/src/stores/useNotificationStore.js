import { defineStore } from 'pinia'

export default defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    recentNotifications: [],
    eventSequence: 0,
    connectionSequence: 0
  }),
  actions: {
    setUnreadCount(value) {
      const count = Number(value)
      if (Number.isFinite(count) && count >= 0) this.unreadCount = count
    },
    decrementUnreadCount() {
      this.unreadCount = Math.max(0, this.unreadCount - 1)
    },
    markReconnected() {
      this.connectionSequence += 1
    },
    receiveEvent(payload) {
      this.setUnreadCount(payload?.unreadCount)

      const notification = payload?.notification
      if (notification && notification.id !== undefined && notification.id !== null) {
        const normalized = { readState: false, ...notification }
        this.recentNotifications = [
          normalized,
          ...this.recentNotifications.filter((item) => String(item.id) !== String(normalized.id))
        ].slice(0, 20)
      }

      this.eventSequence += 1
    },
    reset() {
      this.unreadCount = 0
      this.recentNotifications = []
      this.eventSequence += 1
    }
  }
})
