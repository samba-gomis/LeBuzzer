import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { GameSocket, type ConnectionState } from '../services/gameSocket'
import { saveHostSession } from '../services/session'
import { isErrorMessage, isGameCreated } from '../types/guards'
import type { ErrorMessage } from '../types/messages'

export interface CreateGame {
  createGame: (themeId: string) => void
  creating: boolean
  connectionState: ConnectionState
  error: ErrorMessage | null
}

/**
 * Creates a game for the host. The connection only lives while the request is pending:
 * once GameCreated arrives, the host page opens its own connection.
 */
export function useCreateGame(): CreateGame {
  const navigate = useNavigate()
  const [request, setRequest] = useState<{ themeId: string } | null>(null)
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected')
  const [error, setError] = useState<ErrorMessage | null>(null)

  useEffect(() => {
    if (request === null) {
      return
    }
    const socket = new GameSocket({
      onStateChange: setConnectionState,
      onConnected: () => {
        socket.send('/app/game/create', { themeId: request.themeId })
      },
    })
    socket.subscribe('/user/queue/game', (body) => {
      if (isGameCreated(body)) {
        // The hostToken is the proof of being the host: kept in this tab only.
        saveHostSession(body.code, { hostToken: body.hostToken })
        void navigate(`/host/${body.code}`)
      }
    })
    socket.subscribe('/user/queue/errors', (body) => {
      if (isErrorMessage(body)) {
        setError(body)
        setRequest(null)
      }
    })
    socket.start()
    return () => socket.stop()
  }, [request, navigate])

  const createGame = useCallback((themeId: string) => {
    setError(null)
    setRequest({ themeId })
  }, [])

  return { createGame, creating: request !== null, connectionState, error }
}
