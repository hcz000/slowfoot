import request from './request'

/** 获取全部视频分类。 */
export const apiClassifyGetAll = () => request.get('/index/types')
