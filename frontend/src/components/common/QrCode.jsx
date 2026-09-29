import { useEffect, useState } from 'react'
import QRCode from 'qrcode'

// Renders an otpauth:// URL as a scannable QR code (data-URL image).
export default function QrCode({ value, size = 148 }) {
  const [src, setSrc] = useState(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!value) return
    let cancelled = false
    QRCode.toDataURL(value, { width: size, margin: 1, errorCorrectionLevel: 'M' })
      .then((url) => { if (!cancelled) setSrc(url) })
      .catch(() => { if (!cancelled) setError(true) })
    return () => { cancelled = true }
  }, [value, size])

  if (error || !value) {
    return (
      <div className="border rounded d-grid" style={{ width: size, height: size, placeItems: 'center', background: 'var(--app-bg)' }}>
        <span className="text-muted small text-center px-2">QR unavailable —<br />use the secret key</span>
      </div>
    )
  }

  if (!src) {
    return <div className="skeleton" style={{ width: size, height: size, borderRadius: '0.5rem' }} />
  }

  return (
    <img src={src} alt="Two-factor QR code" width={size} height={size}
      className="rounded border bg-white p-1" style={{ display: 'block' }} />
  )
}
