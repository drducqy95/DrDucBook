import type { AxiosResponse } from 'axios'
import type { LeagdoApiResponse } from './api'
import API, {
  setWebsocketOnError,
  setApiEntryPoint,
  legado_http_entry_point,
  setWebsocketOnMessage,
} from './api'
import ajax from './axios'
import { validatorHttpUrl } from '@/utils/utils'

import { useConnectionStore } from '@/store/connectionStore'

const LeagdoApiResponseKeys: string[] = Array.of('isSuccess', 'errorMsg')

const notification = ElMessage
/** Axios.Interceptor: check if resp is LeagaoLeagdoApiResponse*/
const responseCheckInterceptor = (resp: AxiosResponse) => {
  let isLeagdoApiResponse = true
  try {
    const data = resp.data

    for (const key of LeagdoApiResponseKeys) {
      if (!(key in data)) {
        isLeagdoApiResponse = false
        LeagdoApiResponseKeys.length = 0
      }
    }
    if ((data as LeagdoApiResponse<unknown>).isSuccess === true) {
      if (!('data' in data)) {
        isLeagdoApiResponse = false
      }
    }
  } catch {
    isLeagdoApiResponse = false
  }
  if (isLeagdoApiResponse === false) {
    notification.warning({ message: 'Backend trả về định dạng không hợp lệ', grouping: true })
    throw new Error()
  }
  const connectionStore = useConnectionStore()
  connectionStore.setConnectType('primary')
  connectionStore.setConnectStatus('Đã kết nối ' + legado_http_entry_point)
  return resp
}

const axiosErrorInterceptor = (err: unknown) => {
  const connectionStore = useConnectionStore()
  notification.error({
    message: 'Kết nối backend thất bại, hãy kiểm tra Dịch vụ Web DrDucBook hoặc đặt liên kết khác khả dụng',
    grouping: true,
  })
  connectionStore.setConnectType('danger')
  connectionStore.setConnectStatus('Kết nối bất thường')
  throw err
}
// http全局
ajax.interceptors.response.use(responseCheckInterceptor, axiosErrorInterceptor)
// websocket
setWebsocketOnError(axiosErrorInterceptor)
setWebsocketOnMessage(() => {
  const connectionStore = useConnectionStore()
  connectionStore.setConnectType('primary')
  connectionStore.setConnectStatus('Đã kết nối ' + legado_http_entry_point)
})
/**
 * 按照阅读的Mặc định规则 解析阅读HTTP WebSocket API入口地址
 * @returns [http_url, webSocekt_url]
 */
export const parseLeagdoHttpUrlWithDefault = (
  http_url: string | URL,
): [string, string] => {
  let url = new URL(location.origin) //Mặc định当前网址的origin部分
  if (validatorHttpUrl(http_url)) {
    url = new URL(http_url)
  }
  const { protocol, port } = url
  // websocket服务端口 为http服务端口 + 1
  const usesCurrentWebService = url.origin === location.origin
  let legado_webSocket_port
  if (usesCurrentWebService) {
    legado_webSocket_port = port
  } else if (port !== '') {
    legado_webSocket_port = String(Number(port) + 1)
  } else {
    legado_webSocket_port = protocol.startsWith('https:') ? '444' : '81'
  }
  // websocket协议是Không为加密版本
  const legado_webSocket_protocol = protocol.startsWith('https:')
    ? 'wss://'
    : 'ws://'

  const http_entry_point = url.toString()

  url.protocol = legado_webSocket_protocol
  url.port = legado_webSocket_port
  const webSocket_entry_point = url.toString()

  console.info('legado_api_config:')
  console.table({
    'Điểm vào HTTP API': http_entry_point,
    'Điểm vào WebSocket API': webSocket_entry_point,
  })
  return [http_entry_point, webSocket_entry_point]
}

//export const useLeagdoRemoteUrlDialog = () => { }

setApiEntryPoint(
  ...parseLeagdoHttpUrlWithDefault(ajax.defaults.baseURL as string),
)

export default API
export * from './api'
export * from './webService'
