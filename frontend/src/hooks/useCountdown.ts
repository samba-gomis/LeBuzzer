import { useEffect, useState } from 'react'

const TICK_MS = 200

/**
 * Time left before `endsAt`, in ms (null when there is no deadline).
 * The browser only displays it: `endsAt` comes from the server, and `clockOffsetMs` corrects the
 * difference between the phone's clock and the server's. The server alone decides if an answer is late.
 */
export function useCountdown(endsAt: number | null, clockOffsetMs: number): number | null {
  // The value remembers the deadline it was computed for: when a new question starts, the time left
  // of the previous one is never shown, not even for one frame (it would display "Temps écoulé").
  const [tick, setTick] = useState<{ endsAt: number; remainingMs: number } | null>(null)

  useEffect(() => {
    if (endsAt === null) {
      return
    }
    const update = () => setTick({ endsAt, remainingMs: Math.max(0, endsAt - (Date.now() + clockOffsetMs)) })
    update()
    const timer = setInterval(update, TICK_MS)
    return () => clearInterval(timer)
  }, [endsAt, clockOffsetMs])

  return tick !== null && tick.endsAt === endsAt ? tick.remainingMs : null
}
