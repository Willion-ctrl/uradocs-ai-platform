import request from '@/utils/request'

// ===== 知识空间 =====
export const listSpacesApi = () => request.get('/spaces')
export const createSpaceApi = (data) => request.post('/spaces', data)
export const deleteSpaceApi = (id) => request.delete(`/spaces/${id}`)

// ===== 知识文档 =====
export const listDocsApi = (spaceId) => request.get('/docs', { params: { spaceId } })
export const getDocApi = (id) => request.get(`/docs/${id}`)
export const createDocApi = (data) => request.post('/docs', data)
export const updateDocApi = (id, data) => request.put(`/docs/${id}`, data)
export const deleteDocApi = (id) => request.delete(`/docs/${id}`)