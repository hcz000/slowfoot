import { defineStore } from 'pinia'
import { apiUploadFile } from '../apis/file'
import { uploadMultipart, listPendingUploads } from '../apis/multipartUpload'

const MULTIPART_THRESHOLD = 5 * 1024 * 1024 // 5MB

/**
 * 全局上传队列 Store。
 * 将上传状态提升到 Pinia，切换页面后上传不中断、进度不丢失。
 */
export default defineStore('upload', {
    state: () => ({
        /** @type {Array<{id: string, file: File, title: string, description: string, progress: number, status: number, msg: string, primaryFileId: string|null, coverFileId: string|null, labelNames: string[], subscription: object|null}>} */
        videoUploads: [],
        currentVideoIndex: null
    }),

    getters: {
        currentVideo(state) {
            if (state.currentVideoIndex === null || state.currentVideoIndex < 0) return null
            return state.videoUploads[state.currentVideoIndex] || null
        },
        hasActiveUploads(state) {
            return state.videoUploads.some(u => u.status === 0)
        }
    },

    actions: {
        /** 添加文件并开始上传（自动判断单次PUT还是分片） */
        async startUpload(file) {
            const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
            const useMultipart = file.size >= MULTIPART_THRESHOLD
            const uploadInfo = {
                id,
                file,
                title: file.name,
                description: '',
                progress: 0,
                status: 0, // 0=上传中, 1=完成, -1=失败, 2=已暂停
                msg: '',
                primaryFileId: null,
                coverFileId: null,
                labelNames: [],
                subscription: null,
                multipart: useMultipart
            }
            this.videoUploads.push(uploadInfo)
            if (this.videoUploads.length === 1) this.currentVideoIndex = 0

            if (useMultipart) {
                this._startMultipartUpload(id, file)
            } else {
                this._startSimpleUpload(id, file)
            }
        },

        /** 恢复之前中断的分片上传 */
        resumeUpload(file) {
            const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
            const uploadInfo = {
                id,
                file,
                title: file.name,
                description: '',
                progress: 0,
                status: 0,
                msg: '',
                primaryFileId: null,
                coverFileId: null,
                labelNames: [],
                subscription: null,
                multipart: true
            }
            this.videoUploads.push(uploadInfo)
            if (this.videoUploads.length === 1) this.currentVideoIndex = 0
            this._startMultipartUpload(id, file)
        },

        /** 暂停分片上传 */
        pauseUpload(index) {
            const item = this.videoUploads[index]
            if (!item || item.status !== 0 || !item.subscription?.pause) return
            item.subscription.pause()
            item.status = 2
        },

        /** 继续分片上传 */
        resumeByIndex(index) {
            const item = this.videoUploads[index]
            if (!item || item.status !== 2) return
            item.status = 0
            item.progress = 0
            this._startMultipartUpload(item.id, item.file)
        },

        /** 检查是否有可恢复的上传记录 */
        checkResumableUploads() {
            return listPendingUploads()
        },

        /** 移除某个上传项（取消上传） */
        removeUpload(index) {
            const item = this.videoUploads[index]
            if (!item) return
            if (item.status === 0 && item.subscription?.unsubscribe) {
                item.subscription.unsubscribe()
            } else if (item.status === 0 && item.subscription?.abort) {
                item.subscription.abort()
            } else if (item.status === 2 && item.subscription?.abort) {
                item.subscription.abort()
            }
            this.videoUploads.splice(index, 1)
            if (this.currentVideoIndex >= this.videoUploads.length) {
                this.currentVideoIndex = this.videoUploads.length ? this.videoUploads.length - 1 : null
            }
        },

        /** 选中某个上传项 */
        selectUpload(index) {
            this.currentVideoIndex = index
        },

        /** 更新上传项的编辑字段（标题、简介、标签等） */
        updateItem(index, fields) {
            const item = this.videoUploads[index]
            if (item) Object.assign(item, fields)
        },

        /** 发布成功后清除已完成的项 */
        removeCompleted() {
            this.videoUploads = this.videoUploads.filter(u => u.status === 0)
            if (this.currentVideoIndex >= this.videoUploads.length) {
                this.currentVideoIndex = this.videoUploads.length ? this.videoUploads.length - 1 : null
            }
        },

        _findItem(id) {
            return this.videoUploads.find(u => u.id === id)
        },

        /** 内部：单次 PUT 上传（小文件） */
        async _startSimpleUpload(id, file) {
            const item = this._findItem(id)
            if (!item) return
            item.subscription = await apiUploadFile(file, {
                next: (event) => {
                    const it = this._findItem(id)
                    if (it) it.progress = event.total.percent
                },
                error: (error) => {
                    const it = this._findItem(id)
                    if (it) {
                        it.status = -1
                        it.msg = `上传失败: ${error}`
                    }
                },
                complete: (event, result) => {
                    const it = this._findItem(id)
                    if (!it) return
                    if (!result.success) {
                        it.status = -1
                        it.msg = result.message
                        return
                    }
                    it.status = 1
                    it.progress = 100
                    it.primaryFileId = result.data
                    it.coverFileId = null
                }
            })
        },

        /** 内部：分片上传（大文件，支持断点续传） */
        _startMultipartUpload(id, file) {
            const item = this._findItem(id)
            if (!item) return
            item.subscription = uploadMultipart(file, {
                onProgress: (percent) => {
                    const it = this._findItem(id)
                    if (it) it.progress = percent
                },
                onError: (error) => {
                    const it = this._findItem(id)
                    if (it) {
                        it.status = -1
                        it.msg = `上传失败: ${error.message || error}`
                    }
                },
                onComplete: (fileId) => {
                    const it = this._findItem(id)
                    if (!it) return
                    it.status = 1
                    it.progress = 100
                    it.primaryFileId = fileId
                    it.coverFileId = null
                }
            })
        }
    }
})
