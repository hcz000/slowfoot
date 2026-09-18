import request from './request'

export const apiGetWorkComments = (workId, page = 1, limit = 10) => {
    return request.get(`/index/comment/work/${workId}`, {
        params: { page, limit }
    })
}

export const apiCreateComment = (workId, content, parentId = null) => {
    return request.post('/comment', {
        workId,
        content,
        parentId
    })
}

export const apiDeleteComment = (commentId) => request.delete(`/comment/${commentId}`)
