import request from './request'

export const apiPublishImageWork = (work) => request.post('/work/image', work)

export const apiPublishTextWork = (work) => request.post('/work/text', work)
