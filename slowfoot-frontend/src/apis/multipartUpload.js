import request from './request'

const PART_SIZE = 5 * 1024 * 1024 // 5MB
const CONCURRENCY = 3
const STORAGE_PREFIX = 'upload:'

/**
 * 构建 localStorage 断点续传 key。
 * 使用文件名 + 大小 + 最后修改时间唯一标识一个文件。
 */
function buildResumeKey(file) {
    return `${STORAGE_PREFIX}${file.name}:${file.size}:${file.lastModified}`
}

function loadState(key) {
    try {
        const raw = localStorage.getItem(key)
        return raw ? JSON.parse(raw) : null
    } catch {
        return null
    }
}

function saveState(key, state) {
    localStorage.setItem(key, JSON.stringify(state))
}

function clearState(key) {
    localStorage.removeItem(key)
}

/** 列出所有未完成的分片上传记录 */
export function listPendingUploads() {
    const results = []
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (!key || !key.startsWith(STORAGE_PREFIX)) continue
        try {
            const state = JSON.parse(localStorage.getItem(key))
            if (state && state.uploadId && state.objectKey) {
                results.push({ key, ...state })
            }
        } catch { /* skip malformed */ }
    }
    return results
}

// --- API 调用 ---

async function apiInitiateMultipart(file) {
    const { data } = await request.post('/file/multipart/initiate', null, {
        params: { type: file.type, filename: file.name }
    })
    if (!data.success) throw new Error(data.message || '初始化分片上传失败')
    return data.data // { uploadId, objectKey }
}

async function apiGetPartUrl(objectKey, uploadId, partNumber) {
    const { data } = await request.get('/file/multipart/partUrl', {
        params: { objectKey, uploadId, partNumber }
    })
    if (!data.success) throw new Error(data.message || '获取分片URL失败')
    return data.data // { partNumber, uploadUrl }
}

async function apiCompleteMultipart(objectKey, uploadId, parts) {
    const { data } = await request.post('/file/multipart/complete', {
        objectKey, uploadId, parts
    })
    if (!data.success) throw new Error(data.message || '完成分片上传失败')
    return data.data // fileId
}

async function apiAbortMultipart(objectKey, uploadId) {
    await request.post('/file/multipart/abort', null, {
        params: { objectKey, uploadId }
    }).catch(() => {})
}

// --- 单个分片上传 ---

function uploadPart(uploadUrl, blob, signal) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', uploadUrl)
        xhr.setRequestHeader('Content-Type', blob.type || 'application/octet-stream')

        xhr.addEventListener('load', () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                const etag = xhr.getResponseHeader('ETag')
                resolve(etag ? etag.replace(/"/g, '') : '')
            } else {
                reject(new Error(`分片上传失败 (${xhr.status})`))
            }
        })
        xhr.addEventListener('error', () => reject(new Error('分片网络错误')))
        xhr.addEventListener('abort', () => reject(new Error('分片已取消')))

        signal.addEventListener('abort', () => xhr.abort())
        xhr.send(blob)
    })
}

// --- 核心：分片上传 ---

/**
 * 执行分片上传，支持断点续传。
 * @param {File} file - 浏览器 File 对象
 * @param {object} options
 * @param {(percent: number) => void} options.onProgress - 进度回调 (0-100)
 * @param {(error: Error) => void} options.onError - 错误回调
 * @param {(fileId: number) => void} options.onComplete - 完成回调
 * @returns {{ abort: () => void }} 可用于取消上传的 controller
 */
export function uploadMultipart(file, { onProgress, onError, onComplete }) {
    const controller = new AbortController()
    const signal = controller.signal

    const run = async () => {
        const resumeKey = buildResumeKey(file)
        let state = loadState(resumeKey)

        // 初始化或恢复
        if (!state) {
            const { uploadId, objectKey } = await apiInitiateMultipart(file)
            const totalParts = Math.ceil(file.size / PART_SIZE)
            state = { uploadId, objectKey, totalParts, parts: [] }
            saveState(resumeKey, state)
        }

        // 已完成的分片号集合
        const completedParts = new Map(state.parts.map(p => [p.partNumber, p.eTag]))

        // 计算待上传分片
        const pendingParts = []
        for (let i = 1; i <= state.totalParts; i++) {
            if (!completedParts.has(i)) {
                pendingParts.push(i)
            }
        }

        // 上报初始进度
        const reportProgress = () => {
            const percent = (completedParts.size / state.totalParts) * 100
            onProgress?.(Math.min(percent, 99)) // 留 1% 给 complete 调用
        }
        reportProgress()

        // 并发上传
        let hasError = false
        const uploadNext = async () => {
            while (pendingParts.length > 0 && !hasError && !signal.aborted) {
                const partNumber = pendingParts.shift()
                const start = (partNumber - 1) * PART_SIZE
                const end = Math.min(start + PART_SIZE, file.size)
                const blob = file.slice(start, end)

                try {
                    const { uploadUrl } = await apiGetPartUrl(state.objectKey, state.uploadId, partNumber)
                    const eTag = await uploadPart(uploadUrl, blob, signal)

                    completedParts.set(partNumber, eTag)
                    // 持久化进度
                    state.parts = Array.from(completedParts.entries())
                        .map(([pn, et]) => ({ partNumber: pn, eTag: et }))
                    saveState(resumeKey, state)
                    reportProgress()
                } catch (err) {
                    if (signal.aborted) return
                    hasError = true
                    onError?.(err)
                    return
                }
            }
        }

        // 启动 N 个并发 worker
        const workers = Array.from({ length: Math.min(CONCURRENCY, pendingParts.length) }, () => uploadNext())
        await Promise.all(workers)

        if (hasError || signal.aborted) return

        // 全部分片完成 -> 合并
        try {
            const parts = Array.from(completedParts.entries())
                .map(([pn, et]) => ({ partNumber: pn, eTag: et }))
                .sort((a, b) => a.partNumber - b.partNumber)

            const fileId = await apiCompleteMultipart(state.objectKey, state.uploadId, parts)
            clearState(resumeKey)
            onProgress?.(100)
            onComplete?.(fileId)
        } catch (err) {
            onError?.(err)
        }
    }

    run().catch(err => {
        if (!signal.aborted) onError?.(err)
    })

    return {
        abort: () => {
            controller.abort()
            // 通知后端中止
            const state = loadState(buildResumeKey(file))
            if (state) {
                apiAbortMultipart(state.objectKey, state.uploadId)
                clearState(buildResumeKey(file))
            }
        },
        /** 暂停（不销毁 uploadId，下次可恢复） */
        pause: () => {
            controller.abort()
            // 不清除 localStorage，恢复时继续用
        }
    }
}
