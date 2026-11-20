import { useEffect, useRef, useState } from 'react'
import { FiMoreHorizontal } from 'react-icons/fi'
import Button from './Button'

// A button that opens a short list of secondary actions — the "…" beside a
// primary button.
//
// No Bootstrap JS is loaded, so this opens the stock .dropdown-menu itself:
// closes on an outside click, on Escape (returning focus to the button) and
// after a choice; Arrow keys move between items.
//
// items: [{ label, hint?, onClick, icon?, danger?, disabled? }]
// Falsy entries are skipped, so `cond && { … }` works inline.
//
// `label` is the accessible name. With no `children` the trigger is icon-only
// (FiMoreHorizontal by default) and the label becomes its tooltip.
export default function DropdownMenu({
  items, label = 'More actions', icon = FiMoreHorizontal, children,
  align = 'end', size = 'sm', variant = 'light', disabled = false, write = false, minWidth = 240,
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const triggerRef = useRef(null)
  const shown = (items || []).filter(Boolean)

  const focusItem = (i) => {
    const els = ref.current?.querySelectorAll('[role="menuitem"]:not(:disabled)')
    if (!els?.length) return
    els[(i + els.length) % els.length].focus()
  }

  useEffect(() => {
    if (!open) return undefined
    const onDown = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    const onKey = (e) => {
      if (e.key === 'Escape') { setOpen(false); triggerRef.current?.querySelector('button')?.focus() }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    // Keyboard users land on the first choice, not back on the page.
    requestAnimationFrame(() => focusItem(0))
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const onMenuKey = (e) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
    e.preventDefault()
    const els = [...ref.current.querySelectorAll('[role="menuitem"]:not(:disabled)')]
    const at = els.indexOf(document.activeElement)
    focusItem(at + (e.key === 'ArrowDown' ? 1 : -1))
  }

  if (!shown.length) return null

  return (
    <div className="position-relative d-inline-block" ref={ref}>
      <span ref={triggerRef}>
        <Button write={write} size={size} variant={variant} icon={icon} disabled={disabled}
          aria-label={children ? undefined : label} aria-haspopup="menu" aria-expanded={open}
          title={children ? undefined : label}
          onClick={() => setOpen((o) => !o)}>
          {children}
        </Button>
      </span>
      {open && (
        <div role="menu" aria-label={label} onKeyDown={onMenuKey}
          className={`dropdown-menu show shadow-sm ${align === 'end' ? 'dropdown-menu-end' : ''}`}
          style={{
            // Capped to the screen so a phone never gets a menu hanging off the edge.
            position: 'absolute', top: '100%', zIndex: 1050, minWidth: `min(${minWidth}px, calc(100vw - 32px))`,
            ...(align === 'end' ? { right: 0 } : { left: 0 }),
          }}>
          {shown.map((it) => {
            const Icon = it.icon
            return (
              <button key={it.label} type="button" role="menuitem" disabled={it.disabled}
                className={`dropdown-item text-wrap py-2 d-flex align-items-start gap-2 ${it.danger ? 'text-danger' : ''}`}
                onClick={() => { setOpen(false); it.onClick?.() }}>
                {Icon && <Icon size={14} className="flex-shrink-0 mt-1" />}
                <span>
                  <span className="d-block">{it.label}</span>
                  {it.hint && <small className="text-muted d-block">{it.hint}</small>}
                </span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
