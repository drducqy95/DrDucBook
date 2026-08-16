export const WEB_SESSION_STORAGE_KEY = 'drducbookWebSession'

export const getWebSessionToken = () =>
  sessionStorage.getItem(WEB_SESSION_STORAGE_KEY) || ''

export const setWebSessionToken = (token: string) =>
  sessionStorage.setItem(WEB_SESSION_STORAGE_KEY, token)

export const clearWebSessionToken = () =>
  sessionStorage.removeItem(WEB_SESSION_STORAGE_KEY)

export const withWebSession = (url: URL) => {
  const token = getWebSessionToken()
  if (token) url.searchParams.set('access_token', token)
  return url
}
