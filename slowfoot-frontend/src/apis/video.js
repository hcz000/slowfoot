import request from './request'
import { apiUploadFile } from './file'

export const apiVideoGetToken = (filename) => request.get("/work/token", {
    params: { filename }
})

// The backend now returns an OSS presigned PUT URL.
export const apiVideoUpload = (file, callBack) => apiUploadFile(file, callBack)

export const apiVideoByClassfiy = (classfiyId, page = 1, limit = 15) => {
    if (classfiyId > 0) {
        return request.get(`/index/work/type/${classfiyId}`, {
            params: { page, limit }
        })
    }
    return apiVideoByHot()
}

export const apiVideoHotRank = () => request.get(`/index/work/hot/rank`)

export const apiVideoByHot = () => request.get(`/index/work/hot`)

export const apiVideoByPush = () => request.get("/index/pushWorks")

export const apiGetVideoByFavoriteId = (favoriteId = 0) => {
    return request.get(`/work/favorites/${favoriteId}`)
}

export const apiGetVideoByUser = (page = 1, limit = 5) => {
    return request.get(`/work/mine`, {
        params: { page, limit }
    })
}

export const apiGetVideoBySimilar = (labelNames, id) => {
    return request.get(`/index/work/similar`, {
        params: { labelNames, id }
    })
}

export const apiStarVideo = (workId, liked) => liked
    ? request.put(`/work/like/${workId}`)
    : request.delete(`/work/like/${workId}`)

export const apiGetHistoryVideo = (page = 1, limit = 15) => {
    return request.get(`/work/history`, {
        params: { page, limit }
    })
}

export const apiSearchVideo = (searchName, page = 1, limit = 10) => {
    return request.get(`/index/search`, {
        params: { searchName, page, limit }
    })
}

export const apiGetAuditQueueState = () => request.get(`/work/audit/queue/state`)

export const apiAddHistory = (workId) => request.post(`/work/history/${workId}`)

export const apiGetVideoById = (workId) => request.get(`/index/work/${workId}`)

export const apiSetUserVideoModel = (workId, watchTimeSeconds, watchSessionId) => {
    return request.post(`/customer/updateUserModel`, {
        workId,
        watchTimeSeconds,
        watchSessionId
    })
}

export const apiGetFollowVideo = (lastTime) => {
    return request.get(`/work/follow/feed`, {
        params: { lastTime: lastTime || Date.now() }
    })
}

export const apiInitFollowFeed = () => request.post("/work/init/follow/feed")

export const apiGetUserVideoById = (userId, page = 1, limit = 10) => {
    return request.get(`/index/work/user`, {
        params: { userId, page, limit }
    })
}

export const apiShareVideo = (workId) => request.post(`/index/share/${workId}`)
