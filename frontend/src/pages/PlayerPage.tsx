import { Link, Navigate, useParams } from 'react-router'
import ChoiceGrid from '../components/ChoiceGrid'
import ConnectionStatus from '../components/ConnectionStatus'
import Countdown from '../components/Countdown'
import ErrorBanner from '../components/ErrorBanner'
import Leaderboard from '../components/Leaderboard'
import Notice from '../components/Notice'
import QuestionCard from '../components/QuestionCard'
import { useCountdown } from '../hooks/useCountdown'
import { useGameSocket } from '../hooks/useGameSocket'
import { errorLabel } from '../services/errorMessages'
import { formatPoints, formatRank } from '../services/format'
import type { ConnectionState } from '../services/gameSocket'
import { normalizeGameCode } from '../services/gameCode'
import { loadPlayerSession } from '../services/session'
import type { ErrorCode, GameSnapshot, PlayerSelf, QuestionView } from '../types/messages'

/** Errors after which this player cannot be in the game: back to the home page. */
const FATAL_ERRORS: readonly ErrorCode[] = ['GAME_NOT_FOUND', 'NICKNAME_TAKEN', 'INVALID_NICKNAME', 'GAME_FINISHED']

export default function PlayerPage() {
  const code = normalizeGameCode(useParams().code ?? '')

  // No nickname for this game in this tab (e.g. a shared link): choose one on the home page first.
  if (loadPlayerSession(code) === null) {
    return <Navigate to={`/?code=${code}`} replace />
  }
  return <PlayerGame code={code} />
}

function PlayerGame({ code }: { code: string }) {
  const { connectionState, game, error, dismissError, answer } = useGameSocket(code, 'player')
  const view = game?.view ?? null
  const question = view?.currentQuestion ?? null
  const remainingMs = useCountdown(
    view?.status === 'QUESTION_OPEN' && question !== null ? question.endsAt : null,
    game?.clockOffsetMs ?? 0,
  )

  if (view === null || view.you === null) {
    if (error !== null && FATAL_ERRORS.includes(error.code)) {
      return (
        <Notice title="Impossible de rejoindre" actionLabel="Retour à l'accueil" actionTo={`/?code=${code}`}>
          <p>{errorLabel(error.code)}</p>
        </Notice>
      )
    }
    return (
      <section className="panel">
        <ConnectionStatus state={connectionState} />
        <p className="muted">Connexion à la partie {code}…</p>
      </section>
    )
  }

  const you = view.you
  return (
    <div className="screen">
      <div className="screen__bar">
        <ConnectionStatus state={connectionState} />
        <span className="chip">
          {you.nickname} · {formatPoints(you.score)} pts
        </span>
      </div>
      <ErrorBanner error={error} onDismiss={dismissError} />

      {view.status === 'LOBBY' && (
        <section className="panel">
          <h1 className="panel__title">Bienvenue {you.nickname} !</h1>
          <p>
            Vous êtes dans la partie <strong>{view.code}</strong>.
          </p>
          <p className="muted waiting">En attente de la première question…</p>
        </section>
      )}
      {view.status === 'QUESTION_OPEN' && question !== null && (
        <PlayerQuestion
          question={question}
          you={you}
          remainingMs={remainingMs}
          connectionState={connectionState}
          onAnswer={(choiceIndex) => answer(question.questionId, choiceIndex)}
        />
      )}
      {view.status === 'QUESTION_CLOSED' && question !== null && (
        <PlayerResult view={view} question={question} you={you} />
      )}
      {view.status === 'FINISHED' && <PlayerFinal view={view} you={you} />}
    </div>
  )
}

interface PlayerQuestionProps {
  question: QuestionView
  you: PlayerSelf
  remainingMs: number | null
  connectionState: ConnectionState
  onAnswer: (choiceIndex: number) => void
}

function PlayerQuestion({ question, you, remainingMs, connectionState, onAnswer }: PlayerQuestionProps) {
  const answered = you.choiceIndex !== null
  const timeUp = remainingMs !== null && remainingMs <= 0
  // Locked once answered (only the first answer counts), when the time is up, or while offline.
  const locked = answered || timeUp || connectionState !== 'connected'

  return (
    <section className="panel">
      <QuestionCard question={question} />
      {remainingMs !== null && <Countdown remainingMs={remainingMs} durationMs={question.durationMs} />}
      <ChoiceGrid
        choices={question.choices}
        selectedIndex={you.choiceIndex}
        correctIndex={null}
        disabled={locked}
        onSelect={onAnswer}
      />
      <p className="muted" aria-live="polite">
        {answered && 'Réponse envoyée ! Résultat à la fin du temps.'}
        {!answered && timeUp && 'Temps écoulé.'}
      </p>
    </section>
  )
}

function PlayerResult({ view, question, you }: { view: GameSnapshot; question: QuestionView; you: PlayerSelf }) {
  const answered = you.choiceIndex !== null
  const correct = answered && you.choiceIndex === view.correctIndex
  const entry = view.leaderboard.find((line) => line.publicId === you.publicId)

  return (
    <section className="panel">
      <div className={correct ? 'result result--correct' : 'result result--wrong'} aria-live="polite">
        <p className="result__title">{correct ? 'Bonne réponse !' : answered ? 'Mauvaise réponse' : 'Pas de réponse'}</p>
        <p className="result__points">{correct ? `+${formatPoints(you.lastPoints)} points` : '0 point'}</p>
        {entry !== undefined && (
          <p className="result__rank">
            Vous êtes {formatRank(entry.rank)} sur {view.leaderboard.length} · {formatPoints(entry.score)} pts
          </p>
        )}
      </div>
      <QuestionCard question={question} />
      <ChoiceGrid choices={question.choices} selectedIndex={you.choiceIndex} correctIndex={view.correctIndex} disabled />
      <p className="muted waiting">En attente de la question suivante…</p>
    </section>
  )
}

function PlayerFinal({ view, you }: { view: GameSnapshot; you: PlayerSelf }) {
  const entry = view.leaderboard.find((line) => line.publicId === you.publicId)
  return (
    <section className="panel">
      <h1 className="panel__title">Partie terminée !</h1>
      {entry !== undefined && (
        <p className="result__rank">
          Vous finissez {formatRank(entry.rank)} sur {view.leaderboard.length} avec {formatPoints(entry.score)} points.
        </p>
      )}
      <Leaderboard entries={view.leaderboard} highlightPublicId={you.publicId} />
      <Link className="button button--primary" to="/">
        Rejouer
      </Link>
    </section>
  )
}
