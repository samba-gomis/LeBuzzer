// TypeScript mirror of the backend DTOs (backend/src/main/java/com/lebuzzer/backend/dto).
// Any change of the message contract must be made on both sides.

export const GAME_STATUSES = ['LOBBY', 'QUESTION_OPEN', 'QUESTION_CLOSED', 'FINISHED'] as const
export type GameStatus = (typeof GAME_STATUSES)[number]

export const PARTICIPANT_ROLES = ['HOST', 'PLAYER'] as const
export type ParticipantRole = (typeof PARTICIPANT_ROLES)[number]

export const ERROR_CODES = [
  'GAME_NOT_FOUND',
  'THEME_NOT_FOUND',
  'INVALID_NICKNAME',
  'NICKNAME_TAKEN',
  'INVALID_HOST_TOKEN',
  'NOT_IN_GAME',
  'GAME_FINISHED',
  'QUESTION_ALREADY_OPEN',
  'NO_MORE_QUESTIONS',
  'QUESTION_NOT_OPEN',
  'QUESTION_CLOSED',
  'ALREADY_ANSWERED',
  'INVALID_CHOICE',
  'INVALID_REQUEST',
] as const
export type ErrorCode = (typeof ERROR_CODES)[number]

/** A player as everybody sees them: public id only. */
export interface PlayerView {
  publicId: string
  nickname: string
  connected: boolean
  score: number
}

export interface LeaderboardEntry {
  rank: number
  publicId: string
  nickname: string
  score: number
  /** Points won at the last closed question. */
  lastPoints: number
}

/** Never contains the correct answer. */
export interface QuestionView {
  questionId: string
  /** 0-based. */
  index: number
  total: number
  text: string
  choices: string[]
  points: number
  durationMs: number
  /** Server time (epoch ms) at which answers stop being accepted. */
  endsAt: number
}

/** Only in the player's own snapshot. */
export interface PlayerSelf {
  /** Secret: kept in sessionStorage to rejoin, never shown. */
  playerId: string
  publicId: string
  nickname: string
  score: number
  lastPoints: number
  /** Answer to the current question, null if none. */
  choiceIndex: number | null
}

/** Full current state, received after join, host-join and sync (/user/queue/state). */
export interface GameSnapshot {
  code: string
  status: GameStatus
  role: ParticipantRole
  /** null for the host. */
  you: PlayerSelf | null
  players: PlayerView[]
  /** Open or last closed question, null in the lobby. */
  currentQuestion: QuestionView | null
  /** Only once the current question is closed. */
  correctIndex: number | null
  answeredCount: number
  connectedPlayers: number
  leaderboard: LeaderboardEntry[]
  /** Server time when the snapshot was built. */
  serverNow: number
}

/** Answer to /app/game/create (/user/queue/game). */
export interface GameCreated {
  code: string
  hostToken: string
}

/** /user/queue/errors */
export interface ErrorMessage {
  code: ErrorCode
  message: string
}

/** GET /api/themes */
export interface ThemeView {
  id: string
  name: string
  questionCount: number
}

/**
 * Game events, a discriminated union on `type`. All are broadcast on /topic/game/{code}, except
 * ANSWER_PROGRESS, sent to the host alone on /user/queue/progress.
 */
export type RoomEvent =
  | { type: 'PLAYERS_UPDATED'; players: PlayerView[] }
  | { type: 'QUESTION_STARTED'; question: QuestionView; serverNow: number }
  | { type: 'ANSWER_PROGRESS'; answeredCount: number; connectedPlayers: number }
  | { type: 'QUESTION_CLOSED'; questionId: string; correctIndex: number; leaderboard: LeaderboardEntry[] }
  | { type: 'GAME_ENDED'; leaderboard: LeaderboardEntry[] }
