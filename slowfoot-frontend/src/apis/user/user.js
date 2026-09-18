import request from '../request'
import { apiUploadFile } from '../file'

export const apiGetUserInfo = (userId = "") => {
    return request.get(userId ? `/customer/getInfo/${userId}` : "/customer/getInfo")
}

export const apiAvatarGetToken = (filename) => {
    return request.get("/customer/avatar/token", {
        params: { filename }
    })
}

// Avatar files use the same OSS upload and file registration flow as other files.
export const apiUploadAvatar = (file, callBack) => apiUploadFile(file, callBack)

export const apiChangeUserInfo = (info) => request.put(`/customer`, info)

export const apiGetUserSearchHistory = () => request.get(`/index/search/history`)

export const apiClearUserSearchHistory = () => request.delete(`/index/search/history`)
