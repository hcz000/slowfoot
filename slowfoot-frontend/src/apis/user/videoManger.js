import request from '../request'

// Adapt the editor model to VideoWorkPublishRequest.
export const apiVideoPush = (videoInfo) => {
    if (!videoInfo) return Promise.reject(new Error("作品信息不能为空"))

    const labelNames = Array.isArray(videoInfo.labelNames)
        ? videoInfo.labelNames.join(",")
        : (videoInfo.labelNames || "")

    return request.post("/work", {
        id: videoInfo.id,
        title: videoInfo.title,
        description: videoInfo.description,
        typeId: videoInfo.typeId,
        labelNames,
        openState: videoInfo.openState,
        videoFileId: videoInfo.videoFileId || videoInfo.primaryFileId,
        coverFileId: videoInfo.coverFileId
    })
}

export const apiRemoveVideo = (id) => request.delete(`/work/${id}`)
