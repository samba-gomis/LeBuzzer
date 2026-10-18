import { isRecord } from '../types/guards'

// Memory of the tab (sessionStorage): it survives a reload but not a closed tab, and each tab has
// its own. That is what lets a player come back after F5, and lets several players be tested in
// the same browser.

export interface PlayerSession {
  nickname: string
  /** Secret id received in the first snapshot; null before the first join. */
  playerId: string | null
}

export interface HostSession {
  hostToken: string
}

const playerKey = (code: string) => `buzz.player.${code}`
const hostKey = (code: string) => `buzz.host.${code}`

function read(key: string): unknown {
  try {
    const raw = window.sessionStorage.getItem(key)
    if (raw === null) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    return parsed
  } catch {
    return null
  }
}

function write(key: string, value: PlayerSession | HostSession): void {
  try {
    window.sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Storage unavailable (e.g. blocked): the game still works, only the rejoin after a reload is lost.
  }
}

export function loadPlayerSession(code: string): PlayerSession | null {
  const value = read(playerKey(code))
  if (
    isRecord(value) &&
    typeof value.nickname === 'string' &&
    (value.playerId === null || typeof value.playerId === 'string')
  ) {
    return { nickname: value.nickname, playerId: value.playerId }
  }
  return null
}

export function savePlayerSession(code: string, session: PlayerSession): void {
  write(playerKey(code), session)
}

export function loadHostSession(code: string): HostSession | null {
  const value = read(hostKey(code))
  if (isRecord(value) && typeof value.hostToken === 'string') {
    return { hostToken: value.hostToken }
  }
  return null
}

export function saveHostSession(code: string, session: HostSession): void {
  write(hostKey(code), session)
}
