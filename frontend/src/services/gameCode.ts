const GAME_CODE_LENGTH = 5

/** What the player types, cleaned: capital letters only, 5 at most ("abc d1e" -> "ABCDE"). */
export function cleanGameCodeInput(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z]/g, '').slice(0, GAME_CODE_LENGTH)
}

/** A code coming from the URL may be in lower case: the server stores them in upper case. */
export function normalizeGameCode(raw: string): string {
  return raw.trim().toUpperCase()
}

export function isValidGameCode(code: string): boolean {
  return new RegExp(`^[A-Z]{${GAME_CODE_LENGTH}}$`).test(code)
}
