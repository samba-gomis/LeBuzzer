import { Link, useParams } from 'react-router'
import ChoiceGrid from '../components/ChoiceGrid'
import ConnectionStatus from '../components/ConnectionStatus'
import Countdown from '../components/Countdown'
import ErrorBanner from '../components/ErrorBanner'
import Leaderboard from '../components/Leaderboard'
import Notice from '../components/Notice'
import PlayerList from '../components/PlayerList'
import QuestionCard from '../components/QuestionCard'
import { useCountdown } from '../hooks/useCountdown'
import { useGameSocket, type HostAction } from '../hooks/useGameSocket'
import { errorLabel } from '../services/errorMessages'
import { normalizeGameCode } from '../services/gameCode'
import { loadHostSession } from '../services/session'
import type { ErrorCode, GameSnapshot, QuestionView } from '../types/messages'

/** Errors after which there is nothing left to show on this page. */
const FATAL_ERRORS: readonly ErrorCode[] = ['GAME_NOT_FOUND', 'INVALID_HOST_TOKEN']

export default function HostPage() {
  const code = normalizeGameCode(useParams().code ?? '')

  // The hostToken is only known by the tab that created the game.
  if (loadHostSession(code) === null) {
    return (
      <Notice title="Accès réservé" actionLabel="Retour à l'accueil" actionTo="/">
        <p>
          Cette page est réservée à l'animateur de la partie <strong>{code}</strong>, dans l'onglet qui l'a créée.
        </p>
      </Notice>
    )
  }
  return <HostGame code={code} />
}

function HostGame({ code }: { code: string }) {
  const { connectionState, game, error, dismissError, sendHostAction } = useGameSocket(code, 'host')
  const view = game?.view ?? null
  const question = view?.currentQuestion ?? null
  const remainingMs = useCountdown(
    view?.status === 'QUESTION_OPEN' && question !== null ? question.endsAt : null,
    game?.clockOffsetMs ?? 0,
  )

  if (view === null) {
    if (error !== null && FATAL_ERRORS.includes(error.code)) {
      return (
        <Notice title="Partie indisponible" actionLabel="Créer une nouvelle partie" actionTo="/">
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

  return (
    <div className="screen screen--wide">
      <div className="screen__bar">
        <ConnectionStatus state={connectionState} />
        <span className="chip">Animateur · partie {view.code}</span>
      </div>
      <ErrorBanner error={error} onDismiss={dismissError} />

      {view.status === 'LOBBY' && <HostLobby view={view} onStart={() => sendHostAction('start-question')} />}
      {view.status === 'QUESTION_OPEN' && question !== null && (
        <HostOpenQuestion
          view={view}
          question={question}
          remainingMs={remainingMs}
          onClose={() => sendHostAction('close-question')}
        />
      )}
      {view.status === 'QUESTION_CLOSED' && question !== null && (
        <HostResults view={view} question={question} onAction={sendHostAction} />
      )}
      {view.status === 'FINISHED' && <HostFinal view={view} />}
    </div>
  )
}

function HostLobby({ view, onStart }: { view: GameSnapshot; onStart: () => void }) {
  return (
    <section className="panel panel--wide">
      <p className="muted">Code de la partie</p>
      <p className="game-code">{view.code}</p>
      <p className="muted">
        Les joueurs rejoignent sur <strong>{window.location.host}</strong> avec ce code.
      </p>
      <PlayerList players={view.players} />
      <button className="button button--primary" type="button" disabled={view.connectedPlayers === 0} onClick={onStart}>
        Lancer la première question
      </button>
    </section>
  )
}

interface HostOpenQuestionProps {
  view: GameSnapshot
  question: QuestionView
  remainingMs: number | null
  onClose: () => void
}

function HostOpenQuestion({ view, question, remainingMs, onClose }: HostOpenQuestionProps) {
  return (
    <section className="panel panel--wide">
      <QuestionCard question={question} />
      {remainingMs !== null && <Countdown remainingMs={remainingMs} durationMs={question.durationMs} />}
      <ChoiceGrid choices={question.choices} selectedIndex={null} correctIndex={null} disabled />
      <p className="progress" aria-live="polite">
        <strong>{view.answeredCount}</strong> / {view.connectedPlayers} réponse{view.answeredCount > 1 ? 's' : ''}
      </p>
      <button className="button button--secondary" type="button" onClick={onClose}>
        Clore la question
      </button>
    </section>
  )
}

interface HostResultsProps {
  view: GameSnapshot
  question: QuestionView
  onAction: (action: HostAction) => void
}

function HostResults({ view, question, onAction }: HostResultsProps) {
  const hasNextQuestion = question.index + 1 < question.total
  return (
    <section className="panel panel--wide">
      <QuestionCard question={question} />
      <ChoiceGrid choices={question.choices} selectedIndex={null} correctIndex={view.correctIndex} disabled />
      <h3 className="section-title">Classement</h3>
      <Leaderboard entries={view.leaderboard} limit={5} />
      <div className="actions">
        {hasNextQuestion ? (
          <>
            <button className="button button--primary" type="button" onClick={() => onAction('start-question')}>
              Question suivante
            </button>
            <button className="button button--ghost" type="button" onClick={() => onAction('end')}>
              Terminer la partie
            </button>
          </>
        ) : (
          <button className="button button--primary" type="button" onClick={() => onAction('end')}>
            Voir le classement final
          </button>
        )}
      </div>
    </section>
  )
}

function HostFinal({ view }: { view: GameSnapshot }) {
  return (
    <section className="panel panel--wide">
      <h1 className="panel__title">Classement final</h1>
      <Leaderboard entries={view.leaderboard} />
      <Link className="button button--primary" to="/">
        Créer une nouvelle partie
      </Link>
    </section>
  )
}
