import { Client } from '@stomp/stompjs'

// One STOMP connection and its reconnection policy. No React here: the hook useGameSocket
// plugs this class into the React state.

export type ConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'disconnected'

type MessageHandler = (body: unknown) => void

const BASE_RETRY_DELAY_MS = 1_000
const MAX_RETRY_DELAY_MS = 30_000
const HEARTBEAT_MS = 10_000

/**
 * Exponential backoff: 1 s, 2 s, 4 s, 8 s... capped at 30 s. Each delay is randomised between 80 %
 * and 120 % (jitter), so that after a Wi-Fi cut all the phones of the room do not retry at the
 * same instant. A tight loop every 50 ms would only flood a server that is already in trouble.
 */
export function retryDelayMs(attempt: number, random: () => number = Math.random): number {
  const exponential = Math.min(MAX_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS * 2 ** attempt)
  return Math.round(exponential * (0.8 + 0.4 * random()))
}

/** Same host as the page: in development, the Vite proxy forwards /ws to the backend. */
function defaultBrokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws`
}

function parseBody(body: string): unknown {
  try {
    const parsed: unknown = JSON.parse(body)
    return parsed
  } catch {
    return null
  }
}

export interface GameSocketOptions {
  onStateChange: (state: ConnectionState) => void
  /** Called after every connection and reconnection, once the subscriptions are back. */
  onConnected: () => void
}

export class GameSocket {
  private readonly options: GameSocketOptions
  /** Wanted subscriptions, by destination: recreated on every connection. */
  private readonly handlers = new Map<string, MessageHandler>()
  private client: Client | null = null
  private retryTimer: ReturnType<typeof setTimeout> | null = null
  private attempt = 0
  private running = false

  constructor(options: GameSocketOptions) {
    this.options = options
  }

  subscribe(destination: string, handler: MessageHandler): void {
    this.handlers.set(destination, handler)
    if (this.client?.connected) {
      this.client.subscribe(destination, (message) => handler(parseBody(message.body)))
    }
  }

  start(): void {
    if (this.running) {
      return
    }
    this.running = true
    this.attempt = 0
    this.connect('connecting')
  }

  /** Closes the connection for good (component unmounted, page left). */
  stop(): void {
    this.running = false
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
    const client = this.client
    this.client = null
    if (client !== null) {
      void client.deactivate()
    }
    this.options.onStateChange('disconnected')
  }

  /**
   * Returns false when not connected. Nothing is queued: after the reconnection the server sends a
   * fresh snapshot, and replaying old actions could do more harm than good.
   */
  send(destination: string, body: object): boolean {
    if (this.client === null || !this.client.connected) {
      return false
    }
    this.client.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  private connect(state: 'connecting' | 'reconnecting'): void {
    this.options.onStateChange(state)
    // A brand new STOMP client for each attempt: no state is carried over from a dead connection.
    const client = new Client({
      brokerURL: defaultBrokerUrl(),
      // stompjs's own fixed-delay reconnection is turned off: scheduleRetry() below does it.
      reconnectDelay: 0,
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      // When heartbeats stop (network cut), drop the socket at once instead of waiting for the browser.
      discardWebsocketOnCommFailure: true,
      onConnect: () => {
        if (client !== this.client) {
          return
        }
        this.attempt = 0
        // The subscriptions of the previous connection died with it: they are all recreated.
        for (const [destination, handler] of this.handlers) {
          client.subscribe(destination, (message) => handler(parseBody(message.body)))
        }
        this.options.onStateChange('connected')
        this.options.onConnected()
      },
      onWebSocketClose: () => {
        if (client === this.client && this.running) {
          this.scheduleRetry()
        }
      },
      onStompError: (frame) => {
        console.warn('[gameSocket] STOMP error:', frame.headers.message)
      },
    })
    this.client = client
    client.activate()
  }

  private scheduleRetry(): void {
    const failed = this.client
    this.client = null
    if (failed !== null) {
      void failed.deactivate()
    }
    const delay = retryDelayMs(this.attempt)
    this.attempt += 1
    // Deliberate log: it makes the backoff (1 s, 2 s, 4 s...) visible in the console.
    console.info(`[gameSocket] connection lost, retry #${this.attempt} in ${delay} ms`)
    this.options.onStateChange('reconnecting')
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null
      if (this.running) {
        this.connect('reconnecting')
      }
    }, delay)
  }
}
