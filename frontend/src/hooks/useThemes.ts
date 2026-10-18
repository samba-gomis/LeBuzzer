import { useEffect, useState } from 'react'
import { fetchThemes } from '../services/themesApi'
import type { ThemeView } from '../types/messages'

export type ThemesState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; themes: ThemeView[] }

export function useThemes(): ThemesState {
  const [state, setState] = useState<ThemesState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchThemes(controller.signal)
      .then((themes) => setState({ status: 'ready', themes }))
      .catch(() => {
        // An abort is not an error: it only means the page was left before the answer.
        if (!controller.signal.aborted) {
          setState({ status: 'error' })
        }
      })
    // Cleanup: cancels the request if the page is left, so no state is set on an unmounted component.
    return () => controller.abort()
  }, [])

  return state
}
