/**
 * 发起基于文档上下文的 SSE 流式问答
 * @param {Object} payload { sessionId, docId, question }
 * @param {Object} callbacks { onMeta, onMessage, onDone, onError }
 */
export async function streamAiChat(payload, { onMeta, onMessage, onDone, onError }) {
  const token = localStorage.getItem('auradoc_token')

  try {
    const response = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 请求 AI 服务失败`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let currentEvent = 'message'

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      // 保留最后一行未接收完整的片段
      buffer = lines.pop() || ''

      for (const rawLine of lines) {
        const line = rawLine.trim()
        if (!line) continue

        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const dataStr = line.slice(5).trim()
          if (!dataStr) continue
          try {
            const parsed = JSON.parse(dataStr)
            if (currentEvent === 'meta') {
              onMeta?.(parsed)
            } else if (currentEvent === 'message') {
              onMessage?.(parsed.content || '')
            } else if (currentEvent === 'done') {
              onDone?.()
            } else if (currentEvent === 'error') {
              onError?.(new Error(parsed.message || 'AI 服务异常'))
            }
          } catch {
            // 忽略非 JSON 心跳或片段
          }
        }
      }
    }
    onDone?.()
  } catch (err) {
    onError?.(err)
  }
}