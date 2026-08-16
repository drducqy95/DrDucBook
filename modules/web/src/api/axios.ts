import axios from 'axios'
import { getWebSessionToken } from './webSession'

/** @type {string} localStorageLưu自定义阅读http服务接口的键值 */
export const baseURL_localStorage_key = 'remoteUrl'
const SECOND = 1000

const ajax = axios.create({
  baseURL:
    import.meta.env.VITE_API ||
    localStorage.getItem(baseURL_localStorage_key) ||
    location.origin,
  timeout: 120 * SECOND,
})

ajax.interceptors.request.use(config => {
  const token = getWebSessionToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default ajax
