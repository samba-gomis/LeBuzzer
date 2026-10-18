const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F']

interface ChoiceGridProps {
  choices: string[]
  /** The player's answer, if any. */
  selectedIndex: number | null
  /** Known only once the question is closed. */
  correctIndex: number | null
  disabled: boolean
  /** Absent on the host screen: the choices are only displayed. */
  onSelect?: (index: number) => void
}

function choiceClassName(index: number, selectedIndex: number | null, correctIndex: number | null): string {
  const classes = ['choice', `choice--${index % 4}`]
  if (index === selectedIndex) {
    classes.push('choice--selected')
  }
  if (correctIndex !== null) {
    classes.push(index === correctIndex ? 'choice--correct' : 'choice--faded')
  }
  return classes.join(' ')
}

export default function ChoiceGrid({ choices, selectedIndex, correctIndex, disabled, onSelect }: ChoiceGridProps) {
  return (
    <div className="choice-grid">
      {choices.map((choice, index) => (
        <button
          key={`${index}-${choice}`}
          type="button"
          className={choiceClassName(index, selectedIndex, correctIndex)}
          disabled={disabled || onSelect === undefined}
          onClick={onSelect === undefined ? undefined : () => onSelect(index)}
          aria-pressed={index === selectedIndex}
        >
          <span className="choice__letter">{LETTERS[index]}</span>
          <span className="choice__text">{choice}</span>
          {correctIndex === index && <span className="choice__mark" aria-label="Bonne réponse">✓</span>}
        </button>
      ))}
    </div>
  )
}
