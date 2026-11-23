import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：自动注入 Bearer Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auradoc_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一解析后端 Result<T> 结构
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务成功，直接返回真正的 data
    if (res.code === 200) {
      return res.data
    }
    // Token 过期或未登录
    if (res.code === 401) {
      localStorage.removeItem('auradoc_token')
      localStorage.removeItem('auradoc_user')
      ElMessage.error(res.message || '登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message))
    }
    // 其他业务异常（400/403/2001等）直接弹窗提示
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常，请检查后端服务是否启动')
    return Promise.reject(error)
  }
)

export default request