import {
  ERROR_CODES,
  GAME_STATUSES,
  PARTICIPANT_ROLES,
  type ErrorMessage,
  type GameCreated,
  type GameSnapshot,
  type LeaderboardEntry,
  type PlayerSelf,
  type PlayerView,
  type QuestionView,
  type RoomEvent,
  type ThemeView,
} from './messages'

// Data coming from the network is typed `unknown` until one of these guards has checked its shape.
// No `as` cast: if the server sends something unexpected, the message is ignored instead of crashing the UI.

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

const isString = (value: unknown): value is string => typeof value === 'string'
const isNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value)
const isBoolean = (value: unknown): value is boolean => typeof value === 'boolean'

export function isArrayOf<T>(value: unknown, guard: (item: unknown) => item is T): value is T[] {
  return Array.isArray(value) && value.every((item) => guard(item))
}

function isOneOf<T extends string>(allowed: readonly T[]) {
  return (value: unknown): value is T => allowed.some((item) => item === value)
}

const isGameStatus = isOneOf(GAME_STATUSES)
const isParticipantRole = isOneOf(PARTICIPANT_ROLES)
const isErrorCode = isOneOf(ERROR_CODES)
const isNumberOrNull = (value: unknown): value is number | null => value === null || isNumber(value)

export function isPlayerView(value: unknown): value is PlayerView {
  return (
    isRecord(value) &&
    isString(value.publicId) &&
    isString(value.nickname) &&
    isBoolean(value.connected) &&
    isNumber(value.score)
  )
}

export function isLeaderboardEntry(value: unknown): value is LeaderboardEntry {
  return (
    isRecord(value) &&
    isNumber(value.rank) &&
    isString(value.publicId) &&
    isString(value.nickname) &&
    isNumber(value.score) &&
    isNumber(value.lastPoints)
  )
}

export function isQuestionView(value: unknown): value is QuestionView {
  return (
    isRecord(value) &&
    isString(value.questionId) &&
    isNumber(value.index) &&
    isNumber(value.total) &&
    isString(value.text) &&
    isArrayOf(value.choices, isString) &&
    isNumber(value.points) &&
    isNumber(value.durationMs) &&
    isNumber(value.endsAt)
  )
}

function isPlayerSelf(value: unknown): value is PlayerSelf {
  return (
    isRecord(value) &&
    isString(value.playerId) &&
    isString(value.publicId) &&
    isString(value.nickname) &&
    isNumber(value.score) &&
    isNumber(value.lastPoints) &&
    isNumberOrNull(value.choiceIndex)
  )
}

export function isGameSnapshot(value: unknown): value is GameSnapshot {
  return (
    isRecord(value) &&
    isString(value.code) &&
    isGameStatus(value.status) &&
    isParticipantRole(value.role) &&
    (value.you === null || isPlayerSelf(value.you)) &&
    isArrayOf(value.players, isPlayerView) &&
    (value.currentQuestion === null || isQuestionView(value.currentQuestion)) &&
    isNumberOrNull(value.correctIndex) &&
    isNumber(value.answeredCount) &&
    isNumber(value.connectedPlayers) &&
    isArrayOf(value.leaderboard, isLeaderboardEntry) &&
    isNumber(value.serverNow)
  )
}

export function isGameCreated(value: unknown): value is GameCreated {
  return isRecord(value) && isString(value.code) && isString(value.hostToken)
}

export function isErrorMessage(value: unknown): value is ErrorMessage {
  return isRecord(value) && isErrorCode(value.code) && isString(value.message)
}

export function isThemeView(value: unknown): value is ThemeView {
  return isRecord(value) && isString(value.id) && isString(value.name) && isNumber(value.questionCount)
}

/** Checks the `type` first, then the fields that this type must have. */
export function isRoomEvent(value: unknown): value is RoomEvent {
  if (!isRecord(value)) {
    return false
  }
  switch (value.type) {
    case 'PLAYERS_UPDATED':
      return isArrayOf(value.players, isPlayerView)
    case 'QUESTION_STARTED':
      return (
        isQuestionView(value.question) &&
        isNumber(value.serverNow) &&
        isArrayOf(value.leaderboard, isLeaderboardEntry)
      )
    case 'ANSWER_PROGRESS':
      return isNumber(value.answeredCount) && isNumber(value.connectedPlayers)
    case 'QUESTION_CLOSED':
      return (
        isString(value.questionId) &&
        isNumber(value.correctIndex) &&
        isArrayOf(value.leaderboard, isLeaderboardEntry)
      )
    case 'GAME_ENDED':
      return isArrayOf(value.leaderboard, isLeaderboardEntry)
    default:
      return false
  }
}
