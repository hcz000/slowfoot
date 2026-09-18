import request from './request'

export const apiGetNotifications = (page = 1, limit = 15) => {
    return request.get('/notification', {
        params: { page, limit }
    })
}

export const apiGetUnreadNotificationCount = () => request.get('/notification/unread/count')

export const apiMarkNotificationRead = (id) => request.put(`/notification/${id}/read`)

export const apiMarkAllNotificationsRead = () => request.put('/notification/read/all')
