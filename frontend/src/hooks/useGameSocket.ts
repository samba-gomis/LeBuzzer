import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { GameSocket, type ConnectionState } from '../services/gameSocket'
import { loadHostSession, loadPlayerSession, savePlayerSession } from '../services/session'
import { isErrorMessage, isGameSnapshot, isRoomEvent } from '../types/guards'
import type { ErrorMessage } from '../types/messages'
import { gameReducer, type GameState } from './gameReducer'

export type GameRole = 'host' | 'player'

export type HostAction = 'start-question' | 'close-question' | 'end'

export interface GameConnection {
  connectionState: ConnectionState
  /** null until the first snapshot. */
  game: GameState | null
  error: ErrorMessage | null
  dismissError: () => void
  answer: (questionId: string, choiceIndex: number) => void
  sendHostAction: (action: HostAction) => void
}

/**
 * Connects a page to one game. The credentials (hostToken, or nickname + playerId) are read from
 * the tab's sessionStorage, never passed around by the components.
 */
export function useGameSocket(code: string, role: GameRole): GameConnection {
  const [connectionState, setConnectionState] = useState<ConnectionState>('connecting')
  const [game, dispatch] = useReducer(gameReducer, null)
  const [error, setError] = useState<ErrorMessage | null>(null)
  const socketRef = useRef<GameSocket | null>(null)

  useEffect(() => {
    const socket = new GameSocket({
      onStateChange: setConnectionState,
      // After every (re)connection: introduce ourselves again. The server answers with a fresh
      // snapshot that replaces whatever we had (score and answer included).
      onConnected: () => {
        if (role === 'host') {
          const session = loadHostSession(code)
          if (session !== null) {
            socket.send(`/app/game/${code}/host-join`, { hostToken: session.hostToken })
          }
        } else {
          const session = loadPlayerSession(code)
          if (session !== null) {
            socket.send(`/app/game/${code}/join`, { nickname: session.nickname, playerId: session.playerId })
          }
        }
      },
    })

    // Subscriptions are declared before start(): they exist before join is sent, so no event is missed.
    socket.subscribe(`/topic/game/${code}`, (body) => {
      if (isRoomEvent(body)) {
        dispatch({ kind: 'event', event: body, receivedAt: Date.now() })
      }
    })
    socket.subscribe('/user/queue/state', (body) => {
      if (!isGameSnapshot(body)) {
        return
      }
      if (body.you !== null) {
        // Keep the secret playerId: it is what lets this tab rejoin as the same player.
        savePlayerSession(code, { nickname: body.you.nickname, playerId: body.you.playerId })
      }
      setError(null)
      dispatch({ kind: 'snapshot', snapshot: body, receivedAt: Date.now() })
    })
    socket.subscribe('/user/queue/errors', (body) => {
      if (!isErrorMessage(body)) {
        return
      }
      if (body.code === 'QUESTION_CLOSED') {
        // Answer arrived after the deadline. (ALREADY_ANSWERED keeps the first answer, which did count.)
        dispatch({ kind: 'answer-rejected' })
      }
      setError(body)
    })
    if (role === 'host') {
      // The answer progress is sent to the host alone: players do not display it.
      socket.subscribe('/user/queue/progress', (body) => {
        if (isRoomEvent(body) && body.type === 'ANSWER_PROGRESS') {
          dispatch({ kind: 'event', event: body, receivedAt: Date.now() })
        }
      })
    }

    socket.start()
    socketRef.current = socket

    // Cleanup: without it every mount (twice in StrictMode) would leave one more open connection,
    // each message would be handled several times, and state updates would hit unmounted components.
    return () => {
      socketRef.current = null
      socket.stop()
    }
  }, [code, role])

  const dismissError = useCallback(() => setError(null), [])

  const answer = useCallback(
    (questionId: string, choiceIndex: number) => {
      const sent = socketRef.current?.send(`/app/game/${code}/answer`, { questionId, choiceIndex }) ?? false
      if (sent) {
        dispatch({ kind: 'answered', choiceIndex })
      }
    },
    [code],
  )

  const sendHostAction = useCallback(
    (action: HostAction) => {
      const session = loadHostSession(code)
      if (session !== null) {
        socketRef.current?.send(`/app/game/${code}/${action}`, { hostToken: session.hostToken })
      }
    },
    [code],
  )

  return { connectionState, game, error, dismissError, answer, sendHostAction }
}
