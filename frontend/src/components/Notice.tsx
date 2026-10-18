import type { ReactNode } from 'react'
import { Link } from 'react-router'

interface NoticeProps {
  title: string
  children: ReactNode
  actionLabel: string
  actionTo: string
}

/** A full-panel message with one way out (game not found, page reserved to the host...). */
export default function Notice({ title, children, actionLabel, actionTo }: NoticeProps) {
  return (
    <section className="panel">
      <h1 className="panel__title">{title}</h1>
      <div className="notice__text">{children}</div>
      <Link className="button button--primary" to={actionTo}>
        {actionLabel}
      </Link>
    </section>
  )
}
