import { formatPoints } from '../services/format'
import type { QuestionView } from '../types/messages'

export default function QuestionCard({ question }: { question: QuestionView }) {
  return (
    <section className="question-card">
      <p className="question-card__meta">
        <span>
          Question {question.index + 1} / {question.total}
        </span>
        <span className="question-card__points">{formatPoints(question.points)} pts</span>
      </p>
      <h2 className="question-card__text">{question.text}</h2>
    </section>
  )
}
