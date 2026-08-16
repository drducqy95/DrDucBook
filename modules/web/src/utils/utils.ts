import { formatDate } from '@vueuse/shared'
export const isNullOrBlank = (string: string | null | undefined | number) =>
  string == null ||
  (string as string).length === 0 ||
  /^\s+$/.test(string as string)

export const isLegadoUrl = (/** @type {string} */ url: string) =>
  /,\s*\{/.test(url) ||
  !(
    url.startsWith('http') ||
    url.startsWith('data:') ||
    url.startsWith('blob:')
  )

/**
 * 验证输入的URL是Không符合阅读后端地址规则
 * @param allowedProtocols 允许的协议，Mặc định`["https:", "http:"]`
 */
export const validatorHttpUrl = (
  http_url: string | URL,
  allowedProtocols: string[] = ['https:', 'http:'],
) => {
  try {
    const url = new URL(http_url)
    const { protocol } = url
    if (!allowedProtocols.includes(protocol))
      throw new Error(
        `Expected protocol ${allowedProtocols.join('/')}, but ${protocol}`,
      )
    return true
  } catch {
    return false
  }
}

export const dateFormat = (/** @type {number} */ t: number) => {
  const time = new Date().getTime()
  const offset = Math.floor((time - t) / 1000)
  let str = ''

  if (offset <= 30) {
    str = 'Vừa xong'
  } else if (offset < 60) {
    str = offset + ' giây trước'
  } else if (offset < 3600) {
    str = Math.floor(offset / 60) + ' phút trước'
  } else if (offset < 86400) {
    str = Math.floor(offset / 3600) + ' giờ trước'
  } else if (offset < 2592000) {
    str = Math.floor(offset / 86400) + ' ngày trước'
  } else {
    str = formatDate(new Date(t), 'YYYY-MM-DD')
  }
  return str
}
