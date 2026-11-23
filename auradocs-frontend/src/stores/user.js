import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMeApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('auradoc_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('auradoc_user') || 'null'))

  /** 登录成功后持久化 Token 与基础信息 */
  const setLoginData = (data) => {
    token.value = data.token
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      role: data.role
    }
    localStorage.setItem('auradoc_token', data.token)
    localStorage.setItem('auradoc_user', JSON.stringify(userInfo.value))
  }

  /** 向后端拉取最新用户信息（验证 Token 有效性） */
  const fetchUserInfo = async () => {
    const data = await getMeApi()
    userInfo.value = data
    localStorage.setItem('auradoc_user', JSON.stringify(data))
    return data
  }

  /** 退出登录并清空缓存 */
  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('auradoc_token')
    localStorage.removeItem('auradoc_user')
  }

  return { token, userInfo, setLoginData, fetchUserInfo, logout }
})