import type { ConnectionState } from '../services/gameSocket'

const LABELS: Record<ConnectionState, string> = {
  connecting: 'Connexion…',
  connected: 'Connecté',
  reconnecting: 'Reconnexion…',
  disconnected: 'Déconnecté',
}

/** Always visible: the user must know whether what they see is live or not. */
export default function ConnectionStatus({ state }: { state: ConnectionState }) {
  return (
    <div className={`connection connection--${state}`} role="status" aria-live="polite">
      <span className="connection__dot" aria-hidden="true" />
      {LABELS[state]}
    </div>
  )
}
