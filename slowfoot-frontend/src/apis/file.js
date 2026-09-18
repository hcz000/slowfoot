import request from './request'

const defaultCallbacks = {
    next: () => {},
    error: () => {},
    complete: () => {}
}

const uploadWithTicket = async (file, getTicket, callBack = defaultCallbacks) => {
    const callbacks = { ...defaultCallbacks, ...callBack }
    let ticketResponse

    try {
        ticketResponse = await getTicket()
        if (!ticketResponse?.data?.success) {
            throw new Error(ticketResponse?.data?.message || "获取上传凭证失败")
        }
    } catch (error) {
        callbacks.error(error)
        return null
    }

    const ticket = ticketResponse.data.data
    if (!ticket?.uploadUrl || !ticket.objectKey) {
        callbacks.error(new Error("上传凭证格式无效"))
        return null
    }
    if (ticket.maxSizeBytes && file.size > ticket.maxSizeBytes) {
        callbacks.error(new Error("文件大小超过限制"))
        return null
    }

    const xhr = new XMLHttpRequest()
    const subscription = {
        unsubscribe: () => xhr.abort()
    }

    xhr.upload.addEventListener("progress", (event) => {
        if (!event.lengthComputable) return
        callbacks.next({
            total: {
                percent: (event.loaded / event.total) * 100
            }
        })
    })
    xhr.addEventListener("error", () => callbacks.error(new Error("文件上传失败")))
    xhr.addEventListener("abort", () => callbacks.error(new Error("文件上传已取消")))
    xhr.addEventListener("load", async () => {
        if (xhr.status < 200 || xhr.status >= 300) {
            callbacks.error(new Error(`文件上传失败 (${xhr.status})`))
            return
        }

        try {
            const fileResponse = await request.post("/file", null, {
                params: { fileKey: ticket.objectKey }
            })
            callbacks.complete({ key: ticket.objectKey }, fileResponse.data)
        } catch (error) {
            callbacks.error(error)
        }
    })

    xhr.open(ticket.method || "PUT", ticket.uploadUrl)
    xhr.setRequestHeader("Content-Type", ticket.contentType || file.type || "application/octet-stream")
    xhr.send(file)
    return subscription
}

const apiFileGetToken = (file) => request.get("/file/getToken", {
    params: {
        type: file.type,
        filename: file.name
    }
})

/** Upload a browser File to OSS and register it in the application file table. */
export const apiUploadFile = async (file, callBack = defaultCallbacks) => {
    if (!file) return null
    return uploadWithTicket(file, () => apiFileGetToken(file), callBack)
}

/** Return a trusted, short-lived URL for a registered file. */
export const apiFileGet = (fileId) => {
    return request.getUri({
        url: `/file/${fileId}`
    })
}
