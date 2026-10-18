const URGENT_BELOW_MS = 5_000

interface CountdownProps {
  /** From useCountdown, i.e. computed with the server deadline. */
  remainingMs: number
  durationMs: number
}

export default function Countdown({ remainingMs, durationMs }: CountdownProps) {
  const seconds = Math.ceil(remainingMs / 1000)
  const ratio = Math.min(1, Math.max(0, remainingMs / durationMs))
  const urgent = remainingMs > 0 && remainingMs <= URGENT_BELOW_MS

  return (
    <div className={urgent ? 'countdown countdown--urgent' : 'countdown'} role="timer">
      <div className="countdown__bar" aria-hidden="true">
        <div className="countdown__fill" style={{ transform: `scaleX(${ratio})` }} />
      </div>
      <span className="countdown__seconds">{remainingMs > 0 ? `${seconds} s` : 'Temps écoulé'}</span>
    </div>
  )
}
