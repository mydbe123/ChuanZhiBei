import { getToken } from './client'

export interface ChatStreamPayload {
  conversationId: number | null
  content: string
  images?: string[]
  useKnowledge: boolean
}

export interface StreamHandlers {
  onMeta?: (data: Record<string, unknown>) => void
  onStep?: (data: Record<string, unknown>) => void
  onReasoning?: (delta: string) => void
  onDelta?: (delta: string) => void
  onToolCall?: (data: Record<string, unknown>) => void
  onToolResult?: (data: Record<string, unknown>) => void
  onCitations?: (items: unknown[]) => void
  onUsage?: (data: Record<string, unknown>) => void
  onDone?: (data: Record<string, unknown>) => void
  onError?: (message: string) => void
}

/**
 * 通过 fetch + ReadableStream 消费服务端 SSE（EventSource 无法发送 POST 请求）。
 */
export async function streamChat(
  payload: ChatStreamPayload,
  handlers: StreamHandlers,
  signal: AbortSignal
): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${getToken() ?? ''}`
      },
      body: JSON.stringify(payload),
      signal
    })
  } catch (e) {
    if ((e as Error).name === 'AbortError') return
    handlers.onError?.('网络异常，无法建立连接')
    return
  }

  if (!response.ok || !response.body) {
    let message = `连接失败（${response.status}）`
    try {
      const data = await response.json()
      message = data?.message ?? message
    } catch {
      /* ignore */
    }
    handlers.onError?.(message)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (raw: string) => {
    const lines = raw.split('\n')
    let event = 'message'
    const dataLines: string[] = []
    for (const line of lines) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
    if (!dataLines.length) return
    const rawData = dataLines.join('\n')
    let data: Record<string, unknown>
    try {
      data = JSON.parse(rawData)
    } catch {
      return
    }
    switch (event) {
      case 'meta':
        handlers.onMeta?.(data)
        break
      case 'step':
        handlers.onStep?.(data)
        break
      case 'reasoning':
        handlers.onReasoning?.(String(data.delta ?? ''))
        break
      case 'delta':
        handlers.onDelta?.(String(data.delta ?? ''))
        break
      case 'tool_call':
        handlers.onToolCall?.(data)
        break
      case 'tool_result':
        handlers.onToolResult?.(data)
        break
      case 'citations':
        handlers.onCitations?.((data.items as unknown[]) ?? [])
        break
      case 'usage':
        handlers.onUsage?.(data)
        break
      case 'done':
        handlers.onDone?.(data)
        break
      case 'error':
        handlers.onError?.(String(data.message ?? 'AI 服务异常'))
        break
      default:
        break
    }
  }

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      let index = buffer.indexOf('\n\n')
      while (index >= 0) {
        const chunk = buffer.slice(0, index)
        buffer = buffer.slice(index + 2)
        if (chunk.trim()) dispatch(chunk)
        index = buffer.indexOf('\n\n')
      }
    }
    if (buffer.trim()) dispatch(buffer)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') {
      handlers.onError?.('响应中断，请重试')
    }
  }
}
