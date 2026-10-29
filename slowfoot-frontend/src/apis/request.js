import axios from 'axios'
import { useUserStore } from '../stores'

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

instance.interceptors.request.use((config) => {
  const token = useUserStore().token
  if (token) config.headers.token = token
  return config
})

instance.interceptors.response.use((response) => response, (error) => {
  if (error.response?.status === 401) {
    const userStore = useUserStore()
    sessionStorage.removeItem('token')
    userStore.$patch({ token: null, info: {} })
  }
  return Promise.reject(error)
})

export default instance
