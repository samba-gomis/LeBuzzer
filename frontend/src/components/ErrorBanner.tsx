import { errorLabel } from '../services/errorMessages'
import type { ErrorMessage } from '../types/messages'

interface ErrorBannerProps {
  error: ErrorMessage | null
  onDismiss: () => void
}

export default function ErrorBanner({ error, onDismiss }: ErrorBannerProps) {
  if (error === null) {
    return null
  }
  return (
    <div className="error-banner" role="alert">
      <span>{errorLabel(error.code)}</span>
      <button type="button" className="error-banner__close" onClick={onDismiss} aria-label="Fermer le message">
        ×
      </button>
    </div>
  )
}
