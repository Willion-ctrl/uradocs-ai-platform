import request from '@/utils/request'

/** 用户登录 */
export const loginApi = (data) => request.post('/auth/login', data)

/** 用户注册 */
export const registerApi = (data) => request.post('/auth/register', data)

/** 获取当前登录用户信息（需携带 Token） */
export const getMeApi = () => request.get('/auth/me')