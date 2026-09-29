import { useEffect, useRef, useState } from 'react'
import { FiUploadCloud, FiX, FiLink, FiAlertCircle } from 'react-icons/fi'

// Image field that accepts a FILE or a pasted URL, and always emits a string —
// because the API stores these as plain strings (product images[].url, tenant
// logo) and there is no upload endpoint to POST bytes to.
//
// A picked file is downscaled on a canvas and encoded as a data URI. That keeps
// it small enough to live in a text column; without a real upload endpoint it
// is the only way a file can be persisted at all.
//
// NOTE: data URIs are ~33% larger than the binary. `maxBytes` caps the encoded
// string; if the backend column is narrower than that, saving will fail — ask
// for an upload endpoint rather than raising this limit.

async function downscaleToDataUrl(file, { maxDim, maxBytes, mime }) {
  const bitmap = await createImageBitmap(file)
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')

  // Shrink until the encoded string fits, halving the long edge each round.
  let dim = maxDim
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const scale = Math.min(1, dim / Math.max(bitmap.width, bitmap.height))
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const url = canvas.toDataURL(mime, 0.82)
    if (url.length <= maxBytes) return url
    dim = Math.round(dim / 1.5)
  }
  return null
}

export default function ImageInput({
  value,
  onChange,
  maxDim = 512,
  maxBytes = 96_000,
  // PNG keeps transparency (logos); JPEG is far smaller for photographs.
  mime = 'image/jpeg',
  height = 140,
}) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [showUrl, setShowUrl] = useState(false)
  const inputRef = useRef(null)
  const dropRef = useRef(null)

  // Reset the transient UI when the field is cleared from outside (form reset).
  useEffect(() => { if (!value) setError('') }, [value])

  const accept = async (file) => {
    if (!file) return
    if (!file.type?.startsWith('image/')) { setError('That file is not an image.'); return }
    setBusy(true); setError('')
    try {
      const url = await downscaleToDataUrl(file, { maxDim, maxBytes, mime })
      if (!url) setError('That image is too detailed to store. Try a smaller or simpler one.')
      else onChange(url)
    } catch {
      setError('Could not read that image.')
    } finally {
      setBusy(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  const clear = () => { onChange(''); setError('') }

  return (
    <>
      {value ? (
        <div className="d-flex align-items-center gap-3">
          <img src={value} alt="Preview" className="rounded-3 border bg-white flex-shrink-0"
            style={{ height, width: height, objectFit: 'contain' }} />
          <div className="d-flex flex-column gap-2">
            <button type="button" className="btn btn-sm btn-light" onClick={() => inputRef.current?.click()}>
              Replace
            </button>
            <button type="button" className="btn btn-sm btn-light text-danger" onClick={clear}>
              <FiX size={13} className="me-1" />Remove
            </button>
          </div>
        </div>
      ) : (
        <div
          ref={dropRef}
          className="upload-drop justify-content-center text-center"
          style={{ minHeight: height, flexDirection: 'column' }}
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => { e.preventDefault() }}
          onDrop={(e) => { e.preventDefault(); accept(e.dataTransfer.files?.[0]) }}
        >
          {busy ? (
            <><span className="spinner-border spinner-border-sm mb-2" /><span className="small">Processing…</span></>
          ) : (
            <>
              <FiUploadCloud size={22} className="mb-2" />
              <span className="small fw-semibold">Upload an image</span>
              <span style={{ fontSize: 11 }}>Click or drag &amp; drop</span>
            </>
          )}
        </div>
      )}

      <input ref={inputRef} type="file" accept="image/*" hidden
        onChange={(e) => accept(e.target.files?.[0])} />

      {error && (
        <div className="d-flex align-items-center gap-1 text-danger small mt-2">
          <FiAlertCircle size={13} className="flex-shrink-0" />{error}
        </div>
      )}

      {/* A hosted URL is still valid and avoids embedding the image entirely. */}
      {showUrl ? (
        <input className="form-control mt-2" type="url" placeholder="https://…/image.png"
          value={value?.startsWith('data:') ? '' : (value || '')}
          onChange={(e) => onChange(e.target.value)} />
      ) : (
        <button type="button" className="btn btn-sm btn-link p-0 mt-2 text-decoration-none"
          onClick={() => setShowUrl(true)}>
          <FiLink size={12} className="me-1" />Use an image URL instead
        </button>
      )}
    </>
  )
}
