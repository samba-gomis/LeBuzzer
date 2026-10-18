import type { PlayerView } from '../types/messages'

/** Connected players only: someone who closed the tab leaves the list (but keeps their score). */
export default function PlayerList({ players }: { players: PlayerView[] }) {
  const connected = players.filter((player) => player.connected)
  const away = players.length - connected.length

  return (
    <section className="player-list">
      <h3 className="player-list__title">
        {connected.length} joueur{connected.length > 1 ? 's' : ''} connecté{connected.length > 1 ? 's' : ''}
      </h3>
      {connected.length === 0 ? (
        <p className="muted">En attente des joueurs…</p>
      ) : (
        <ul className="player-list__items">
          {connected.map((player) => (
            <li key={player.publicId} className="player-chip">
              {player.nickname}
            </li>
          ))}
        </ul>
      )}
      {away > 0 && (
        <p className="muted">
          {away} joueur{away > 1 ? 's' : ''} déconnecté{away > 1 ? 's' : ''}
        </p>
      )}
    </section>
  )
}
