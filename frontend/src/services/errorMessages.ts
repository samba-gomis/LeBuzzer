import type { ErrorCode } from '../types/messages'

// The server sends a code, the interface decides how to say it (in French).
// A Record over the union forces a label for every code: a new code without label does not compile.
const ERROR_LABELS: Record<ErrorCode, string> = {
  GAME_NOT_FOUND: 'Aucune partie ne correspond à ce code.',
  THEME_NOT_FOUND: "Ce thème n'existe pas.",
  INVALID_NICKNAME: 'Le pseudo doit contenir entre 1 et 20 caractères.',
  NICKNAME_TAKEN: 'Ce pseudo est déjà pris dans cette partie.',
  INVALID_HOST_TOKEN: "Seul l'animateur de la partie peut faire cette action.",
  NOT_IN_GAME: "Vous n'avez pas rejoint cette partie.",
  GAME_FINISHED: 'Cette partie est terminée.',
  QUESTION_ALREADY_OPEN: 'Une question est déjà en cours.',
  NO_MORE_QUESTIONS: 'Toutes les questions ont été jouées.',
  QUESTION_NOT_OPEN: "Aucune question n'est en cours.",
  QUESTION_CLOSED: 'Trop tard, la question est terminée.',
  ALREADY_ANSWERED: 'Vous avez déjà répondu à cette question.',
  INVALID_CHOICE: "Cette réponse n'existe pas.",
  INVALID_REQUEST: "Le message envoyé n'a pas pu être lu.",
}

export function errorLabel(code: ErrorCode): string {
  return ERROR_LABELS[code]
}
