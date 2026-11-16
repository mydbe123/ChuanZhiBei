const TOKEN_KEY = 'zhixue.token'

export class ApiError extends Error {
  code: number

  constructor(message: string, code: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const isForm = init.body instanceof FormData
  if (init.body && !isForm && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(`/api${path}`, { ...init, headers })
  } catch {
    throw new ApiError('网络连接失败，请检查后端服务是否已启动', 0)
  }

  const text = await response.text()
  let payload: ApiResult<T> | null = null
  if (text) {
    try {
      payload = JSON.parse(text) as ApiResult<T>
    } catch {
      payload = null
    }
  }

  if (response.status === 401) {
    setToken(null)
    window.dispatchEvent(new CustomEvent('zhixue:unauthorized'))
  }

  if (!response.ok || (payload && payload.code !== 0)) {
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.code ?? response.status)
  }
  return (payload ? payload.data : null) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form })
}
