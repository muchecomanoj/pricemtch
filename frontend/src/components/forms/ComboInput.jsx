import { useEffect, useMemo, useRef, useState } from 'react'
import { FiChevronDown, FiCheck, FiX } from 'react-icons/fi'

// Text input with a styled suggestion list — a combobox, not a select.
//
// Replaces <datalist>, which browsers render inconsistently and which ignores
// the app's styling entirely.
//
// Two modes:
//   default   free text. The typed value IS the value — needed for brand and
//             category on the product form, where a new value must be typeable.
//   strict    typing only searches. The value changes only when an option is
//             picked, so a filter can never hold something nothing matches.
export default function ComboInput({
  id, value = '', onChange, options = [], placeholder, maxLength, disabled,
  strict = false, clearable = false, emptyLabel,
}) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [query, setQuery] = useState('')   // strict mode only
  const [typed, setTyped] = useState(false)
  const inputRef = useRef(null)
  const wrapRef = useRef(null)

  // What the input shows: in strict mode the search text while open, otherwise
  // the committed value.
  const text = strict ? (open ? query : value) : value

  // Filter only once the user has actually typed. Filtering by the existing
  // value would show a product's current brand as the only option, leaving no
  // way to browse to a different one while editing.
  const filtered = useMemo(() => {
    const q = typed ? (strict ? query : value || '').trim().toLowerCase() : ''
    const list = q ? options.filter((o) => o.toLowerCase().includes(q)) : options
    return list.slice(0, 50)
  }, [options, value, query, strict, typed])

  const close = () => { setOpen(false); setActive(-1); setQuery(''); setTyped(false) }

  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) close()
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  const pick = (opt) => { onChange(opt); close() }

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault(); setOpen(true)
      setActive((i) => Math.min(i + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault(); setActive((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && open && active >= 0) {
      e.preventDefault(); pick(filtered[active])
    } else if (e.key === 'Escape') {
      close()
    }
  }

  const showClear = clearable && !!value && !disabled

  return (
    <div className="combo" ref={wrapRef}>
      <input
        id={id}
        className={`form-control combo__input ${showClear ? 'combo__input--clearable' : ''}`}
        value={text}
        maxLength={maxLength}
        placeholder={placeholder}
        disabled={disabled}
        autoComplete="off"
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        ref={inputRef}
        onChange={(e) => {
          if (strict) setQuery(e.target.value)
          else onChange(e.target.value)
          setOpen(true); setActive(-1); setTyped(true)
        }}
        onFocus={(e) => {
          setOpen(true); setTyped(false)
          if (strict) setQuery('')
          // Select the existing text so typing replaces the current value
          // rather than appending to it — the usual intent when editing.
          else e.target.select()
        }}
        onKeyDown={onKeyDown}
      />

      {showClear && (
        <button type="button" className="combo__toggle combo__toggle--clear" tabIndex={-1}
          aria-label="Clear" onClick={() => { onChange(''); close() }}>
          <FiX size={14} />
        </button>
      )}
      {!showClear && options.length > 0 && (
        <button type="button" className="combo__toggle" tabIndex={-1} disabled={disabled}
          aria-label="Show suggestions"
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => {
            // Always open on the full list, never pre-filtered by the current value.
            if (open) close()
            else { setOpen(true); setTyped(false); setQuery(''); inputRef.current?.focus() }
          }}>
          <FiChevronDown size={15} />
        </button>
      )}

      {open && (filtered.length > 0 || emptyLabel) && (
        <ul className="combo__menu" role="listbox">
          {/* "All categories" style reset, so clearing is a menu choice too. */}
          {emptyLabel && (
            <li>
              <button type="button" role="option" aria-selected={!value}
                className={`combo__option ${!value ? 'is-active' : ''}`}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => { onChange(''); close() }}>
                <span className="text-muted">{emptyLabel}</span>
                {!value && <FiCheck size={13} className="flex-shrink-0" />}
              </button>
            </li>
          )}
          {filtered.map((opt, i) => (
            <li key={opt}>
              <button
                type="button"
                role="option"
                aria-selected={opt === value}
                className={`combo__option ${i === active ? 'is-active' : ''}`}
                // Keep focus on the input so blur doesn't close before the click.
                onMouseDown={(e) => e.preventDefault()}
                onMouseEnter={() => setActive(i)}
                onClick={() => pick(opt)}
              >
                <span className="text-truncate">{opt}</span>
                {opt === value && <FiCheck size={13} className="flex-shrink-0" />}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
