import { useState, type SubmitEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useCreateGame } from '../hooks/useCreateGame'
import { useThemes } from '../hooks/useThemes'
import { errorLabel } from '../services/errorMessages'
import { cleanGameCodeInput, isValidGameCode } from '../services/gameCode'
import { savePlayerSession } from '../services/session'

const MAX_NICKNAME_LENGTH = 20

export default function HomePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  // ?code=ABCDE pre-fills the code (e.g. when coming back after a refused join).
  const [code, setCode] = useState(() => cleanGameCodeInput(searchParams.get('code') ?? ''))
  const [nickname, setNickname] = useState('')
  const [joinError, setJoinError] = useState<string | null>(null)

  const themes = useThemes()
  const [chosenThemeId, setChosenThemeId] = useState('')
  const { createGame, creating, connectionState, error: createError } = useCreateGame()
  const firstThemeId = themes.status === 'ready' && themes.themes.length > 0 ? themes.themes[0].id : ''
  const themeId = chosenThemeId === '' ? firstThemeId : chosenThemeId

  const handleJoin = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    const cleanedNickname = nickname.trim()
    if (!isValidGameCode(code)) {
      setJoinError('Le code de la partie contient 5 lettres.')
      return
    }
    if (cleanedNickname.length === 0 || cleanedNickname.length > MAX_NICKNAME_LENGTH) {
      setJoinError('Le pseudo doit contenir entre 1 et 20 caractères.')
      return
    }
    // A new join from the home page = a new player for this tab (no playerId yet).
    savePlayerSession(code, { nickname: cleanedNickname, playerId: null })
    void navigate(`/play/${code}`)
  }

  return (
    <div className="home">
      <section className="panel">
        <h1 className="panel__title">Rejoindre une partie</h1>
        <form className="form" onSubmit={handleJoin} noValidate>
          <label className="field">
            <span className="field__label">Code de la partie</span>
            <input
              className="field__input field__input--code"
              value={code}
              onChange={(event) => setCode(cleanGameCodeInput(event.target.value))}
              placeholder="ABCDE"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
            />
          </label>
          <label className="field">
            <span className="field__label">Pseudo</span>
            <input
              className="field__input"
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              maxLength={MAX_NICKNAME_LENGTH}
              placeholder="Votre pseudo"
              autoComplete="nickname"
            />
          </label>
          {joinError !== null && (
            <p className="form__error" role="alert">
              {joinError}
            </p>
          )}
          <button className="button button--primary" type="submit">
            Rejoindre
          </button>
        </form>
      </section>

      <section className="panel">
        <h2 className="panel__title">Animer une partie</h2>
        {themes.status === 'loading' && <p className="muted">Chargement des thèmes…</p>}
        {themes.status === 'error' && (
          <p className="form__error" role="alert">
            Impossible de charger les thèmes. Le serveur est-il démarré ?
          </p>
        )}
        {themes.status === 'ready' && (
          <div className="form">
            <label className="field">
              <span className="field__label">Thème</span>
              <select
                className="field__input"
                value={themeId}
                onChange={(event) => setChosenThemeId(event.target.value)}
              >
                {themes.themes.map((theme) => (
                  <option key={theme.id} value={theme.id}>
                    {theme.name} — {theme.questionCount} questions
                  </option>
                ))}
              </select>
            </label>
            {createError !== null && (
              <p className="form__error" role="alert">
                {errorLabel(createError.code)}
              </p>
            )}
            {creating && connectionState === 'reconnecting' && (
              <p className="muted">Serveur injoignable, nouvelle tentative…</p>
            )}
            <button
              className="button button--secondary"
              type="button"
              disabled={creating || themeId === ''}
              onClick={() => createGame(themeId)}
            >
              {creating ? 'Création…' : 'Créer la partie'}
            </button>
          </div>
        )}
      </section>
    </div>
  )
}
