import { formatPoints, formatRank } from '../services/format'
import type { LeaderboardEntry } from '../types/messages'

interface LeaderboardProps {
  entries: LeaderboardEntry[]
  /** Highlights this player's line (the player's own screen). */
  highlightPublicId?: string
  limit?: number
}

export default function Leaderboard({ entries, highlightPublicId, limit }: LeaderboardProps) {
  const shown = limit === undefined ? entries : entries.slice(0, limit)
  if (shown.length === 0) {
    return <p className="muted">Pas encore de joueur.</p>
  }
  return (
    <ol className="leaderboard">
      {shown.map((entry) => (
        <li
          key={entry.publicId}
          className={entry.publicId === highlightPublicId ? 'leaderboard__row leaderboard__row--me' : 'leaderboard__row'}
        >
          <span className={`leaderboard__rank leaderboard__rank--${Math.min(entry.rank, 4)}`}>
            {formatRank(entry.rank)}
          </span>
          <span className="leaderboard__name">{entry.nickname}</span>
          {entry.lastPoints > 0 && <span className="leaderboard__gain">+{formatPoints(entry.lastPoints)}</span>}
          <span className="leaderboard__score">{formatPoints(entry.score)}</span>
        </li>
      ))}
    </ol>
  )
}
