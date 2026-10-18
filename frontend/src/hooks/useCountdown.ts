import { useEffect, useState } from 'react'

const TICK_MS = 200

/**
 * Time left before `endsAt`, in ms (null when there is no deadline).
 * The browser only displays it: `endsAt` comes from the server, and `clockOffsetMs` corrects the
 * difference between the phone's clock and the server's. The server alone decides if an answer is late.
 */
export function useCountdown(endsAt: number | null, clockOffsetMs: number): number | null {
  const [remainingMs, setRemainingMs] = useState<number | null>(null)

  useEffect(() => {
    if (endsAt === null) {
      return
    }
    const tick = () => setRemainingMs(Math.max(0, endsAt - (Date.now() + clockOffsetMs)))
    tick()
    const timer = setInterval(tick, TICK_MS)
    return () => clearInterval(timer)
  }, [endsAt, clockOffsetMs])

  return endsAt === null ? null : remainingMs
}
