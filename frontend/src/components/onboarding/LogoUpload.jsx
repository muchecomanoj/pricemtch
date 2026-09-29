import { useState, useRef } from 'react'
import { FiUploadCloud, FiX } from 'react-icons/fi'

// Company logo picker with drag & drop + preview. Emits the File to onChange.
export default function LogoUpload({ value, onChange, size = 92 }) {
  const [preview, setPreview] = useState(value || null)
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef(null)

  const accept = (file) => {
    if (!file || !file.type?.startsWith('image/')) return
    setPreview(URL.createObjectURL(file))
    onChange?.(file)
  }

  const clear = (e) => {
    e.stopPropagation()
    setPreview(null); onChange?.(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div
      className="logo-drop position-relative"
      style={{ minHeight: size + 24, borderColor: dragging ? 'var(--primary)' : undefined }}
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => { e.preventDefault(); setDragging(false); accept(e.dataTransfer.files?.[0]) }}
    >
      <input ref={inputRef} type="file" accept="image/*" hidden
        onChange={(e) => accept(e.target.files?.[0])} />

      {preview ? (
        <div className="position-relative">
          <img src={preview} alt="Logo preview" width={size} height={size}
            className="rounded-3 border bg-white" style={{ objectFit: 'contain' }} />
          <button type="button" className="btn btn-sm btn-light position-absolute rounded-circle p-1"
            style={{ top: -8, right: -8, lineHeight: 1 }} onClick={clear} title="Remove">
            <FiX size={13} />
          </button>
        </div>
      ) : (
        <div className="text-muted">
          <FiUploadCloud size={22} className="mb-2" />
          <div className="small fw-semibold">Upload logo</div>
          <div style={{ fontSize: 11 }}>PNG or SVG · drag &amp; drop</div>
        </div>
      )}
    </div>
  )
}
