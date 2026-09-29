import { useRef } from 'react'

// 6-box OTP input with auto-advance, backspace, and paste support.
export default function OtpInput({ length = 6, value = '', onChange, onComplete, disabled, className = '' }) {
  const refs = useRef([])
  const digits = value.padEnd(length, ' ').slice(0, length).split('')

  const setChar = (i, ch) => {
    const arr = value.padEnd(length, ' ').split('')
    arr[i] = ch || ' '
    const next = arr.join('').replace(/\s+$/g, '')
    onChange(next.trimEnd())
    return next.replace(/\s/g, '')
  }

  const handleChange = (i, e) => {
    const ch = e.target.value.replace(/\D/g, '').slice(-1)
    if (!ch) return
    const clean = setChar(i, ch)
    if (i < length - 1) refs.current[i + 1]?.focus()
    if (clean.length === length) onComplete?.(clean)
  }

  const handleKey = (i, e) => {
    if (e.key === 'Backspace') {
      if (digits[i].trim()) { setChar(i, '') }
      else if (i > 0) { refs.current[i - 1]?.focus(); setChar(i - 1, '') }
    }
    if (e.key === 'ArrowLeft' && i > 0) refs.current[i - 1]?.focus()
    if (e.key === 'ArrowRight' && i < length - 1) refs.current[i + 1]?.focus()
  }

  const handlePaste = (e) => {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!pasted) return
    e.preventDefault()
    onChange(pasted)
    if (pasted.length === length) onComplete?.(pasted)
    else refs.current[pasted.length]?.focus()
  }

  return (
    <div className={`d-flex justify-content-center gap-2 ${className}`} onPaste={handlePaste}>
      {Array.from({ length }).map((_, i) => (
        <input
          key={i}
          ref={(el) => (refs.current[i] = el)}
          className="otp-box"
          inputMode="numeric"
          maxLength={1}
          disabled={disabled}
          value={digits[i].trim()}
          onChange={(e) => handleChange(i, e)}
          onKeyDown={(e) => handleKey(i, e)}
          onFocus={(e) => e.target.select()}
        />
      ))}
    </div>
  )
}
