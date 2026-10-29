const EVENT_STREAM_URL = '/api/notification/events'
const INITIAL_RETRY_DELAY = 1000
const MAX_RETRY_DELAY = 30000

const parseEvent = (eventName, dataLines, onEvent) => {
  if (!dataLines.length || (eventName && eventName !== 'notification')) return

  try {
    onEvent(JSON.parse(dataLines.join('\n')))
  } catch (error) {
    console.error('无法解析通知 SSE 事件', error)
  }
}

const readEventStream = async (body, signal, onEvent) => {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = ''
  let dataLines = []

  const processLine = (line) => {
    if (line === '') {
      parseEvent(eventName, dataLines, onEvent)
      eventName = ''
      dataLines = []
      return
    }

    if (line.startsWith(':')) return

    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')

    if (field === 'event') eventName = value
    if (field === 'data') dataLines.push(value)
  }

  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newlineIndex).replace(/\r$/, '')
        buffer = buffer.slice(newlineIndex + 1)
        processLine(line)
      }
    }
  } finally {
    if (signal.aborted) await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

export const connectNotificationEvents = ({ token, onEvent, onConnected, onUnauthorized }) => {
  let active = true
  let controller
  let retryTimer
  let resolveRetry

  const stop = () => {
    active = false
    controller?.abort()
    clearTimeout(retryTimer)
    resolveRetry?.()
    resolveRetry = undefined
  }

  const waitBeforeRetry = (delay) => new Promise((resolve) => {
    resolveRetry = resolve
    retryTimer = setTimeout(() => {
      retryTimer = undefined
      resolveRetry = undefined
      resolve()
    }, delay)
  })

  const run = async () => {
    let retryDelay = INITIAL_RETRY_DELAY

    while (active) {
      controller = new AbortController()
      let connectedAt = 0

      try {
        const response = await fetch(EVENT_STREAM_URL, {
          method: 'GET',
          headers: {
            Accept: 'text/event-stream',
            token
          },
          cache: 'no-cache',
          signal: controller.signal
        })

        if (response.status === 401) {
          onUnauthorized?.()
          stop()
          return
        }
        if (!response.ok || !response.body) {
          throw new Error(`通知 SSE 连接失败：HTTP ${response.status}`)
        }

        connectedAt = Date.now()
        onConnected?.()
        await readEventStream(response.body, controller.signal, onEvent)
        if (active) throw new Error('Notification SSE connection closed')
      } catch (error) {
        if (!active) return
        if (error.name !== 'AbortError') console.warn('通知 SSE 连接中断，将自动重连', error)
      }

      if (!active) return
      if (connectedAt && Date.now() - connectedAt >= MAX_RETRY_DELAY) {
        retryDelay = INITIAL_RETRY_DELAY
      }
      await waitBeforeRetry(retryDelay)
      retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY)
    }
  }

  run()
  return { stop }
}
