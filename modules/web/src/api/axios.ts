import axios from 'axios'
import { getWebSessionToken } from './webSession'

/** @type {string} localStorageLưu自定义阅读http服务接口的键值 */
export const baseURL_localStorage_key = 'remoteUrl'
const SECOND = 1000

const getInitialBaseUrl = () => {
  if (import.meta.env.VITE_API) return import.meta.env.VITE_API
  const saved = typeof localStorage !== 'undefined' ? localStorage.getItem(baseURL_localStorage_key) : null
  if (saved) {
    if (typeof location !== 'undefined' && location.protocol === 'https:' && saved.startsWith('http:')) {
      return location.origin
    }
    return saved
  }
  return typeof location !== 'undefined' ? location.origin : ''
}

const ajax = axios.create({
  baseURL: getInitialBaseUrl(),
  timeout: 120 * SECOND,
})

ajax.interceptors.request.use(config => {
  const token = getWebSessionToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default ajax
