import { isArrayOf, isThemeView } from '../types/guards'
import type { ThemeView } from '../types/messages'

/** Plain HTTP: the theme list is static data, read once on the home page. */
export async function fetchThemes(signal: AbortSignal): Promise<ThemeView[]> {
  const response = await fetch('/api/themes', { signal })
  if (!response.ok) {
    throw new Error(`GET /api/themes failed with status ${response.status}`)
  }
  const data: unknown = await response.json()
  if (!isArrayOf(data, isThemeView)) {
    throw new Error('GET /api/themes returned an unexpected payload')
  }
  return data
}
