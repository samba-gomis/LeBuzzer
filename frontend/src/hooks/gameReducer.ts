import type { GameSnapshot, LeaderboardEntry, PlayerSelf, RoomEvent } from '../types/messages'

// How the local game state evolves. A pure function (no network, no clock): easy to read and to test.
//
// Shared state vs event stream:
// - a snapshot (after join, rejoin or sync) REPLACES the whole local state: it is the present;
// - an event only describes a change, applied on top of the last snapshot.
// A late joiner or a reconnected client therefore never keeps an outdated screen.

export interface GameState {
  /** The current view, kept in the same shape as a snapshot. */
  view: GameSnapshot
  /** serverNow - Date.now() at the last reception: converts the server deadline to local time. */
  clockOffsetMs: number
}

export type GameAction =
  | { kind: 'snapshot'; snapshot: GameSnapshot; receivedAt: number }
  | { kind: 'event'; event: RoomEvent; receivedAt: number }
  | { kind: 'answered'; choiceIndex: number }
  | { kind: 'answer-rejected' }

export function gameReducer(state: GameState | null, action: GameAction): GameState | null {
  switch (action.kind) {
    case 'snapshot':
      return { view: action.snapshot, clockOffsetMs: action.snapshot.serverNow - action.receivedAt }
    case 'event':
      // Events before the first snapshot are ignored: that snapshot already contains them.
      if (state === null) {
        return null
      }
      return {
        view: applyEvent(state.view, action.event),
        clockOffsetMs:
          action.event.type === 'QUESTION_STARTED'
            ? action.event.serverNow - action.receivedAt
            : state.clockOffsetMs,
      }
    case 'answered':
      // Shown at once so the buttons lock immediately; the server stays the judge (an error comes
      // back if the answer was refused, and the next snapshot always tells the truth).
      if (state === null || state.view.you === null) {
        return state
      }
      return { ...state, view: { ...state.view, you: { ...state.view.you, choiceIndex: action.choiceIndex } } }
    case 'answer-rejected':
      // The server refused the answer (too late): it does not count, so it is not shown either.
      if (state === null || state.view.you === null) {
        return state
      }
      return { ...state, view: { ...state.view, you: { ...state.view.you, choiceIndex: null } } }
  }
}

function applyEvent(view: GameSnapshot, event: RoomEvent): GameSnapshot {
  switch (event.type) {
    case 'PLAYERS_UPDATED':
      return {
        ...view,
        players: event.players,
        connectedPlayers: event.players.filter((player) => player.connected).length,
      }
    case 'QUESTION_STARTED':
      return {
        ...view,
        status: 'QUESTION_OPEN',
        currentQuestion: event.question,
        correctIndex: null,
        answeredCount: 0,
        leaderboard: event.leaderboard,
        you: view.you === null ? null : { ...view.you, choiceIndex: null },
      }
    case 'ANSWER_PROGRESS':
      return { ...view, answeredCount: event.answeredCount, connectedPlayers: event.connectedPlayers }
    case 'QUESTION_CLOSED':
      return {
        ...view,
        status: 'QUESTION_CLOSED',
        correctIndex: event.correctIndex,
        leaderboard: event.leaderboard,
        you: withScoreFrom(view.you, event.leaderboard),
      }
    case 'GAME_ENDED':
      return {
        ...view,
        status: 'FINISHED',
        leaderboard: event.leaderboard,
        you: withScoreFrom(view.you, event.leaderboard),
      }
  }
}

/** The player's own score and last points, as given by the new leaderboard. */
function withScoreFrom(you: PlayerSelf | null, leaderboard: LeaderboardEntry[]): PlayerSelf | null {
  if (you === null) {
    return null
  }
  const entry = leaderboard.find((line) => line.publicId === you.publicId)
  return entry === undefined ? you : { ...you, score: entry.score, lastPoints: entry.lastPoints }
}
